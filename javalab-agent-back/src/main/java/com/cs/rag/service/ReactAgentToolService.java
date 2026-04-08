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
        private final String summary;
        private final String source;
        private final long costMs;
        private final Map<String, Object> metadata;

        private ToolExecutionResult(String toolName,
                                    boolean success,
                                    Object data,
                                    String errorMessage,
                                    String summary,
                                    String source,
                                    long costMs,
                                    Map<String, Object> metadata) {
            this.toolName = toolName;
            this.success = success;
            this.data = data;
            this.errorMessage = errorMessage;
            this.summary = summary;
            this.source = source;
            this.costMs = costMs;
            this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }

        public static ToolExecutionResult success(String toolName, Object data) {
            return success(toolName, data, null, "builtin", 0L, Map.of());
        }

        public static ToolExecutionResult error(String toolName, String errorMessage) {
            return error(toolName, errorMessage, "builtin", 0L, Map.of());
        }

        public static ToolExecutionResult success(String toolName,
                                                  Object data,
                                                  String summary,
                                                  String source,
                                                  long costMs,
                                                  Map<String, Object> metadata) {
            return new ToolExecutionResult(toolName, true, data, null, summary, source, costMs, metadata);
        }

        public static ToolExecutionResult error(String toolName,
                                                String errorMessage,
                                                String source,
                                                long costMs,
                                                Map<String, Object> metadata) {
            return new ToolExecutionResult(toolName, false, null, errorMessage, null, source, costMs, metadata);
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

        public String getSummary() {
            return summary;
        }

        public String getSource() {
            return source;
        }

        public long getCostMs() {
            return costMs;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }
    }
}
