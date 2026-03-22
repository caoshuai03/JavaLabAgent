package com.cs.rag.mcp;

import lombok.Data;

import java.util.Map;

/**
 * MCP 工具信息
 * 从 MCP 服务器的 tools/list 响应中解析出的单个工具定义
 *
 * @author caoshuai
 */
@Data
public class McpToolInfo {

    /** 工具名称 */
    private String name;

    /** 工具描述 */
    private String description;

    /** 工具输入参数的 JSON Schema */
    private Map<String, Object> inputSchema;

    /** 该工具所属的MCP服务器名称 */
    private String serverName;
}
