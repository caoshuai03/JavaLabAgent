package com.cs.rag.tools;

import com.cs.rag.mcp.McpClientManager;
import com.cs.rag.service.ReactAgentToolService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP 工具执行器。
 * 负责将非内置工具调用转发给 MCP ClientManager。
 */
@Component
public class McpToolExecutor {

    private final McpClientManager mcpClientManager;

    public McpToolExecutor(McpClientManager mcpClientManager) {
        this.mcpClientManager = mcpClientManager;
    }

    /**
     * 执行指定 MCP 工具。
     */
    public ReactAgentToolService.ToolExecutionResult execute(String toolName, Map<String, Object> input) {
        long start = System.currentTimeMillis();
        try {
            McpClientManager.McpToolResult mcpResult = mcpClientManager.callTool(toolName, input);
            if (mcpResult.isSuccess()) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("source", "mcp");
                data.put("result", mcpResult.getContent());
                return ReactAgentToolService.ToolExecutionResult.success(
                        toolName,
                        data,
                        null,
                        "mcp",
                        System.currentTimeMillis() - start,
                        Map.of()
                );
            }
            return ReactAgentToolService.ToolExecutionResult.error(
                    toolName,
                    "MCP tool error: " + mcpResult.getErrorMessage(),
                    "mcp",
                    System.currentTimeMillis() - start,
                    Map.of()
            );
        } catch (Exception e) {
            return ReactAgentToolService.ToolExecutionResult.error(
                    toolName,
                    "MCP tool invocation failed: " + e.getMessage(),
                    "mcp",
                    System.currentTimeMillis() - start,
                    Map.of()
            );
        }
    }
}
