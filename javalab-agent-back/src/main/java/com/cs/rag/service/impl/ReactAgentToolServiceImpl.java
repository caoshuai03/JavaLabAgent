package com.cs.rag.service.impl;

import com.cs.rag.mcp.McpClientManager;
import com.cs.rag.mcp.McpToolInfo;
import com.cs.rag.pojo.entity.ChatMessage;
import com.cs.rag.service.ChatMessageService;
import com.cs.rag.service.ReactAgentToolService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ReactAgentToolServiceImpl implements ReactAgentToolService {

    private static final String TOOL_KNOWLEDGE_SEARCH = "knowledge_search";
    private static final String TOOL_SESSION_RECALL = "session_recall";

    private final RagConversationSupport ragConversationSupport;
    private final ChatMessageService chatMessageService;
    private final McpClientManager mcpClientManager;

    public ReactAgentToolServiceImpl(RagConversationSupport ragConversationSupport,
                                     ChatMessageService chatMessageService,
                                     McpClientManager mcpClientManager) {
        this.ragConversationSupport = ragConversationSupport;
        this.chatMessageService = chatMessageService;
        this.mcpClientManager = mcpClientManager;
    }

    @Override
    public ToolExecutionResult execute(String toolName, Map<String, Object> input, String sessionId, Long userId) {
        Map<String, Object> safeInput = input == null ? Map.of() : input;
        return switch (toolName) {
            case TOOL_KNOWLEDGE_SEARCH -> executeKnowledgeSearch(safeInput);
            case TOOL_SESSION_RECALL -> executeSessionRecall(safeInput, sessionId, userId);
            default -> executeExternalTool(toolName, safeInput);
        };
    }

    @Override
    public String getToolDescription(String toolName) {
        ToolDefinition builtinTool = getBuiltinToolDefinition(toolName);
        if (builtinTool != null) {
            return builtinTool.description();
        }
        McpToolInfo mcpTool = mcpClientManager.findTool(toolName);
        return mcpTool == null ? null : mcpTool.getDescription();
    }

    @Override
    public List<Map<String, Object>> toolSchemas() {
        List<Map<String, Object>> tools = new ArrayList<>();
        builtinToolDefinitions().stream().map(this::toolSchema).forEach(tools::add);

        for (McpToolInfo mcpTool : mcpClientManager.getAllTools()) {
            Map<String, Object> schema = new HashMap<>();
            schema.put("name", mcpTool.getName());
            schema.put("description", mcpTool.getDescription());
            schema.put("source", "mcp:" + mcpTool.getServerName());
            if (mcpTool.getInputSchema() != null && mcpTool.getInputSchema().containsKey("properties")) {
                Object props = mcpTool.getInputSchema().get("properties");
                if (props instanceof Map<?, ?> propsMap) {
                    schema.put("params", new ArrayList<>(propsMap.keySet()));
                    StringBuilder paramDesc = new StringBuilder();
                    propsMap.forEach((k, v) -> {
                        if (v instanceof Map<?, ?> propDef) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> properties = (Map<String, Object>) propDef;
                            String desc = properties.containsKey("description")
                                    ? String.valueOf(properties.get("description"))
                                    : String.valueOf(properties.getOrDefault("type", "any"));
                            paramDesc.append(k).append(": ").append(desc).append("; ");
                        }
                    });
                    schema.put("paramDesc", paramDesc.toString());
                }
            } else {
                schema.put("params", List.of());
                schema.put("paramDesc", "No params");
            }
            tools.add(schema);
        }
        return tools;
    }

    private ToolExecutionResult executeKnowledgeSearch(Map<String, Object> input) {
        String query = asString(input.get("query"));
        if (query == null || query.isBlank()) {
            return ToolExecutionResult.error(TOOL_KNOWLEDGE_SEARCH, "Missing query");
        }
        List<Document> documents = ragConversationSupport.performSearch(query);
        List<Map<String, Object>> hits = new ArrayList<>();
        if (documents != null) {
            for (Document doc : documents) {
                Map<String, Object> hit = new HashMap<>();
                hit.put("score", doc.getScore());
                String text = doc.getText();
                hit.put("content", text.length() > 400 ? text.substring(0, 400) : text);
                hits.add(hit);
                if (hits.size() >= 5) {
                    break;
                }
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("query", query);
        data.put("count", hits.size());
        data.put("hits", hits);
        return ToolExecutionResult.success(TOOL_KNOWLEDGE_SEARCH, data);
    }

    private ToolExecutionResult executeSessionRecall(Map<String, Object> input, String sessionId, Long userId) {
        int limit = asInt(input.get("limit"), 5);
        limit = Math.max(1, Math.min(limit, 20));
        List<ChatMessage> messages = chatMessageService.getRecentMessages(sessionId, userId, limit);
        Collections.reverse(messages);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ChatMessage msg : messages) {
            Map<String, Object> row = new HashMap<>();
            row.put("role", msg.getRole());
            String content = msg.getContent();
            row.put("content", content.length() > 200 ? content.substring(0, 200) : content);
            row.put("createdAt", msg.getCreatedAt());
            rows.add(row);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", sessionId);
        data.put("count", rows.size());
        data.put("messages", rows);
        return ToolExecutionResult.success(TOOL_SESSION_RECALL, data);
    }

    private ToolExecutionResult executeExternalTool(String toolName, Map<String, Object> input) {
        McpToolInfo mcpTool = mcpClientManager.findTool(toolName);
        if (mcpTool == null) {
            return ToolExecutionResult.error(toolName, "Unsupported tool");
        }
        return executeMcpTool(toolName, input);
    }

    private Map<String, Object> toolSchema(ToolDefinition toolDefinition) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", toolDefinition.name());
        data.put("description", toolDefinition.description());
        data.put("params", toolDefinition.params());
        data.put("paramDesc", toolDefinition.paramDesc());
        return data;
    }

    private List<ToolDefinition> builtinToolDefinitions() {
        return List.of(
                new ToolDefinition(TOOL_KNOWLEDGE_SEARCH, "Search knowledge base", List.of("query"), "query: search input"),
                new ToolDefinition(TOOL_SESSION_RECALL, "Recall recent session messages", List.of("limit"), "limit: default 5, max 20")
        );
    }

    private ToolDefinition getBuiltinToolDefinition(String toolName) {
        for (ToolDefinition tool : builtinToolDefinitions()) {
            if (tool.name().equals(toolName)) {
                return tool;
            }
        }
        return null;
    }

    private ToolExecutionResult executeMcpTool(String toolName, Map<String, Object> input) {
        try {
            McpClientManager.McpToolResult mcpResult = mcpClientManager.callTool(toolName, input);
            if (mcpResult.isSuccess()) {
                Map<String, Object> data = new HashMap<>();
                data.put("source", "mcp");
                data.put("result", mcpResult.getContent());
                return ToolExecutionResult.success(toolName, data);
            }
            return ToolExecutionResult.error(toolName, "MCP tool error: " + mcpResult.getErrorMessage());
        } catch (Exception e) {
            log.error("MCP tool invocation failed: toolName={}, error={}", toolName, e.getMessage(), e);
            return ToolExecutionResult.error(toolName, "MCP tool invocation failed: " + e.getMessage());
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int asInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private record ToolDefinition(String name, String description, List<String> params, String paramDesc) {
    }
}
