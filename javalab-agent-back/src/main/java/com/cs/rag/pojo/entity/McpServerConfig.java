package com.cs.rag.pojo.entity;

import com.cs.rag.constant.RagConstant;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * MCP 服务器配置模型
 * 对应 mcp-tools.json 中每个 mcpServers 的条目
 *
 * @author caoshuai
 */
@Data
public class McpServerConfig {

    /** 是否启用该MCP服务器 */
    private boolean enabled = true;

    /** 传输类型: stdio（子进程）或 http（远程HTTP） */
    private String type = RagConstant.MCP_TRANSPORT_STDIO;

    /** stdio 模式下的可执行命令（如 npx, python, node） */
    private String command;

    /** stdio 模式下的命令参数列表 */
    private List<String> args;

    /** 环境变量（stdio模式传递给子进程） */
    private Map<String, String> env;

    /** http 模式下的服务器URL */
    private String url;

    /** 该MCP服务器的描述信息 */
    private String description;
}
