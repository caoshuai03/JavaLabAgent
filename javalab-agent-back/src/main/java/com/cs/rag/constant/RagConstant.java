package com.cs.rag.constant;

import java.util.List;

/**
 * RAG相关常量类
 *
 * @author caoshuai
 * @since 1.0
 */
public class RagConstant {

    // ==================== 配置常量 ====================

    /**
     * RAG相似度阈值
     */
    public static final double SIMILARITY_THRESHOLD = 0.71;

    /**
     * RAG检索返回的最大文档数量
     */
    public static final int TOP_K = 10;

    /**
     * 对话上下文滑动窗口大小
     */
    public static final int MEMORY_SIZE = 10;

    /**
     * 默认外部大模型
     */
    public static final String DEFAULT_EXTERNAL_LLM = "qwen3:8b";

    /**
     * 本地大模型
     */
    public static final List<String> OLLAMA_LLM = List.of("qwen3:8b");

    public static final List<String> OPENAI_LLM = List.of("ernie-4.5-turbo-128k-preview", "deepseek-v3", "deepseek-r1", "qwen3-235b-a22b", "llama-2-70b");

    /**
     * 最大轮次
     */
    public static final int MAX_ROUNDS = 10;

    // ==================== MCP 配置常量 ====================

    /**
     * MCP 配置文件名（classpath 及外部目录均使用此名称）
     */
    public static final String MCP_CONFIG_FILE = "mcp-tools.json";

    /**
     * MCP 传输类型: stdio（子进程模式）
     */
    public static final String MCP_TRANSPORT_STDIO = "stdio";

    /**
     * MCP 传输类型: http（远程HTTP模式）
     */
    public static final String MCP_TRANSPORT_HTTP = "http";

    /**
     * MCP 传输类型: sse（Server-Sent Events模式）
     */
    public static final String MCP_TRANSPORT_SSE = "sse";
}
