package com.cs.rag.constant;

/**
 * RAG相关常量类
 * 
 * @author caoshuai
 * @since 1.0
 */
public class RagConstant {

    // ==================== 配置常量 ====================
    
    /** RAG相似度阈值 */
    public static final double SIMILARITY_THRESHOLD = 0.7;
    
    /** RAG检索返回的最大文档数量 */
    public static final int TOP_K = 5;
    
    /** 对话上下文滑动窗口大小 */
    public static final int MEMORY_SIZE = 10;
    
    // ==================== 格式常量 ====================
    
    public static final String SESSION_ID_PREFIX = "[SESSION_ID:";
    public static final String SESSION_ID_SUFFIX = "]";
    public static final String WEB_SOURCE_LABEL = "网络来源:\n";
    public static final String KNOWLEDGE_SOURCE_LABEL = "\n\n知识库来源:\n";

}
