package com.cs.rag.tools;

import com.cs.rag.mcp.McpClientManager;
import com.cs.rag.mcp.McpToolInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 轻量工具注册表。
 * 负责聚合内置工具与 MCP 工具，并输出给模型使用的工具清单。
 */
@Component
public class ToolRegistry {

    public static final String TOOL_KNOWLEDGE_SEARCH = "knowledge_search";
    public static final String TOOL_WEB_SEARCH = "web_search";
    public static final String TOOL_FILE_READ = "file_read";
    public static final String TOOL_DIR_LIST = "dir_list";
    public static final String TOOL_FILE_SEARCH = "file_search";
    public static final String TOOL_GREP_SEARCH = "grep_search";
    public static final String TOOL_WEB_READ = "web_read";

    private final McpClientManager mcpClientManager;

    public ToolRegistry(McpClientManager mcpClientManager) {
        this.mcpClientManager = mcpClientManager;
    }

    /**
     * 获取全部可见工具描述。
     */
    public List<ToolDescriptor> getAllTools() {
        List<ToolDescriptor> descriptors = new ArrayList<>();
        descriptors.addAll(builtinTools());
        descriptors.addAll(mcpTools());
        return deduplicate(descriptors);
    }

    /**
     * 根据工具名查找工具描述。
     */
    public ToolDescriptor findTool(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return null;
        }
        for (ToolDescriptor descriptor : getAllTools()) {
            if (descriptor.name().equals(toolName)) {
                return descriptor;
            }
        }
        return null;
    }

    /**
     * 输出给模型的简化工具 schema。
     */
    public List<Map<String, Object>> toolSchemas() {
        List<Map<String, Object>> schemas = new ArrayList<>();
        for (ToolDescriptor descriptor : getAllTools()) {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("name", descriptor.name());
            schema.put("description", descriptor.description());
            schema.put("source", descriptor.source());
            schema.put("readOnly", descriptor.readOnly());
            Map<String, Object> inputSchema = descriptor.inputSchema();
            Object properties = inputSchema.get("properties");
            if (properties instanceof Map<?, ?> propsMap) {
                schema.put("params", new ArrayList<>(propsMap.keySet()));
                schema.put("paramDesc", buildParamDesc(propsMap));
            } else {
                schema.put("params", List.of());
                schema.put("paramDesc", "No params");
            }
            schemas.add(schema);
        }
        return schemas;
    }

    private List<ToolDescriptor> builtinTools() {
        return List.of(
                new ToolDescriptor(TOOL_KNOWLEDGE_SEARCH, "Search knowledge base", "builtin", schemaOf(new LinkedHashMap<>() {{
                    put("query", property("string", "Search query"));
                }}), true),
                new ToolDescriptor(TOOL_WEB_SEARCH, "Search the web for relevant pages", "builtin", schemaOf(new LinkedHashMap<>() {{
                    put("query", property("string", "Search query"));
                    put("maxResults", property("integer", "Optional max result count"));
                }}), true),
                new ToolDescriptor(TOOL_FILE_READ, "Read a text file inside workspace", "builtin", schemaOf(new LinkedHashMap<>() {{
                    put("path", property("string", "Workspace-relative or absolute file path"));
                    put("startLine", property("integer", "Optional start line, default 1"));
                    put("endLine", property("integer", "Optional end line"));
                }}), true),
                new ToolDescriptor(TOOL_DIR_LIST, "List files or directories inside workspace", "builtin", schemaOf(new LinkedHashMap<>() {{
                    put("path", property("string", "Optional directory path, default workspace root"));
                    put("recursive", property("boolean", "Whether to recursively list subdirectories"));
                    put("maxDepth", property("integer", "Maximum traversal depth"));
                }}), true),
                new ToolDescriptor(TOOL_FILE_SEARCH, "Search files by pattern inside workspace", "builtin", schemaOf(new LinkedHashMap<>() {{
                    put("pattern", property("string", "Filename pattern, glob or keyword"));
                    put("baseDir", property("string", "Optional base directory"));
                    put("maxDepth", property("integer", "Maximum traversal depth"));
                }}), true),
                new ToolDescriptor(TOOL_GREP_SEARCH, "Search text in workspace files", "builtin", schemaOf(new LinkedHashMap<>() {{
                    put("query", property("string", "Text query to search"));
                    put("baseDir", property("string", "Optional base directory"));
                    put("includes", property("array", "Optional file glob filters"));
                    put("caseSensitive", property("boolean", "Whether the search is case-sensitive"));
                }}), true),
                new ToolDescriptor(TOOL_WEB_READ, "Read webpage content from an http or https URL", "builtin", schemaOf(new LinkedHashMap<>() {{
                    put("url", property("string", "Target webpage URL"));
                }}), true)
        );
    }

    private List<ToolDescriptor> mcpTools() {
        List<ToolDescriptor> descriptors = new ArrayList<>();
        for (McpToolInfo mcpTool : mcpClientManager.getAllTools()) {
            descriptors.add(new ToolDescriptor(
                    mcpTool.getName(),
                    mcpTool.getDescription(),
                    "mcp:" + mcpTool.getServerName(),
                    mcpTool.getInputSchema(),
                    true
            ));
        }
        return descriptors;
    }

    private List<ToolDescriptor> deduplicate(List<ToolDescriptor> descriptors) {
        List<ToolDescriptor> deduplicated = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ToolDescriptor descriptor : descriptors) {
            if (seen.add(descriptor.name())) {
                deduplicated.add(descriptor);
            }
        }
        return deduplicated;
    }

    private String buildParamDesc(Map<?, ?> propsMap) {
        StringBuilder builder = new StringBuilder();
        propsMap.forEach((key, value) -> {
            if (value instanceof Map<?, ?> propDef) {
                Object description = propDef.get("description");
                Object type = propDef.get("type");
                if (builder.length() > 0) {
                    builder.append("; ");
                }
                builder.append(key).append(": ").append(description != null ? description : type);
            }
        });
        return builder.length() == 0 ? "No params" : builder.toString();
    }

    private Map<String, Object> schemaOf(Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return schema;
    }

    private Map<String, Object> property(String type, String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", description);
        return property;
    }
}
