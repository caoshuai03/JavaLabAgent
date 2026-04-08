package com.cs.rag.tools;

import java.util.Collections;
import java.util.Map;

/**
 * 轻量工具描述对象。
 * 统一承接内置工具与 MCP 工具的最小公共信息。
 */
public record ToolDescriptor(String name,
                             String description,
                             String source,
                             Map<String, Object> inputSchema,
                             boolean readOnly) {

    public ToolDescriptor {
        inputSchema = inputSchema == null ? Collections.emptyMap() : Collections.unmodifiableMap(inputSchema);
    }
}
