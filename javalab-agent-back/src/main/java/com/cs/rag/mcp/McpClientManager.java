package com.cs.rag.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * MCP 客户端管理器
 * 负责加载配置、管理MCP服务器连接、转发工具调用请求
 *
 * <p>支持两种传输模式:</p>
 * <ul>
 *   <li>stdio: 通过子进程的stdin/stdout通信 (JSON-RPC 2.0)</li>
 *   <li>http: 通过HTTP POST发送JSON-RPC请求</li>
 * </ul>
 *
 * @author caoshuai
 */
@Slf4j
@Component
public class McpClientManager {

    private final ObjectMapper objectMapper;
    private final McpConfigSupport mcpConfigSupport;
    private final McpProtocolSupport mcpProtocolSupport;

    /** MCP配置文件路径 (classpath下) */
    private static final String CONFIG_FILE = "mcp-tools.json";

    /** 外部配置文件路径 (项目根目录下, 优先级高于classpath) */
    private static final String EXTERNAL_CONFIG_FILE = "mcp-tools.json";

    /** JSON-RPC 请求ID计数器 */
    private final AtomicInteger requestIdCounter = new AtomicInteger(1);

    /** 当前加载的MCP配置 */
    private volatile McpToolsConfig currentConfig;

    /** 已启动的stdio子进程: key=服务器名称 */
    private final Map<String, Process> stdioProcesses = new ConcurrentHashMap<>();

    /** 子进程的输入流写入器: key=服务器名称 */
    private final Map<String, BufferedWriter> stdioWriters = new ConcurrentHashMap<>();

    /** 子进程的输出流读取器: key=服务器名称 */
    private final Map<String, BufferedReader> stdioReaders = new ConcurrentHashMap<>();

    /** 从各MCP服务器获取的工具列表缓存: key=服务器名称 */
    private final Map<String, List<McpToolInfo>> toolsCache = new ConcurrentHashMap<>();

    /** SSE握手后获取到的JSON-RPC POST端点URL: key=服务器名称 */
    private final Map<String, String> sseEndpoints = new ConcurrentHashMap<>();

    /** HTTP客户端 (用于http和sse模式) */
    private final HttpClient httpClient;

    public McpClientManager(ObjectMapper objectMapper,
                            McpConfigSupport mcpConfigSupport,
                            McpProtocolSupport mcpProtocolSupport) {
        this.objectMapper = objectMapper;
        this.mcpConfigSupport = mcpConfigSupport;
        this.mcpProtocolSupport = mcpProtocolSupport;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // ==================== 生命周期管理 ====================

    /**
     * 应用启动时加载MCP配置并初始化所有已启用的服务器连接
     */
    @PostConstruct
    public void init() {
        try {
            loadConfig();
            if (currentConfig != null && currentConfig.getMcpServers() != null) {
                // 逐个初始化已启用的MCP服务器
                currentConfig.getMcpServers().forEach((name, config) -> {
                    if (config.isEnabled()) {
                        try {
                            initServer(name, config);
                        } catch (Exception e) {
                            log.error("初始化MCP服务器失败: name={}, error={}", name, e.getMessage());
                        }
                    }
                });
                log.info("MCP客户端管理器初始化完成, 已加载{}个服务器配置", currentConfig.getMcpServers().size());
            } else {
                log.info("MCP客户端管理器: 无MCP服务器配置或配置为空");
            }
        } catch (Exception e) {
            log.warn("MCP客户端管理器初始化异常: {}", e.getMessage());
        }
    }

    /**
     * 应用关闭时销毁所有子进程
     */
    @PreDestroy
    public void destroy() {
        log.info("MCP客户端管理器正在关闭...");
        stdioProcesses.forEach((name, process) -> {
            try {
                // 先关闭写入器
                BufferedWriter writer = stdioWriters.remove(name);
                if (writer != null) writer.close();
                // 再关闭读取器
                BufferedReader reader = stdioReaders.remove(name);
                if (reader != null) reader.close();
                // 最后销毁进程
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                log.info("已关闭MCP服务器进程: {}", name);
            } catch (Exception e) {
                log.warn("关闭MCP服务器进程异常: name={}, error={}", name, e.getMessage());
            }
        });
        stdioProcesses.clear();
    }

    // ==================== 配置管理 ====================

    /**
     * 加载MCP配置文件
     * 优先从项目根目录加载外部配置，找不到则从classpath加载
     */
    public void loadConfig() {
        if (useSupportDelegates()) {
            currentConfig = mcpConfigSupport.loadConfig(Paths.get(EXTERNAL_CONFIG_FILE), CONFIG_FILE);
            return;
        }
        try {
            String jsonContent = null;

            // 1. 优先从外部文件加载 (支持运行时修改)
            Path externalPath = Paths.get(EXTERNAL_CONFIG_FILE);
            if (Files.exists(externalPath)) {
                jsonContent = Files.readString(externalPath, StandardCharsets.UTF_8);
                log.info("从外部文件加载MCP配置: {}", externalPath.toAbsolutePath());
            }

            // 2. 回退到classpath资源
            if (jsonContent == null) {
                ClassPathResource resource = new ClassPathResource(CONFIG_FILE);
                if (resource.exists()) {
                    try (InputStream is = resource.getInputStream()) {
                        jsonContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        log.info("从classpath加载MCP配置: {}", CONFIG_FILE);
                    }
                }
            }

            if (jsonContent != null && !jsonContent.isBlank()) {
                currentConfig = objectMapper.readValue(jsonContent, McpToolsConfig.class);
                normalizeConfig(currentConfig);
            } else {
                log.info("未找到MCP配置文件，使用空配置");
                currentConfig = new McpToolsConfig();
                currentConfig.setMcpServers(new LinkedHashMap<>());
            }
        } catch (Exception e) {
            log.error("加载MCP配置文件失败: {}", e.getMessage(), e);
            currentConfig = new McpToolsConfig();
            currentConfig.setMcpServers(new LinkedHashMap<>());
        }
    }

    /**
     * 归一化MCP配置，兼容仅填写url但未显式声明type的场景
     */
    private void normalizeConfig(McpToolsConfig config) {
        if (useSupportDelegates()) {
            mcpConfigSupport.normalizeConfig(config);
            return;
        }
        if (config == null) {
            return;
        }
        if (config.getMcpServers() == null) {
            config.setMcpServers(new LinkedHashMap<>());
            return;
        }
        config.getMcpServers().forEach((name, serverConfig) -> {
            if (serverConfig == null) {
                return;
            }
            if ((serverConfig.getType() == null || serverConfig.getType().isBlank())
                    && serverConfig.getUrl() != null && !serverConfig.getUrl().isBlank()) {
                serverConfig.setType("http");
                log.info("MCP服务器[{}]未显式配置type，因存在url自动识别为http模式", name);
            }
            // 自动将包含 /sse 路径的URL识别为SSE传输类型
            if (serverConfig.getUrl() != null
                    && serverConfig.getUrl().toLowerCase(Locale.ROOT).contains("/sse")) {
                serverConfig.setType("sse");
                log.info("MCP服务器[{}]检测到SSE地址，自动切换为SSE传输模式: {}", name, serverConfig.getUrl());
            }
        });
    }

    /**
     * 保存当前配置到外部文件
     */
    public void saveConfig() {
        if (useSupportDelegates()) {
            mcpConfigSupport.saveConfig(Paths.get(EXTERNAL_CONFIG_FILE), currentConfig);
            return;
        }
        try {
            Path externalPath = Paths.get(EXTERNAL_CONFIG_FILE);
            String json = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(currentConfig);
            Files.writeString(externalPath, json, StandardCharsets.UTF_8);
            log.info("MCP配置已保存到: {}", externalPath.toAbsolutePath());
        } catch (Exception e) {
            log.error("保存MCP配置失败: {}", e.getMessage(), e);
            throw new RuntimeException("保存MCP配置失败: " + e.getMessage());
        }
    }

    /**
     * 获取当前配置 (只读副本)
     */
    public McpToolsConfig getConfig() {
        return currentConfig;
    }

    // ==================== 服务器管理 ====================

    /**
     * 初始化单个MCP服务器连接
     */
    private void initServer(String name, McpServerConfig config) {
        // 这里只做协议分发，具体实现放到各自方法。
        if ("stdio".equalsIgnoreCase(config.getType())) {
            initStdioServer(name, config);
        } else if ("sse".equalsIgnoreCase(config.getType())) {
            // SSE模式：先握手获取JSON-RPC POST端点，再获取工具列表
            initSseServer(name, config);
        } else if ("http".equalsIgnoreCase(config.getType())) {
            // HTTP模式不需要预初始化，按需请求即可
            log.info("MCP服务器[{}]使用HTTP模式, url={}", name, config.getUrl());
            // 立即尝试获取工具列表
            refreshToolsForServer(name);
        }
    }

    /**
     * 初始化stdio模式的MCP服务器（启动子进程）
     */
    private void initStdioServer(String name, McpServerConfig config) {
        try {
            List<String> commandList = new ArrayList<>();
            commandList.add(config.getCommand());
            if (config.getArgs() != null) {
                commandList.addAll(config.getArgs());
            }

            ProcessBuilder pb = new ProcessBuilder(commandList);
            pb.redirectErrorStream(false); // 分开处理stderr

            // 设置环境变量
            if (config.getEnv() != null) {
                pb.environment().putAll(config.getEnv());
            }

            Process process = pb.start();
            stdioProcesses.put(name, process);

            // 设置输入输出流
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            stdioWriters.put(name, writer);
            stdioReaders.put(name, reader);

            // 异步读取stderr（防止阻塞）
            Thread stderrThread = new Thread(() -> {
                try (BufferedReader errReader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = errReader.readLine()) != null) {
                        log.debug("MCP[{}] stderr: {}", name, line);
                    }
                } catch (Exception ignored) {
                }
            }, "mcp-stderr-" + name);
            stderrThread.setDaemon(true);
            stderrThread.start();

            log.info("MCP服务器[{}]子进程已启动: command={}", name, commandList);

            // 发送 initialize 请求 (MCP协议握手)
            sendInitialize(name);

            // 获取工具列表
            refreshToolsForServer(name);

        } catch (Exception e) {
            log.error("启动MCP服务器[{}]子进程失败: {}", name, e.getMessage(), e);
        }
    }

    /**
     * 初始化SSE模式的MCP服务器
     * MCP SSE协议流程:
     * 1. GET SSE端点 → 服务器返回SSE事件流，其中 event:endpoint 包含JSON-RPC POST地址
     * 2. POST JSON-RPC请求到该endpoint
     * 3. 响应通过SSE事件流返回（event:message）
     */
    private void initSseServer(String name, McpServerConfig config) {
        try {
            log.info("MCP服务器[{}]开始SSE握手: url={}", name, config.getUrl());
            String sseUrl = config.getUrl();

            // GET SSE端点，使用 InputStream 流式读取（SSE是长连接，不能用ofString否则会永久阻塞）
            HttpRequest sseRequest = HttpRequest.newBuilder()
                    .uri(URI.create(sseUrl))
                    .header("Accept", "text/event-stream")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<InputStream> sseResponse = httpClient.send(sseRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (sseResponse.statusCode() != 200) {
                log.warn("MCP服务器[{}] SSE握手失败, HTTP状态码={}", name, sseResponse.statusCode());
                return;
            }

            // 从SSE事件流中逐行读取，找到 event:endpoint 后立即提取data并关闭流
            String endpointUrl = null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(sseResponse.body(), StandardCharsets.UTF_8))) {
                endpointUrl = readEndpointFromSseStream(reader, sseUrl);
            }

            if (endpointUrl == null || endpointUrl.isBlank()) {
                log.warn("MCP服务器[{}] SSE握手未找到endpoint事件", name);
                return;
            }

            sseEndpoints.put(name, endpointUrl);
            log.info("MCP服务器[{}] SSE握手成功, JSON-RPC endpoint={}", name, endpointUrl);

            // 发送 initialize 请求（通过SSE endpoint POST）
            sendInitialize(name);

            // 获取工具列表
            refreshToolsForServer(name);

        } catch (Exception e) {
            log.error("MCP服务器[{}] SSE初始化失败: {}", name, e.getMessage(), e);
        }
    }

    /**
     * 从SSE事件流中逐行读取，提取 event:endpoint 对应的 data URL
     * SSE格式示例:
     *   event: endpoint\n
     *   data: /mcp/message?sessionId=xxx\n
     *   \n
     *
     * 注意：SSE是长连接流，找到endpoint后必须立即返回，由调用方关闭流。
     *
     * @param reader  SSE流的BufferedReader
     * @param baseUrl 原始SSE URL，用于将相对路径拼接为完整URL
     * @return 完整的JSON-RPC POST endpoint URL，未找到则返回null
     */
    private String readEndpointFromSseStream(BufferedReader reader, String baseUrl) throws IOException {
        if (useSupportDelegates()) {
            return mcpProtocolSupport.readEndpointFromSseStream(reader, baseUrl);
        }
        boolean isEndpointEvent = false;
        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            log.debug("SSE握手流读取行: {}", trimmed);

            // 检测 event: endpoint
            if (trimmed.equals("event: endpoint") || trimmed.equals("event:endpoint")) {
                isEndpointEvent = true;
                continue;
            }

            // 在endpoint事件内，读取 data: 字段
            if (isEndpointEvent && trimmed.startsWith("data:")) {
                String data = trimmed.substring(5).trim();
                // data可能是完整URL或相对路径
                if (data.startsWith("http://") || data.startsWith("https://")) {
                    return data;
                }
                // 相对路径：从baseUrl提取 scheme + authority 拼接
                try {
                    URI base = URI.create(baseUrl);
                    String authority = base.getScheme() + "://" + base.getAuthority();
                    return authority + (data.startsWith("/") ? data : "/" + data);
                } catch (Exception e) {
                    log.warn("拼接SSE endpoint URL失败: base={}, data={}", baseUrl, data);
                    return data;
                }
            }

            // 空行或其他event类型，重置标志
            if (isEndpointEvent && (trimmed.isEmpty() || trimmed.startsWith("event:"))) {
                isEndpointEvent = false;
            }
        }
        return null;
    }

    /**
     * 发送MCP initialize 握手请求
     */
    private void sendInitialize(String serverName) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("protocolVersion", "2024-11-05");
        Map<String, Object> clientInfo = new LinkedHashMap<>();
        clientInfo.put("name", "JavaLabAgent");
        clientInfo.put("version", "1.0.0");
        params.put("clientInfo", clientInfo);
        params.put("capabilities", Map.of());

        try {
            JsonNode response = sendJsonRpc(serverName, "initialize", params);
            if (response != null) {
                log.info("MCP服务器[{}] initialize成功: {}", serverName, response.path("serverInfo"));

                // 发送 initialized 通知
                sendJsonRpcNotification(serverName, "notifications/initialized", null);
            }
        } catch (Exception e) {
            log.warn("MCP服务器[{}] initialize失败: {}", serverName, e.getMessage());
        }
    }

    /**
     * 刷新指定服务器的工具列表
     */
    public void refreshToolsForServer(String serverName) {
        try {
            JsonNode response = sendJsonRpc(serverName, "tools/list", Map.of());
            if (response != null && response.has("tools")) {
                List<McpToolInfo> tools = new ArrayList<>();
                for (JsonNode toolNode : response.get("tools")) {
                    McpToolInfo tool = new McpToolInfo();
                    tool.setName(toolNode.path("name").asText());
                    tool.setDescription(toolNode.path("description").asText(""));
                    tool.setServerName(serverName);
                    if (toolNode.has("inputSchema")) {
                        tool.setInputSchema(objectMapper.convertValue(
                                toolNode.get("inputSchema"), new TypeReference<>() {}));
                    }
                    tools.add(tool);
                }
                toolsCache.put(serverName, tools);
                log.info("MCP服务器[{}]工具列表已刷新, 共{}个工具: {}",
                        serverName, tools.size(),
                        tools.stream().map(McpToolInfo::getName).toList());
                log.info("当前已加载的全部MCP工具: {}", formatAllMcpToolsForLog());
            } else {
                log.warn("MCP服务器[{}]未返回tools列表，response={}", serverName, response);
            }
        } catch (Exception e) {
            log.warn("获取MCP服务器[{}]工具列表失败: {}", serverName, e.getMessage());
        }
    }

    // ==================== 工具调用 ====================

    /**
     * 获取所有已启用MCP服务器的工具列表（合并）
     */
    public List<McpToolInfo> getAllTools() {
        List<McpToolInfo> allTools = new ArrayList<>();
        toolsCache.values().forEach(allTools::addAll);
        return allTools;
    }

    /**
     * 生成全部MCP工具的日志展示文本
     */
    public String formatAllMcpToolsForLog() {
        List<McpToolInfo> allTools = getAllTools();
        if (allTools.isEmpty()) {
            return "[]";
        }
        return allTools.stream()
                .map(tool -> tool.getName() + "@" + tool.getServerName())
                .sorted()
                .collect(Collectors.joining(", ", "[", "]"));
    }

    /**
     * 调用MCP工具
     *
     * @param toolName  工具名称
     * @param arguments 工具参数
     * @return 工具执行结果
     */
    public McpToolResult callTool(String toolName, Map<String, Object> arguments) {
        // 查找工具所属的服务器
        McpToolInfo toolInfo = findTool(toolName);
        if (toolInfo == null) {
            return McpToolResult.error("未找到MCP工具: " + toolName);
        }

        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("name", toolName);
            params.put("arguments", arguments != null ? arguments : Map.of());

            JsonNode response = sendJsonRpc(toolInfo.getServerName(), "tools/call", params);
            if (response == null) {
                return McpToolResult.error("MCP服务器无响应");
            }

            // 解析工具调用结果
            if (response.has("content")) {
                StringBuilder result = new StringBuilder();
                for (JsonNode content : response.get("content")) {
                    String type = content.path("type").asText("text");
                    if ("text".equals(type)) {
                        result.append(content.path("text").asText());
                    } else {
                        // 其他类型（image等），序列化为JSON
                        result.append(objectMapper.writeValueAsString(content));
                    }
                }
                boolean isError = response.path("isError").asBoolean(false);
                if (isError) {
                    return McpToolResult.error(result.toString());
                }
                return McpToolResult.success(result.toString());
            }

            // 兼容非标准响应
            return McpToolResult.success(objectMapper.writeValueAsString(response));

        } catch (Exception e) {
            log.error("调用MCP工具[{}]失败: {}", toolName, e.getMessage(), e);
            return McpToolResult.error("调用失败: " + e.getMessage());
        }
    }

    /**
     * 查找工具定义
     */
    public McpToolInfo findTool(String toolName) {
        for (List<McpToolInfo> tools : toolsCache.values()) {
            for (McpToolInfo tool : tools) {
                if (tool.getName().equals(toolName)) {
                    return tool;
                }
            }
        }
        return null;
    }

    // ==================== JSON-RPC 通信 ====================

    /**
     * 发送JSON-RPC请求并等待响应
     */
    private synchronized JsonNode sendJsonRpc(String serverName, String method, Map<String, Object> params) {
        // 所有 JSON-RPC 请求都从这里进入，再路由到具体协议。
        McpServerConfig config = currentConfig.getMcpServers().get(serverName);
        if (config == null) {
            log.warn("MCP服务器[{}]未找到配置", serverName);
            return null;
        }

        if ("sse".equalsIgnoreCase(config.getType())) {
            // SSE模式：POST到握手阶段获取的endpoint URL
            return sendJsonRpcSse(serverName, method, params);
        } else if ("http".equalsIgnoreCase(config.getType())) {
            return sendJsonRpcHttp(serverName, config, method, params);
        } else {
            return sendJsonRpcStdio(serverName, method, params);
        }
    }

    /**
     * 通过stdio发送JSON-RPC请求
     */
    private JsonNode sendJsonRpcStdio(String serverName, String method, Map<String, Object> params) {
        // stdio 模式适合本地子进程型 MCP 服务。
        BufferedWriter writer = stdioWriters.get(serverName);
        BufferedReader reader = stdioReaders.get(serverName);

        if (writer == null || reader == null) {
            log.warn("MCP服务器[{}]的stdio通道不可用", serverName);
            return null;
        }

        try {
            int id = requestIdCounter.getAndIncrement();

            // 构建JSON-RPC 2.0请求
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("jsonrpc", "2.0");
            request.put("id", id);
            request.put("method", method);
            if (params != null) {
                request.put("params", params);
            }

            String requestJson = objectMapper.writeValueAsString(request);
            log.debug("MCP[{}] -> {}", serverName, requestJson);

            // 写入stdin
            writer.write(requestJson);
            writer.newLine();
            writer.flush();

            // 从stdout读取响应
            String responseLine = reader.readLine();
            if (responseLine == null) {
                log.warn("MCP服务器[{}]返回null（进程可能已退出）", serverName);
                return null;
            }

            log.debug("MCP[{}] <- {}", serverName, responseLine);

            JsonNode responseNode = objectMapper.readTree(responseLine);

            // 检查错误
            if (responseNode.has("error")) {
                JsonNode error = responseNode.get("error");
                log.warn("MCP服务器[{}]返回错误: code={}, message={}",
                        serverName, error.path("code").asInt(), error.path("message").asText());
                return null;
            }

            return responseNode.path("result");

        } catch (Exception e) {
            log.error("MCP stdio通信异常[{}]: {}", serverName, e.getMessage());
            return null;
        }
    }

    /**
     * 发送JSON-RPC通知（无需响应）
     */
    private void sendJsonRpcNotification(String serverName, String method, Map<String, Object> params) {
        McpServerConfig config = currentConfig.getMcpServers().get(serverName);
        if (config == null) return;

        if ("stdio".equalsIgnoreCase(config.getType())) {
            BufferedWriter writer = stdioWriters.get(serverName);
            if (writer == null) return;
            try {
                Map<String, Object> notification = new LinkedHashMap<>();
                notification.put("jsonrpc", "2.0");
                notification.put("method", method);
                if (params != null) {
                    notification.put("params", params);
                }
                String json = objectMapper.writeValueAsString(notification);
                writer.write(json);
                writer.newLine();
                writer.flush();
            } catch (Exception e) {
                log.warn("发送MCP通知失败[{}]: {}", serverName, e.getMessage());
            }
        }
        // SSE模式：POST通知到endpoint（无需响应）
        if ("sse".equalsIgnoreCase(config.getType())) {
            String endpointUrl = sseEndpoints.get(serverName);
            if (endpointUrl == null) return;
            try {
                Map<String, Object> notification = new LinkedHashMap<>();
                notification.put("jsonrpc", "2.0");
                notification.put("method", method);
                if (params != null) {
                    notification.put("params", params);
                }
                String json = objectMapper.writeValueAsString(notification);
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(endpointUrl))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
                httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                log.warn("发送MCP SSE通知失败[{}]: {}", serverName, e.getMessage());
            }
        }
        // HTTP模式通知暂不实现
    }

    /**
     * 通过HTTP发送JSON-RPC请求
     */
    private JsonNode sendJsonRpcHttp(String serverName, McpServerConfig config, String method, Map<String, Object> params) {
        // http 模式直接发送标准 JSON-RPC 请求。
        try {
            int id = requestIdCounter.getAndIncrement();

            Map<String, Object> request = new LinkedHashMap<>();
            request.put("jsonrpc", "2.0");
            request.put("id", id);
            request.put("method", method);
            if (params != null) {
                request.put("params", params);
            }

            String requestJson = objectMapper.writeValueAsString(request);
            log.debug("MCP HTTP[{}] -> {}", serverName, requestJson);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(config.getUrl()))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            log.debug("MCP HTTP[{}] <- status={}, body={}", serverName, httpResponse.statusCode(), httpResponse.body());

            if (httpResponse.statusCode() != 200) {
                log.warn("MCP HTTP[{}]返回非200状态: {}", serverName, httpResponse.statusCode());
                return null;
            }

            JsonNode responseNode = objectMapper.readTree(httpResponse.body());

            if (responseNode.has("error")) {
                JsonNode error = responseNode.get("error");
                log.warn("MCP HTTP[{}]返回错误: code={}, message={}",
                        serverName, error.path("code").asInt(), error.path("message").asText());
                return null;
            }

            return responseNode.path("result");

        } catch (Exception e) {
            log.error("MCP HTTP通信异常[{}]: {}", serverName, e.getMessage());
            return null;
        }
    }

    /**
     * 通过SSE endpoint发送JSON-RPC请求
     * SSE模式下，POST请求发送到握手阶段获取的endpoint URL，
     * 响应可能是标准JSON-RPC响应，也可能通过SSE事件流返回
     */
    private JsonNode sendJsonRpcSse(String serverName, String method, Map<String, Object> params) {
        // SSE 模式下，请求和响应不一定在同一个通道返回。
        String endpointUrl = sseEndpoints.get(serverName);
        if (endpointUrl == null || endpointUrl.isBlank()) {
            log.warn("MCP服务器[{}]的SSE endpoint未就绪，无法发送请求", serverName);
            return null;
        }

        try {
            int id = requestIdCounter.getAndIncrement();

            // 构建JSON-RPC 2.0请求
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("jsonrpc", "2.0");
            request.put("id", id);
            request.put("method", method);
            if (params != null) {
                request.put("params", params);
            }

            String requestJson = objectMapper.writeValueAsString(request);
            log.debug("MCP SSE[{}] -> {}", serverName, requestJson);

            // POST到SSE握手获得的endpoint地址
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpointUrl))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            log.debug("MCP SSE[{}] <- status={}, body({}chars)", serverName, httpResponse.statusCode(),
                    httpResponse.body() != null ? httpResponse.body().length() : 0);

            if (httpResponse.statusCode() != 200 && httpResponse.statusCode() != 202) {
                log.warn("MCP SSE[{}]返回非成功状态: {}, body={}", serverName, httpResponse.statusCode(),
                        httpResponse.body() != null && httpResponse.body().length() > 500
                                ? httpResponse.body().substring(0, 500) : httpResponse.body());
                return null;
            }

            String body = httpResponse.body();
            if (body == null || body.isBlank()) {
                // 202 Accepted 表示请求已被接收，响应通过SSE事件流异步返回（通知类请求）
                if (httpResponse.statusCode() == 202) {
                    log.debug("MCP SSE[{}] 收到202 Accepted（通知/异步）", serverName);
                    return null;
                }
                log.warn("MCP SSE[{}]返回空响应体", serverName);
                return null;
            }

            // 尝试解析响应：可能是标准JSON-RPC响应，也可能是SSE事件流
            String jsonContent = body.trim();
            if (jsonContent.startsWith("{")) {
                // 标准JSON-RPC响应
                return parseJsonRpcResponse(serverName, jsonContent);
            }

            // SSE事件流格式：从 event:message 的 data 字段提取JSON-RPC响应
            return parseSseMessageResponse(serverName, body, id);

        } catch (Exception e) {
            log.error("MCP SSE通信异常[{}]: {}", serverName, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 解析标准JSON-RPC响应
     */
    private JsonNode parseJsonRpcResponse(String serverName, String jsonContent) {
        if (useSupportDelegates()) {
            return mcpProtocolSupport.parseJsonRpcResponse(serverName, jsonContent);
        }
        try {
            JsonNode responseNode = objectMapper.readTree(jsonContent);
            if (responseNode.has("error")) {
                JsonNode error = responseNode.get("error");
                log.warn("MCP[{}]返回错误: code={}, message={}",
                        serverName, error.path("code").asInt(), error.path("message").asText());
                return null;
            }
            return responseNode.path("result");
        } catch (Exception e) {
            log.warn("MCP[{}]解析JSON-RPC响应失败: {}", serverName, e.getMessage());
            return null;
        }
    }

    /**
     * 从SSE事件流中提取JSON-RPC响应
     * 查找匹配请求ID的 event:message 事件
     */
    private JsonNode parseSseMessageResponse(String serverName, String sseBody, int requestId) {
        if (useSupportDelegates()) {
            return mcpProtocolSupport.parseSseMessageResponse(serverName, sseBody, requestId);
        }
        String[] lines = sseBody.split("\n");
        boolean nextIsMessage = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals("event: message") || trimmed.equals("event:message")) {
                nextIsMessage = true;
                continue;
            }
            if (nextIsMessage && trimmed.startsWith("data:")) {
                String data = trimmed.substring(5).trim();
                try {
                    JsonNode node = objectMapper.readTree(data);
                    // 匹配请求ID或直接使用第一个message事件
                    if (!node.has("id") || node.path("id").asInt() == requestId) {
                        if (node.has("error")) {
                            JsonNode error = node.get("error");
                            log.warn("MCP SSE[{}]事件流返回错误: code={}, message={}",
                                    serverName, error.path("code").asInt(), error.path("message").asText());
                            return null;
                        }
                        return node.path("result");
                    }
                } catch (Exception e) {
                    log.debug("MCP SSE[{}]解析message事件失败: {}", serverName, e.getMessage());
                }
                nextIsMessage = false;
            }
            if (nextIsMessage && (trimmed.isEmpty() || trimmed.startsWith("event:"))) {
                nextIsMessage = false;
            }
        }
        log.warn("MCP SSE[{}]未从事件流中找到匹配的message响应, requestId={}", serverName, requestId);
        return null;
    }

    // ==================== 服务器动态管理 ====================

    /**
     * 添加一个新的MCP服务器配置
     *
     * @param name   服务器名称
     * @param config 服务器配置
     */
    public void addServer(String name, McpServerConfig config) {
        if (currentConfig.getMcpServers() == null) {
            currentConfig.setMcpServers(new LinkedHashMap<>());
        }
        currentConfig.getMcpServers().put(name, config);
        saveConfig();

        // 如果启用了，立即初始化
        if (config.isEnabled()) {
            try {
                initServer(name, config);
            } catch (Exception e) {
                log.error("初始化新增MCP服务器失败: name={}, error={}", name, e.getMessage());
            }
        }
        log.info("已添加MCP服务器: {}", name);
    }

    /**
     * 移除一个MCP服务器
     *
     * @param name 服务器名称
     */
    public void removeServer(String name) {
        // 先停止进程
        stopServer(name);
        // 从配置中移除
        if (currentConfig.getMcpServers() != null) {
            currentConfig.getMcpServers().remove(name);
        }
        toolsCache.remove(name);
        sseEndpoints.remove(name);
        saveConfig();
        log.info("已移除MCP服务器: {}", name);
    }

    /**
     * 停止MCP服务器
     */
    private void stopServer(String name) {
        Process process = stdioProcesses.remove(name);
        if (process != null) {
            try {
                BufferedWriter writer = stdioWriters.remove(name);
                if (writer != null) writer.close();
                BufferedReader reader = stdioReaders.remove(name);
                if (reader != null) reader.close();
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("停止MCP服务器进程异常: {}", e.getMessage());
            }
        }
    }

    /**
     * 重启指定MCP服务器
     */
    public void restartServer(String name) {
        stopServer(name);
        toolsCache.remove(name);
        McpServerConfig config = currentConfig.getMcpServers().get(name);
        if (config != null && config.isEnabled()) {
            initServer(name, config);
        }
    }

    /**
     * 测试MCP服务器连接（ping）
     *
     * @param name 服务器名称
     * @return 测试结果描述
     */
    public String testServer(String name) {
        try {
            JsonNode response = sendJsonRpc(name, "ping", Map.of());
            if (response != null) {
                return "连接成功";
            }
            // ping可能不被支持，尝试tools/list
            refreshToolsForServer(name);
            List<McpToolInfo> tools = toolsCache.get(name);
            if (tools != null && !tools.isEmpty()) {
                return "连接成功，发现" + tools.size() + "个工具";
            }
            return "服务器无响应";
        } catch (Exception e) {
            return "连接失败: " + e.getMessage();
        }
    }

    /**
     * 获取指定服务器的工具列表
     */
    public List<McpToolInfo> getServerTools(String serverName) {
        return toolsCache.getOrDefault(serverName, Collections.emptyList());
    }

    /**
     * 判断MCP是否有可用工具
     */
    public boolean hasAvailableTools() {
        return !toolsCache.isEmpty() && toolsCache.values().stream().anyMatch(list -> !list.isEmpty());
    }

    /**
     * 工具调用结果
     */
    public static class McpToolResult {
        private final boolean success;
        private final String content;
        private final String errorMessage;

        private McpToolResult(boolean success, String content, String errorMessage) {
            this.success = success;
            this.content = content;
            this.errorMessage = errorMessage;
        }

        public static McpToolResult success(String content) {
            return new McpToolResult(true, content, null);
        }

        public static McpToolResult error(String errorMessage) {
            return new McpToolResult(false, null, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public String getContent() { return content; }
        public String getErrorMessage() { return errorMessage; }
    }

    // 先通过委托方式平滑迁移，确认稳定后再删除旧实现。
    private boolean useSupportDelegates() {
        return true;
    }
}
