package com.cs.rag.pojo.entity;

import lombok.Data;

import java.util.Map;

/**
 * MCP 工具配置根模型
 * 对应 mcp-tools.json 的顶层结构
 *
 * @author caoshuai
 */
@Data
public class McpToolsConfig {

    /** MCP 服务器映射: key 为服务器名称, value 为服务器配置 */
    private Map<String, McpServerConfig> mcpServers;
}
