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
    public static final double SIMILARITY_THRESHOLD = 0.7;

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
    public static final String DEFAULT_EXTERNAL_LLM = "ernie-4.5-turbo-128k-preview";

    /**
     * 本地大模型
     */
    public static final List<String> OLLAMA_LLM = List.of("qwen3:8b");

    public static final List<String> OPENAI_LLM = List.of("ernie-4.5-turbo-128k-preview", "deepseek-v3", "deepseek-r1", "qwen3-235b-a22b", "llama-2-70b");


    // ==================== 格式常量 ====================

    public static final String SESSION_ID_PREFIX = "[SESSION_ID:";
    public static final String SESSION_ID_SUFFIX = "]";
    public static final String WEB_SOURCE_LABEL = "网络来源:\n";
    public static final String KNOWLEDGE_SOURCE_LABEL = "\n\n知识库来源:\n";

    /**
     * 无知识库命中时的提示标签
     */
    public static final String NO_KNOWLEDGE_FOUND_LABEL = "\n\n[系统提示: 知识库中未检索到相关内容，请基于通用知识回答]\n";

    /**
     * 最大轮次
     */
    public static final int MAX_ROUNDS = 5;
}
