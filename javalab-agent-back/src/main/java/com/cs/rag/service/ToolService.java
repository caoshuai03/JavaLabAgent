package com.cs.rag.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface ToolService {

    String TOOL_KNOWLEDGE_SEARCH = "knowledge_search";
    String TOOL_WEB_SEARCH = "web_search";
    String TOOL_FILE_READ = "file_read";
    String TOOL_FILE_WRITE = "file_write";
    String TOOL_FILE_SEARCH = "file_search";
    String TOOL_GREP_SEARCH = "grep_search";
    String TOOL_TERMINAL_EXEC = "terminal_exec";
    String TOOL_WEB_READ = "web_read";

    ToolExecutionResult execute(String toolName, Map<String, Object> input, String sessionId, Long userId);

    String getToolDescription(String toolName);

    List<Map<String, Object>> toolSchemas();

    /**
     * 宸ュ叿鎻忚堪瀵硅薄銆?     * 缁熶竴鎻忚堪鍐呯疆宸ュ叿涓?MCP 宸ュ叿鐨勬渶灏忓叕鍏变俊鎭€?     */
    record ToolDescriptor(String name,
                          String description,
                          String source,
                          Map<String, Object> inputSchema,
                          boolean readOnly) {

        public ToolDescriptor {
            inputSchema = inputSchema == null
                    ? Collections.emptyMap()
                    : Collections.unmodifiableMap(inputSchema);
        }
    }

    /**
     * 宸ュ叿绛栫暐鍒ゆ柇缁撴灉銆?     * 鍦ㄦ墽琛屽墠杩斿洖鏄惁鏀捐锛屼互鍙婂綊涓€鍖栧悗鐨勫弬鏁般€?     */
    record ToolPolicyDecision(boolean allowed,
                              String message,
                              Map<String, Object> normalizedInput) {

        public ToolPolicyDecision {
            normalizedInput = normalizedInput == null
                    ? Collections.emptyMap()
                    : Collections.unmodifiableMap(normalizedInput);
        }

        public static ToolPolicyDecision allow(Map<String, Object> normalizedInput) {
            return new ToolPolicyDecision(true, null, normalizedInput);
        }

        public static ToolPolicyDecision deny(String message) {
            return new ToolPolicyDecision(false, message, Collections.emptyMap());
        }
    }

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

