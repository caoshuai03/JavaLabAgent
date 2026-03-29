package com.cs.rag.service;

import java.util.List;
import java.util.Map;

public interface ReactAgentToolService {

    ToolExecutionResult execute(String toolName, Map<String, Object> input, String sessionId, Long userId);

    String getToolDescription(String toolName);

    List<Map<String, Object>> toolSchemas();

    class ToolExecutionResult {
        private final String toolName;
        private final boolean success;
        private final Object data;
        private final String errorMessage;

        private ToolExecutionResult(String toolName, boolean success, Object data, String errorMessage) {
            this.toolName = toolName;
            this.success = success;
            this.data = data;
            this.errorMessage = errorMessage;
        }

        public static ToolExecutionResult success(String toolName, Object data) {
            return new ToolExecutionResult(toolName, true, data, null);
        }

        public static ToolExecutionResult error(String toolName, String errorMessage) {
            return new ToolExecutionResult(toolName, false, null, errorMessage);
        }

        public String getToolName() {
            return toolName;
        }

        public boolean isSuccess() {
            return success;
        }

        public Object getData() {
            return data;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
