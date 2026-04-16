package com.cs.rag.service;

import com.cs.rag.mcp.McpServerConfig;
import com.cs.rag.mcp.McpToolInfo;
import com.cs.rag.mcp.McpToolsConfig;

import java.util.List;
import java.util.Map;

/**
 * MCP 服务。
 * 统一负责 MCP 配置、连接、工具发现和工具调用。
 */
public interface McpService {

    void loadConfig();

    void saveConfig();

    McpToolsConfig getConfig();

    void refreshToolsForServer(String serverName);

    List<McpToolInfo> getAllTools();

    String formatAllMcpToolsForLog();

    McpToolInfo findTool(String toolName);

    McpToolResult callTool(String toolName, Map<String, Object> arguments);

    void addServer(String name, McpServerConfig config);

    void removeServer(String name);

    void restartServer(String name);

    String testServer(String name);

    List<McpToolInfo> getServerTools(String serverName);

    int getCachedToolCount();

    boolean hasAvailableTools();

    class McpToolResult {
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
}
