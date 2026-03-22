package com.cs.rag.controller;

import com.cs.rag.common.ApplicationConstant;
import com.cs.rag.mcp.McpClientManager;
import com.cs.rag.mcp.McpServerConfig;
import com.cs.rag.mcp.McpToolInfo;
import com.cs.rag.mcp.McpToolsConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * MCP工具管理控制器
 * 提供MCP服务器的增删查改、工具列表查询、连接测试等接口
 *
 * @author caoshuai
 */
@Tag(name = "McpController", description = "MCP工具管理接口")
@Slf4j
@RestController
@RequestMapping(ApplicationConstant.API_VERSION + "/mcp")
public class McpController {

    private final McpClientManager mcpClientManager;

    public McpController(McpClientManager mcpClientManager) {
        this.mcpClientManager = mcpClientManager;
    }

    // ==================== 服务器管理 ====================

    /**
     * 获取所有MCP服务器配置列表
     */
    @Operation(summary = "listServers", description = "获取所有MCP服务器配置")
    @GetMapping("/servers")
    public Map<String, Object> listServers() {
        McpToolsConfig config = mcpClientManager.getConfig();
        Map<String, McpServerConfig> servers = config.getMcpServers();

        // 构造响应，为每个服务器附加工具数量信息
        List<Map<String, Object>> serverList = new ArrayList<>();
        if (servers != null) {
            servers.forEach((name, serverConfig) -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", name);
                item.put("enabled", serverConfig.isEnabled());
                item.put("type", serverConfig.getType());
                item.put("command", serverConfig.getCommand());
                item.put("args", serverConfig.getArgs());
                item.put("url", serverConfig.getUrl());
                // 对 env 中的敏感值进行脱敏处理（如 API Key）
                item.put("env", maskEnvValues(serverConfig.getEnv()));
                item.put("description", serverConfig.getDescription());
                // 附加已发现的工具数量
                List<McpToolInfo> tools = mcpClientManager.getServerTools(name);
                item.put("toolCount", tools.size());
                item.put("toolNames", tools.stream().map(McpToolInfo::getName).toList());
                // 返回工具详情列表（含 name 和 description），供前端 tooltip 使用
                item.put("tools", tools.stream().map(t -> {
                    Map<String, Object> toolItem = new LinkedHashMap<>();
                    toolItem.put("name", t.getName());
                    toolItem.put("description", t.getDescription());
                    return toolItem;
                }).toList());
                serverList.add(item);
            });
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("servers", serverList);
        result.put("totalTools", mcpClientManager.getAllTools().size());
        return result;
    }

    /**
     * 添加MCP服务器
     */
    @Operation(summary = "addServer", description = "添加MCP服务器配置")
    @PostMapping("/servers")
    public Map<String, Object> addServer(@RequestBody Map<String, Object> request) {
        String name = (String) request.get("name");
        if (name == null || name.isBlank()) {
            return Map.of("success", false, "message", "服务器名称不能为空");
        }

        // 检查是否已存在
        McpToolsConfig config = mcpClientManager.getConfig();
        if (config.getMcpServers() != null && config.getMcpServers().containsKey(name)) {
            return Map.of("success", false, "message", "服务器名称已存在: " + name);
        }

        McpServerConfig serverConfig = new McpServerConfig();
        serverConfig.setEnabled(getBoolean(request, "enabled", true));
        serverConfig.setType((String) request.getOrDefault("type", "stdio"));
        serverConfig.setCommand((String) request.get("command"));
        serverConfig.setDescription((String) request.get("description"));
        serverConfig.setUrl((String) request.get("url"));

        // 解析args
        Object argsObj = request.get("args");
        if (argsObj instanceof List<?> argsList) {
            serverConfig.setArgs(argsList.stream().map(String::valueOf).toList());
        } else if (argsObj instanceof String argsStr) {
            // 支持逗号分隔的字符串
            serverConfig.setArgs(Arrays.asList(argsStr.split("\\s*,\\s*")));
        }

        // 解析env
        Object envObj = request.get("env");
        if (envObj instanceof Map<?, ?> envMap) {
            Map<String, String> env = new LinkedHashMap<>();
            envMap.forEach((k, v) -> env.put(String.valueOf(k), String.valueOf(v)));
            serverConfig.setEnv(env);
        }

        try {
            mcpClientManager.addServer(name, serverConfig);
            log.info("已添加MCP服务器: name={}, type={}", name, serverConfig.getType());
            return Map.of("success", true, "message", "添加成功");
        } catch (Exception e) {
            log.error("添加MCP服务器失败: {}", e.getMessage(), e);
            return Map.of("success", false, "message", "添加失败: " + e.getMessage());
        }
    }

    /**
     * 删除MCP服务器
     */
    @Operation(summary = "removeServer", description = "删除MCP服务器配置")
    @DeleteMapping("/servers/{name}")
    public Map<String, Object> removeServer(@PathVariable String name) {
        try {
            mcpClientManager.removeServer(name);
            log.info("已删除MCP服务器: {}", name);
            return Map.of("success", true, "message", "删除成功");
        } catch (Exception e) {
            log.error("删除MCP服务器失败: {}", e.getMessage(), e);
            return Map.of("success", false, "message", "删除失败: " + e.getMessage());
        }
    }

    /**
     * 更新MCP服务器配置
     */
    @Operation(summary = "updateServer", description = "更新MCP服务器配置")
    @PutMapping("/servers/{name}")
    public Map<String, Object> updateServer(@PathVariable String name, @RequestBody Map<String, Object> request) {
        McpToolsConfig config = mcpClientManager.getConfig();
        if (config.getMcpServers() == null || !config.getMcpServers().containsKey(name)) {
            return Map.of("success", false, "message", "服务器不存在: " + name);
        }

        McpServerConfig serverConfig = config.getMcpServers().get(name);

        // 更新字段（只更新传入的字段）
        if (request.containsKey("enabled")) {
            serverConfig.setEnabled(getBoolean(request, "enabled", true));
        }
        if (request.containsKey("type")) {
            serverConfig.setType((String) request.get("type"));
        }
        if (request.containsKey("command")) {
            serverConfig.setCommand((String) request.get("command"));
        }
        if (request.containsKey("description")) {
            serverConfig.setDescription((String) request.get("description"));
        }
        if (request.containsKey("url")) {
            serverConfig.setUrl((String) request.get("url"));
        }
        if (request.containsKey("args")) {
            Object argsObj = request.get("args");
            if (argsObj instanceof List<?> argsList) {
                serverConfig.setArgs(argsList.stream().map(String::valueOf).toList());
            }
        }
        if (request.containsKey("env")) {
            Object envObj = request.get("env");
            if (envObj instanceof Map<?, ?> envMap) {
                Map<String, String> env = new LinkedHashMap<>();
                envMap.forEach((k, v) -> env.put(String.valueOf(k), String.valueOf(v)));
                serverConfig.setEnv(env);
            }
        }

        try {
            mcpClientManager.saveConfig();
            // 重启该服务器以应用新配置
            mcpClientManager.restartServer(name);
            log.info("已更新MCP服务器: {}", name);
            return Map.of("success", true, "message", "更新成功");
        } catch (Exception e) {
            log.error("更新MCP服务器失败: {}", e.getMessage(), e);
            return Map.of("success", false, "message", "更新失败: " + e.getMessage());
        }
    }

    /**
     * 测试MCP服务器连接
     */
    @Operation(summary = "testServer", description = "测试MCP服务器连接")
    @PostMapping("/servers/{name}/test")
    public Map<String, Object> testServer(@PathVariable String name) {
        try {
            String result = mcpClientManager.testServer(name);
            boolean success = result.contains("成功");
            return Map.of("success", success, "message", result);
        } catch (Exception e) {
            return Map.of("success", false, "message", "测试失败: " + e.getMessage());
        }
    }

    /**
     * 重启MCP服务器
     */
    @Operation(summary = "restartServer", description = "重启MCP服务器")
    @PostMapping("/servers/{name}/restart")
    public Map<String, Object> restartServer(@PathVariable String name) {
        try {
            mcpClientManager.restartServer(name);
            return Map.of("success", true, "message", "重启成功");
        } catch (Exception e) {
            return Map.of("success", false, "message", "重启失败: " + e.getMessage());
        }
    }

    // ==================== 工具查询 ====================

    /**
     * 获取所有可用的MCP工具列表（合并所有服务器）
     */
    @Operation(summary = "listTools", description = "获取所有可用的MCP工具列表")
    @GetMapping("/tools")
    public Map<String, Object> listTools() {
        List<McpToolInfo> tools = mcpClientManager.getAllTools();
        List<Map<String, Object>> toolList = new ArrayList<>();
        for (McpToolInfo tool : tools) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", tool.getName());
            item.put("description", tool.getDescription());
            item.put("serverName", tool.getServerName());
            item.put("inputSchema", tool.getInputSchema());
            toolList.add(item);
        }
        return Map.of("tools", toolList, "count", toolList.size());
    }

    /**
     * 刷新指定服务器的工具列表
     */
    @Operation(summary = "refreshTools", description = "刷新指定服务器的工具列表")
    @PostMapping("/servers/{name}/refresh")
    public Map<String, Object> refreshTools(@PathVariable String name) {
        try {
            mcpClientManager.refreshToolsForServer(name);
            List<McpToolInfo> tools = mcpClientManager.getServerTools(name);
            return Map.of("success", true, "message", "刷新成功，发现" + tools.size() + "个工具",
                    "tools", tools.stream().map(McpToolInfo::getName).toList());
        } catch (Exception e) {
            return Map.of("success", false, "message", "刷新失败: " + e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 对环境变量的值进行脱敏处理
     * 保留前4位和后4位，中间用 **** 替代；短于8位的值全部替换为 ****
     */
    private Map<String, String> maskEnvValues(Map<String, String> env) {
        if (env == null || env.isEmpty()) {
            return env;
        }
        Map<String, String> masked = new LinkedHashMap<>();
        env.forEach((key, value) -> {
            if (value == null || value.length() <= 8) {
                // 过短的值全部脱敏
                masked.put(key, "****");
            } else {
                // 保留前4位和后4位
                masked.put(key, value.substring(0, 4) + "****" + value.substring(value.length() - 4));
            }
        });
        return masked;
    }

    private boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object val = map.get(key);
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return Boolean.parseBoolean(s);
        return defaultValue;
    }
}
