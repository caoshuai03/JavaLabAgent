package com.cs.rag.controller;

import com.cs.rag.common.ApplicationConstant;
import com.cs.rag.pojo.entity.McpServerConfig;
import com.cs.rag.pojo.entity.McpToolInfo;
import com.cs.rag.pojo.entity.McpToolsConfig;
import com.cs.rag.service.McpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 管理控制器。
 */
@Tag(name = "McpController", description = "MCP 管理接口")
@Slf4j
@RestController
@RequestMapping(ApplicationConstant.API_VERSION + "/mcp")
public class McpController {

    private final McpService mcpService;

    public McpController(McpService mcpService) {
        this.mcpService = mcpService;
    }

    @Operation(summary = "listServers", description = "获取所有 MCP 服务器配置")
    @GetMapping("/servers")
    public Map<String, Object> listServers() {
        McpToolsConfig config = mcpService.getConfig();
        Map<String, McpServerConfig> servers = config == null ? null : config.getMcpServers();

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
                item.put("env", maskEnvValues(serverConfig.getEnv()));
                item.put("description", serverConfig.getDescription());

                List<McpToolInfo> tools = mcpService.getServerTools(name);
                item.put("toolCount", tools.size());
                item.put("toolNames", tools.stream().map(McpToolInfo::getName).toList());
                item.put("tools", tools.stream().map(tool -> {
                    Map<String, Object> toolItem = new LinkedHashMap<>();
                    toolItem.put("name", tool.getName());
                    toolItem.put("description", tool.getDescription());
                    return toolItem;
                }).toList());
                serverList.add(item);
            });
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("servers", serverList);
        result.put("totalTools", mcpService.getCachedToolCount());
        return result;
    }

    @Operation(summary = "addServer", description = "添加 MCP 服务器配置")
    @PostMapping("/servers")
    public Map<String, Object> addServer(@RequestBody Map<String, Object> request) {
        String name = (String) request.get("name");
        if (name == null || name.isBlank()) {
            return Map.of("success", false, "message", "服务器名称不能为空");
        }

        McpToolsConfig config = mcpService.getConfig();
        if (config != null && config.getMcpServers() != null && config.getMcpServers().containsKey(name)) {
            return Map.of("success", false, "message", "服务器名称已存在: " + name);
        }

        McpServerConfig serverConfig = new McpServerConfig();
        serverConfig.setEnabled(getBoolean(request, "enabled", true));
        serverConfig.setType((String) request.getOrDefault("type", "stdio"));
        serverConfig.setCommand((String) request.get("command"));
        serverConfig.setDescription((String) request.get("description"));
        serverConfig.setUrl((String) request.get("url"));

        Object argsObj = request.get("args");
        if (argsObj instanceof List<?> argsList) {
            serverConfig.setArgs(argsList.stream().map(String::valueOf).toList());
        } else if (argsObj instanceof String argsStr) {
            serverConfig.setArgs(Arrays.asList(argsStr.split("\\s*,\\s*")));
        }

        Object envObj = request.get("env");
        if (envObj instanceof Map<?, ?> envMap) {
            Map<String, String> env = new LinkedHashMap<>();
            envMap.forEach((k, v) -> env.put(String.valueOf(k), String.valueOf(v)));
            serverConfig.setEnv(env);
        }

        try {
            mcpService.addServer(name, serverConfig);
            return Map.of("success", true, "message", "添加成功");
        } catch (Exception e) {
            log.error("添加 MCP 服务器失败", e);
            return Map.of("success", false, "message", "添加失败: " + e.getMessage());
        }
    }

    @Operation(summary = "removeServer", description = "删除 MCP 服务器配置")
    @DeleteMapping("/servers/{name}")
    public Map<String, Object> removeServer(@PathVariable String name) {
        try {
            mcpService.removeServer(name);
            return Map.of("success", true, "message", "删除成功");
        } catch (Exception e) {
            log.error("删除 MCP 服务器失败", e);
            return Map.of("success", false, "message", "删除失败: " + e.getMessage());
        }
    }

    @Operation(summary = "updateServer", description = "更新 MCP 服务器配置")
    @PutMapping("/servers/{name}")
    public Map<String, Object> updateServer(@PathVariable String name, @RequestBody Map<String, Object> request) {
        McpToolsConfig config = mcpService.getConfig();
        if (config == null || config.getMcpServers() == null || !config.getMcpServers().containsKey(name)) {
            return Map.of("success", false, "message", "服务器不存在: " + name);
        }

        McpServerConfig serverConfig = config.getMcpServers().get(name);
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
            mcpService.saveConfig();
            mcpService.restartServer(name);
            return Map.of("success", true, "message", "更新成功");
        } catch (Exception e) {
            log.error("更新 MCP 服务器失败", e);
            return Map.of("success", false, "message", "更新失败: " + e.getMessage());
        }
    }

    @Operation(summary = "testServer", description = "测试 MCP 服务器连接")
    @PostMapping("/servers/{name}/test")
    public Map<String, Object> testServer(@PathVariable String name) {
        try {
            String result = mcpService.testServer(name);
            return Map.of("success", result.contains("成功"), "message", result);
        } catch (Exception e) {
            return Map.of("success", false, "message", "测试失败: " + e.getMessage());
        }
    }

    @Operation(summary = "restartServer", description = "重启 MCP 服务器")
    @PostMapping("/servers/{name}/restart")
    public Map<String, Object> restartServer(@PathVariable String name) {
        try {
            mcpService.restartServer(name);
            return Map.of("success", true, "message", "重启成功");
        } catch (Exception e) {
            return Map.of("success", false, "message", "重启失败: " + e.getMessage());
        }
    }

    @Operation(summary = "listTools", description = "获取所有可用 MCP 工具")
    @GetMapping("/tools")
    public Map<String, Object> listTools() {
        List<McpToolInfo> tools = mcpService.getAllTools();
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

    @Operation(summary = "refreshTools", description = "刷新指定服务器的工具列表")
    @PostMapping("/servers/{name}/refresh")
    public Map<String, Object> refreshTools(@PathVariable String name) {
        try {
            mcpService.refreshToolsForServer(name);
            List<McpToolInfo> tools = mcpService.getServerTools(name);
            return Map.of(
                    "success", true,
                    "message", "刷新成功，发现 " + tools.size() + " 个工具",
                    "tools", tools.stream().map(McpToolInfo::getName).toList()
            );
        } catch (Exception e) {
            return Map.of("success", false, "message", "刷新失败: " + e.getMessage());
        }
    }

    private Map<String, String> maskEnvValues(Map<String, String> env) {
        if (env == null || env.isEmpty()) {
            return env;
        }
        Map<String, String> masked = new LinkedHashMap<>();
        env.forEach((key, value) -> {
            if (value == null || value.length() <= 8) {
                masked.put(key, "****");
            } else {
                masked.put(key, value.substring(0, 4) + "****" + value.substring(value.length() - 4));
            }
        });
        return masked;
    }

    private boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object val = map.get(key);
        if (val instanceof Boolean bool) {
            return bool;
        }
        if (val instanceof String str) {
            return Boolean.parseBoolean(str);
        }
        return defaultValue;
    }
}

