package com.cs.rag.service.impl;

import com.cs.rag.mcp.McpServerConfig;
import com.cs.rag.mcp.McpToolInfo;
import com.cs.rag.mcp.McpToolsConfig;
import com.cs.rag.service.McpService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

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
 * MCP 服务实现。
 * 负责加载配置、管理 MCP 服务连接，并转发工具调用请求。
 */
@Slf4j
@Service
public class McpServiceImpl implements McpService {

    private final ObjectMapper objectMapper;

    /** 防止初始化锁，避免并发场景下同一服务重复初始化 */
    private final Object initLock = new Object();

    /** 正在初始化的服务集合，避免初始化过程中递归重入 */
    private final Set<String> initializingServers = ConcurrentHashMap.newKeySet();

    /** MCP配置文件路径 (classpath中) */
    private static final String CONFIG_FILE = "mcp-tools.json";

    /** 外部配置文件路径 (项目根目录下, 优先级高于classpath) */
    private static final String EXTERNAL_CONFIG_FILE = "mcp-tools.json";

    /** JSON-RPC 请求ID计数器 */
    private final AtomicInteger requestIdCounter = new AtomicInteger(1);

    /** 当前加载的MCP配置 */
    private volatile McpToolsConfig currentConfig;

    /** 已启动的stdio子进程 key=服务器名称 */
    private final Map<String, Process> stdioProcesses = new ConcurrentHashMap<>();

    /** 子进程的输入流写入器: key=服务器名称 */
    private final Map<String, BufferedWriter> stdioWriters = new ConcurrentHashMap<>();

    /** 子进程的输出流读取器: key=服务器名称 */
    private final Map<String, BufferedReader> stdioReaders = new ConcurrentHashMap<>();

    /** 从各MCP服务器获取的工具列表缓存: key=服务器名称 */
    private final Map<String, List<McpToolInfo>> toolsCache = new ConcurrentHashMap<>();

    /** SSE握手后获取到的JSON-RPC POST端点URL: key=服务器名称 */
    private final Map<String, String> sseEndpoints = new ConcurrentHashMap<>();

    /** HTTP客户端(用于http和sse模式) */
    private final HttpClient httpClient;

    public McpServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // ==================== 鐢熷懡鍛ㄦ湡绠＄悊 ====================

    /**
     * 搴旂敤鍚姩鏃朵粎鍔犺浇MCP閰嶇疆
     * 鎳掑垵濮嬪寲妯″紡涓嬶紝涓嶅湪鍚姩闃舵杩炴帴浠讳綍MCP鏈嶅姟
     */
    @PostConstruct
    public void init() {
        try {
            loadConfig();
            if (currentConfig == null || currentConfig.getMcpServers() == null) {
                log.info("MCP 服务初始化完成，当前没有可用的服务器配置");
            }
        } catch (Exception e) {
            log.warn("MCP 服务初始化异常: {}", e.getMessage());
        }
    }

    /**
     * 搴旂敤鍏抽棴鏃堕攢姣佹墍鏈夊瓙杩涚▼
     */
    @PreDestroy
    public void destroy() {
        log.info("MCP 服务正在关闭...");
        stdioProcesses.forEach((name, process) -> {
            try {
                // 鍏堝叧闂啓鍏ュ櫒
                BufferedWriter writer = stdioWriters.remove(name);
                if (writer != null) writer.close();
                // 鍐嶅叧闂鍙栧櫒
                BufferedReader reader = stdioReaders.remove(name);
                if (reader != null) reader.close();
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("关闭 MCP 服务异常: {}", e.getMessage());
            }
        });
        stdioProcesses.clear();
    }

    // ==================== 閰嶇疆绠＄悊 ====================

    /**
     * 加载MCP配置文件
     * 优先从项目根目录加载外部配置，找不到则从classpath加载
     */
    public void loadConfig() {
        try {
            String jsonContent = null;

            // 1. 优先从外部文件加载(支持运行时修改)
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
                log.info("未找到 MCP 配置文件，使用空配置启动");
                currentConfig = new McpToolsConfig();
                currentConfig.setMcpServers(new LinkedHashMap<>());
            }
        } catch (Exception e) {
            log.error("加载 MCP 配置文件失败: {}", e.getMessage(), e);
            currentConfig = new McpToolsConfig();
            currentConfig.setMcpServers(new LinkedHashMap<>());
        }
    }

    /**
     * 统一 MCP 配置，兼容仅填写url但未显式声明type的场景
     */
    private void normalizeConfig(McpToolsConfig config) {
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
                log.info("MCP 服务 [{}] 未显式配置 type，已根据 url 自动识别为 http", name);
            }

            // 自动将包含 /sse 路径的 URL 识别为 SSE 传输类型
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
        try {
            Path externalPath = Paths.get(EXTERNAL_CONFIG_FILE);
            String json = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(currentConfig);
            Files.writeString(externalPath, json, StandardCharsets.UTF_8);
            log.info("MCP 配置已保存到: {}", externalPath.toAbsolutePath());
        } catch (Exception e) {
            log.error("保存 MCP 配置失败: {}", e.getMessage(), e);
            throw new RuntimeException("保存 MCP 配置失败: " + e.getMessage());
        }
    }

    /**
     * 鑾峰彇褰撳墠閰嶇疆 (鍙鍓湰)
     */
    public McpToolsConfig getConfig() {
        return currentConfig;
    }

    // ==================== 鏈嶅姟鍣ㄧ鐞?====================

    /**
     * 鍒濆鍖栧崟涓狹CP鏈嶅姟鍣ㄨ繛鎺?
     */
    private void initServer(String name, McpServerConfig config) {
        if (config == null || !config.isEnabled()) {
            return;
        }
        // 杩欓噷鍙仛鍗忚鍒嗗彂锛屽叿浣撳疄鐜版斁鍒板悇鑷柟娉曘€?
        if ("stdio".equalsIgnoreCase(config.getType())) {
            initStdioServer(name, config);
        } else if ("sse".equalsIgnoreCase(config.getType())) {
            // SSE妯″紡锛氬厛鎻℃墜鑾峰彇JSON-RPC POST绔偣锛屽啀鑾峰彇宸ュ叿鍒楄〃
            initSseServer(name, config);
        } else if ("http".equalsIgnoreCase(config.getType())) {
            // HTTP妯″紡涓嶉渶瑕佸缓绔嬮暱杩炴帴锛岄娆′娇鐢ㄦ椂浠呭埛鏂板伐鍏风紦瀛?
            log.info("MCP 服务 [{}] 使用 HTTP 连接，url={}", name, config.getUrl());
            refreshToolsForServer(name);
        }
    }

    /**
     * 纭繚鎸囧畾鏈嶅姟鍣ㄥ凡瀹屾垚鎸夐渶鍒濆鍖?
     * stdio/sse 浼氬缓绔嬭繛鎺ュ苟鑾峰彇宸ュ叿鍒楄〃锛宧ttp 浼氬湪棣栨璁块棶鏃舵媺鍙栧伐鍏峰垪琛?
     */
    private void ensureServerReady(String serverName) {
        McpServerConfig config = getServerConfig(serverName);
        if (config == null) {
            return;
        }
        if (!config.isEnabled()) {
            log.info("MCP 服务 [{}] 未启用，跳过初始化", serverName);
            return;
        }

        synchronized (initLock) {
            if (isServerReady(serverName, config) || initializingServers.contains(serverName)) {
                return;
            }
            initializingServers.add(serverName);
            try {
                log.info("MCP 服务 [{}] 首次使用，开始延迟初始化", serverName);
                initServer(serverName, config);
            } finally {
                initializingServers.remove(serverName);
            }
        }
    }

    /**
     * 鍒ゆ柇鏈嶅姟鏄惁宸茬粡鍏峰鍙敤鐘舵€?
     */
    private boolean isServerReady(String serverName, McpServerConfig config) {
        if (config == null || !config.isEnabled()) {
            return false;
        }
        if ("stdio".equalsIgnoreCase(config.getType())) {
            return stdioProcesses.containsKey(serverName)
                    && stdioWriters.containsKey(serverName)
                    && stdioReaders.containsKey(serverName)
                    && toolsCache.containsKey(serverName);
        }
        if ("sse".equalsIgnoreCase(config.getType())) {
            return sseEndpoints.containsKey(serverName) && toolsCache.containsKey(serverName);
        }
        if ("http".equalsIgnoreCase(config.getType())) {
            return toolsCache.containsKey(serverName);
        }
        return false;
    }

    /**
     * 鑾峰彇鏈嶅姟鍣ㄩ厤缃?
     */
    private McpServerConfig getServerConfig(String serverName) {
        if (currentConfig == null || currentConfig.getMcpServers() == null) {
            return null;
        }
        return currentConfig.getMcpServers().get(serverName);
    }

    /**
     * 鎸夐渶鍔犺浇鍏ㄩ儴宸插惎鐢ㄦ湇鍔＄殑宸ュ叿缂撳瓨
     */
    private void ensureAllEnabledServersToolsLoaded() {
        if (currentConfig == null || currentConfig.getMcpServers() == null) {
            return;
        }
        currentConfig.getMcpServers().forEach((name, config) -> {
            if (config != null && config.isEnabled() && !toolsCache.containsKey(name)) {
                ensureServerReady(name);
            }
        });
    }

    /**
     * 鑾峰彇褰撳墠缂撳瓨涓殑鍏ㄩ儴宸ュ叿蹇収
     */
    private List<McpToolInfo> getCachedToolsSnapshot() {
        List<McpToolInfo> allTools = new ArrayList<>();
        toolsCache.values().forEach(allTools::addAll);
        return allTools;
    }

    /**
     * 鍒濆鍖杝tdio妯″紡鐨凪CP鏈嶅姟鍣紙鍚姩瀛愯繘绋嬶級
     */
    private void initStdioServer(String name, McpServerConfig config) {
        try {
            List<String> commandList = new ArrayList<>();
            commandList.add(config.getCommand());
            if (config.getArgs() != null) {
                commandList.addAll(config.getArgs());
            }

            ProcessBuilder pb = new ProcessBuilder(commandList);
            pb.redirectErrorStream(false); // 鍒嗗紑澶勭悊stderr

            // 璁剧疆鐜鍙橀噺
            if (config.getEnv() != null) {
                pb.environment().putAll(config.getEnv());
            }

            Process process = pb.start();
            stdioProcesses.put(name, process);

            // 璁剧疆杈撳叆杈撳嚭娴?
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            stdioWriters.put(name, writer);
            stdioReaders.put(name, reader);

            // 寮傛璇诲彇stderr锛堥槻姝㈤樆濉烇級
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

            // 鍙戦€?initialize 璇锋眰 (MCP鍗忚鎻℃墜)
            sendInitialize(name);

            // 鑾峰彇宸ュ叿鍒楄〃
            refreshToolsForServer(name);

        } catch (Exception e) {
            log.error("初始化 MCP 服务 [{}] 失败: {}", name, e.getMessage(), e);
        }
    }

    /**
     * 鍒濆鍖朣SE妯″紡鐨凪CP鏈嶅姟鍣?
     * MCP SSE鍗忚娴佺▼:
     * 1. GET SSE绔偣 鈫?鏈嶅姟鍣ㄨ繑鍥濻SE浜嬩欢娴侊紝鍏朵腑 event:endpoint 鍖呭惈JSON-RPC POST鍦板潃
     * 2. POST JSON-RPC璇锋眰鍒拌endpoint
     * 3. 鍝嶅簲閫氳繃SSE浜嬩欢娴佽繑鍥烇紙event:message锛?
     */
    private void initSseServer(String name, McpServerConfig config) {
        try {
            log.info("MCP 服务 [{}] 使用 SSE 连接，url={}", name, config.getUrl());
            String sseUrl = config.getUrl();

            HttpRequest sseRequest = HttpRequest.newBuilder()
                    .uri(URI.create(sseUrl))
                    .header("Accept", "text/event-stream")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<InputStream> sseResponse = httpClient.send(sseRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (sseResponse.statusCode() != 200) {
                log.warn("MCP 服务 [{}] SSE连接失败，HTTP状态码={}", name, sseResponse.statusCode());
                return;
            }

            String endpointUrl;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(sseResponse.body(), StandardCharsets.UTF_8))) {
                endpointUrl = readEndpointFromSseStream(reader, sseUrl);
            }

            if (endpointUrl == null || endpointUrl.isBlank()) {
                log.warn("MCP 服务 [{}] SSE连接失败，未找到 endpoint", name);
                return;
            }

            sseEndpoints.put(name, endpointUrl);
            log.info("MCP 服务 [{}] SSE连接成功，JSON-RPC endpoint={}", name, endpointUrl);

            sendInitialize(name);
            refreshToolsForServer(name);
        } catch (Exception e) {
            log.error("初始化 MCP 服务 [{}] 失败: {}", name, e.getMessage(), e);
        }
    }

    /**
     * 从 SSE 事件流中逐行读取，提取 event:endpoint 对应的 data URL
     */
    private String readEndpointFromSseStream(BufferedReader reader, String baseUrl) throws IOException {
        boolean isEndpointEvent = false;
        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            log.debug("SSE连接响应: {}", trimmed);

            if (trimmed.equals("event: endpoint") || trimmed.equals("event:endpoint")) {
                isEndpointEvent = true;
                continue;
            }

            if (isEndpointEvent && trimmed.startsWith("data:")) {
                String data = trimmed.substring(5).trim();
                if (data.startsWith("http://") || data.startsWith("https://")) {
                    return data;
                }
                try {
                    URI base = URI.create(baseUrl);
                    String authority = base.getScheme() + "://" + base.getAuthority();
                    return authority + (data.startsWith("/") ? data : "/" + data);
                } catch (Exception e) {
                    log.warn("解析 SSE endpoint URL 失败: base={}, data={}", baseUrl, data);
                    return data;
                }
            }

            if (isEndpointEvent && (trimmed.isEmpty() || trimmed.startsWith("event:"))) {
                isEndpointEvent = false;
            }
        }
        return null;
    }

    /**
     * 鍙戦€丮CP initialize 鎻℃墜璇锋眰
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
                log.info("MCP 服务 [{}] 初始化成功: {}", serverName, response.path("serverInfo"));

                // 鍙戦€?initialized 閫氱煡
                sendJsonRpcNotification(serverName, "notifications/initialized", null);
            }
        } catch (Exception e) {
            log.warn("MCP 服务 [{}] 初始化失败: {}", serverName, e.getMessage());
        }
    }

    /**
     * 鑾峰彇鏈嶅姟鍣ㄧ殑宸ュ叿鍒楄〃
     */
    public void refreshToolsForServer(String serverName) {
        try {
            McpServerConfig config = getServerConfig(serverName);
            if (config == null) {
                log.warn("刷新 MCP 服务 [{}] 工具列表失败: 配置不存在", serverName);
                return;
            }

            if (!"http".equalsIgnoreCase(config.getType()) && !initializingServers.contains(serverName)) {
                ensureServerReady(serverName);
            }
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
                log.info("MCP 服务 [{}] 工具列表刷新成功，发现 {} 个工具", serverName, tools.size());
            } else {
                log.warn("MCP 服务 [{}] 工具列表刷新失败，响应={}", serverName, response);
            }
        } catch (Exception e) {
            log.warn("刷新 MCP 服务 [{}] 工具列表失败: {}", serverName, e.getMessage());
        }
    }

    // ==================== 宸ュ叿璋冪敤 ====================

    /**
     * 鑾峰彇鎵€鏈夊凡鍚敤MCP鏈嶅姟鍣ㄧ殑宸ュ叿鍒楄〃锛堝悎骞讹級
     */
    public List<McpToolInfo> getAllTools() {
        ensureAllEnabledServersToolsLoaded();
        return getCachedToolsSnapshot();
    }

    /**
     * 鐢熸垚鍏ㄩ儴MCP宸ュ叿鐨勬棩蹇楀睍绀烘枃鏈?
     */
    public String formatAllMcpToolsForLog() {
        List<McpToolInfo> allTools = getCachedToolsSnapshot();
        if (allTools.isEmpty()) {
            return "[]";
        }
        return allTools.stream()
                .map(tool -> tool.getName() + "@" + tool.getServerName())
                .sorted()
                .collect(Collectors.joining(", ", "[", "]"));
    }

    /**
     * 调用 MCP 工具。
     */
    @Override
    public McpService.McpToolResult callTool(String toolName, Map<String, Object> arguments) {
        McpToolInfo toolInfo = findTool(toolName);
        if (toolInfo == null) {
            return McpService.McpToolResult.error("未找到 MCP 工具: " + toolName);
        }

        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("name", toolName);
            params.put("arguments", arguments != null ? arguments : Map.of());

            JsonNode response = sendJsonRpc(toolInfo.getServerName(), "tools/call", params);
            if (response == null) {
                return McpService.McpToolResult.error("MCP 服务无响应");
            }

            if (response.has("content")) {
                StringBuilder result = new StringBuilder();
                for (JsonNode content : response.get("content")) {
                    String type = content.path("type").asText("text");
                    if ("text".equals(type)) {
                        result.append(content.path("text").asText());
                    } else {
                        result.append(objectMapper.writeValueAsString(content));
                    }
                }
                boolean isError = response.path("isError").asBoolean(false);
                if (isError) {
                    return McpService.McpToolResult.error(result.toString());
                }
                return McpService.McpToolResult.success(result.toString());
            }

            return McpService.McpToolResult.success(objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            log.error("调用 MCP 工具 [{}] 失败: {}", toolName, e.getMessage(), e);
            return McpService.McpToolResult.error("调用失败: " + e.getMessage());
        }
    }

    /**
     * 鏌ユ壘宸ュ叿瀹氫箟
     */
    public McpToolInfo findTool(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return null;
        }
        for (List<McpToolInfo> tools : toolsCache.values()) {
            for (McpToolInfo tool : tools) {
                if (tool.getName().equals(toolName)) {
                    return tool;
                }
            }
        }

        if (currentConfig == null || currentConfig.getMcpServers() == null) {
            return null;
        }

        for (Map.Entry<String, McpServerConfig> entry : currentConfig.getMcpServers().entrySet()) {
            String serverName = entry.getKey();
            McpServerConfig config = entry.getValue();
            if (config == null || !config.isEnabled() || toolsCache.containsKey(serverName)) {
                continue;
            }
            ensureServerReady(serverName);
            List<McpToolInfo> serverTools = toolsCache.getOrDefault(serverName, Collections.emptyList());
            for (McpToolInfo tool : serverTools) {
                if (tool.getName().equals(toolName)) {
                    return tool;
                }
            }
        }
        return null;
    }

    // ==================== JSON-RPC 閫氫俊 ====================

    /**
     * 鍙戦€丣SON-RPC璇锋眰骞剁瓑寰呭搷搴?
     */
    private synchronized JsonNode sendJsonRpc(String serverName, String method, Map<String, Object> params) {
        McpServerConfig config = getServerConfig(serverName);
        if (config == null) {
            log.warn("MCP 服务 [{}] 未找到配置", serverName);
            return null;
        }

        if (!"tools/list".equals(method)
                && !isServerReady(serverName, config)
                && !initializingServers.contains(serverName)) {
            ensureServerReady(serverName);
            config = getServerConfig(serverName);
        }

        if ("sse".equalsIgnoreCase(config.getType())) {
            // SSE妯″紡锛歅OST鍒版彙鎵嬮樁娈佃幏鍙栫殑endpoint URL
            return sendJsonRpcSse(serverName, method, params);
        } else if ("http".equalsIgnoreCase(config.getType())) {
            return sendJsonRpcHttp(serverName, config, method, params);
        } else {
            return sendJsonRpcStdio(serverName, method, params);
        }
    }

    /**
     * 閫氳繃stdio鍙戦€丣SON-RPC璇锋眰
     */
    private JsonNode sendJsonRpcStdio(String serverName, String method, Map<String, Object> params) {
        BufferedWriter writer = stdioWriters.get(serverName);
        BufferedReader reader = stdioReaders.get(serverName);

        if (writer == null || reader == null) {
            log.warn("MCP 服务 [{}] stdio连接异常", serverName);
            return null;
        }

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
            log.debug("MCP[{}] -> {}", serverName, requestJson);

            writer.write(requestJson);
            writer.newLine();
            writer.flush();

            String responseLine = reader.readLine();
            if (responseLine == null) {
                log.warn("MCP 服务 [{}] stdio连接异常，响应为空", serverName);
                return null;
            }

            log.debug("MCP[{}] <- {}", serverName, responseLine);
            JsonNode responseNode = objectMapper.readTree(responseLine);
            if (responseNode.has("error")) {
                JsonNode error = responseNode.get("error");
                log.warn("MCP 服务 [{}] 响应异常: code={}, message={}",
                        serverName, error.path("code").asInt(), error.path("message").asText());
                return null;
            }
            return responseNode.path("result");
        } catch (Exception e) {
            log.error("MCP stdio连接异常 [{}]: {}", serverName, e.getMessage());
            return null;
        }
    }

    /**
     * 鍙戦€丣SON-RPC閫氱煡锛堟棤闇€鍝嶅簲锛?
     */
    private void sendJsonRpcNotification(String serverName, String method, Map<String, Object> params) {
        McpServerConfig config = getServerConfig(serverName);
        if (config == null) {
            return;
        }

        if ("stdio".equalsIgnoreCase(config.getType())) {
            BufferedWriter writer = stdioWriters.get(serverName);
            if (writer == null) {
                return;
            }
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
                log.warn("发送 MCP 通知失败 [{}]: {}", serverName, e.getMessage());
            }
        }

        if ("sse".equalsIgnoreCase(config.getType())) {
            String endpointUrl = sseEndpoints.get(serverName);
            if (endpointUrl == null) {
                return;
            }
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
                log.warn("发送 MCP SSE 通知失败 [{}]: {}", serverName, e.getMessage());
            }
        }
    }

    /**
     * 閫氳繃HTTP鍙戦€丣SON-RPC璇锋眰
     */
    private JsonNode sendJsonRpcHttp(String serverName, McpServerConfig config, String method, Map<String, Object> params) {
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
                log.warn("MCP HTTP[{}] 响应异常，状态码={}", serverName, httpResponse.statusCode());
                return null;
            }

            JsonNode responseNode = objectMapper.readTree(httpResponse.body());
            if (responseNode.has("error")) {
                JsonNode error = responseNode.get("error");
                log.warn("MCP HTTP[{}] 响应异常: code={}, message={}",
                        serverName, error.path("code").asInt(), error.path("message").asText());
                return null;
            }
            return responseNode.path("result");
        } catch (Exception e) {
            log.error("MCP HTTP连接异常 [{}]: {}", serverName, e.getMessage());
            return null;
        }
    }

    /**
     * 閫氳繃SSE endpoint鍙戦€丣SON-RPC璇锋眰
     */
    private JsonNode sendJsonRpcSse(String serverName, String method, Map<String, Object> params) {
        String endpointUrl = sseEndpoints.get(serverName);
        if (endpointUrl == null || endpointUrl.isBlank()) {
            log.warn("MCP 服务 [{}] SSE连接异常，未找到 endpoint", serverName);
            return null;
        }

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
            log.debug("MCP SSE[{}] -> {}", serverName, requestJson);

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
                log.warn("MCP SSE[{}] 响应异常，状态码={}", serverName, httpResponse.statusCode());
                return null;
            }

            String body = httpResponse.body();
            if (body == null || body.isBlank()) {
                if (httpResponse.statusCode() == 202) {
                    log.debug("MCP SSE[{}] 响应成功，状态码 202", serverName);
                    return null;
                }
                log.warn("MCP SSE[{}] 响应异常，响应体为空", serverName);
                return null;
            }

            String jsonContent = body.trim();
            if (jsonContent.startsWith("{")) {
                return parseJsonRpcResponse(serverName, jsonContent);
            }

            return parseSseMessageResponse(serverName, body, id);
        } catch (Exception e) {
            log.error("MCP SSE连接异常 [{}]: {}", serverName, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 解析标准 JSON-RPC 响应体
     */
    private JsonNode parseJsonRpcResponse(String serverName, String jsonContent) {
        try {
            JsonNode responseNode = objectMapper.readTree(jsonContent);
            if (responseNode.has("error")) {
                JsonNode error = responseNode.get("error");
                log.warn("MCP[{}] 响应异常: code={}, message={}",
                        serverName, error.path("code").asInt(), error.path("message").asText());
                return null;
            }
            return responseNode.path("result");
        } catch (Exception e) {
            log.warn("解析 JSON-RPC 响应失败 [{}]: {}", serverName, e.getMessage());
            return null;
        }
    }

    /**
     * 从 SSE 事件流中提取 JSON-RPC 响应
     */
    private JsonNode parseSseMessageResponse(String serverName, String sseBody, int requestId) {
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
                    if (!node.has("id") || node.path("id").asInt() == requestId) {
                        if (node.has("error")) {
                            JsonNode error = node.get("error");
                            log.warn("MCP SSE[{}] 响应异常: code={}, message={}",
                                    serverName, error.path("code").asInt(), error.path("message").asText());
                            return null;
                        }
                        return node.path("result");
                    }
                } catch (Exception e) {
                    log.debug("解析 SSE message 失败 [{}]: {}", serverName, e.getMessage());
                }
                nextIsMessage = false;
            }
            if (nextIsMessage && (trimmed.isEmpty() || trimmed.startsWith("event:"))) {
                nextIsMessage = false;
            }
        }
        log.warn("MCP SSE[{}] 响应异常，未找到 message，requestId={}", serverName, requestId);
        return null;
    }

    // ==================== 鏈嶅姟鍣ㄥ姩鎬佺鐞?====================

    /**
     * 娣诲姞涓€涓柊鐨凪CP鏈嶅姟鍣ㄩ厤缃?
     */
    public void addServer(String name, McpServerConfig config) {
        if (currentConfig == null) {
            currentConfig = new McpToolsConfig();
        }
        if (currentConfig.getMcpServers() == null) {
            currentConfig.setMcpServers(new LinkedHashMap<>());
        }
        currentConfig.getMcpServers().put(name, config);
        saveConfig();
        log.info("已添加 MCP 服务: {}", name);
    }

    /**
     * 绉婚櫎涓€涓狹CP鏈嶅姟鍣?
     */
    public void removeServer(String name) {
        stopServer(name);
        if (currentConfig != null && currentConfig.getMcpServers() != null) {
            currentConfig.getMcpServers().remove(name);
        }
        toolsCache.remove(name);
        sseEndpoints.remove(name);
        saveConfig();
        log.info("已移除 MCP 服务: {}", name);
    }

    /**
     * 鍋滄MCP鏈嶅姟鍣?
     */
    private void stopServer(String name) {
        Process process = stdioProcesses.remove(name);
        if (process != null) {
            try {
                BufferedWriter writer = stdioWriters.remove(name);
                if (writer != null) {
                    writer.close();
                }
                BufferedReader reader = stdioReaders.remove(name);
                if (reader != null) {
                    reader.close();
                }
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("关闭 MCP 服务异常: {}", e.getMessage());
            }
        }
    }

    /**
     * 閲嶅惎鎸囧畾MCP鏈嶅姟鍣?
     */
    public void restartServer(String name) {
        stopServer(name);
        toolsCache.remove(name);
        sseEndpoints.remove(name);
        McpServerConfig config = getServerConfig(name);
        if (config != null && config.isEnabled()) {
            log.info("MCP 服务 [{}] 正在重启...", name);
        }
    }

    /**
     * 娴嬭瘯MCP鏈嶅姟鍣ㄨ繛鎺ワ紙ping锛?
     */
    public String testServer(String name) {
        try {
            ensureServerReady(name);
            JsonNode response = sendJsonRpc(name, "ping", Map.of());
            if (response != null) {
                return "连接成功";
            }
            refreshToolsForServer(name);
            List<McpToolInfo> tools = toolsCache.get(name);
            if (tools != null && !tools.isEmpty()) {
                return "连接成功，发现 " + tools.size() + " 个工具";
            }
            return "服务无响应";
        } catch (Exception e) {
            return "连接失败: " + e.getMessage();
        }
    }

    /**
     * 鑾峰彇鎸囧畾鏈嶅姟鍣ㄧ殑宸ュ叿鍒楄〃
     */
    public List<McpToolInfo> getServerTools(String serverName) {
        return toolsCache.getOrDefault(serverName, Collections.emptyList());
    }

    /**
     * 娴嬭瘯MCP鏈嶅姟鍣ㄨ繛鎺ワ紙ping锛?
     */
    public int getCachedToolCount() {
        return getCachedToolsSnapshot().size();
    }

    /**
     * 鍒ゆ柇MCP鏄惁鏈夊彲鐢ㄥ伐鍏?
     */
    public boolean hasAvailableTools() {
        return !toolsCache.isEmpty() && toolsCache.values().stream().anyMatch(list -> !list.isEmpty());
    }
}
