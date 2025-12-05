package com.cs.rag.service;

import com.cs.rag.constant.RagConstant;
import com.cs.rag.entity.ChatMessage;
import com.cs.rag.entity.ChatSession;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;

/**
 * RAG服务接口
 * 负责处理RAG对话相关的核心业务逻辑
 * 
 * <p>主要功能:</p>
 * <ul>
 *   <li>RAG检索增强</li>
 *   <li>网络搜索增强</li>
 *   <li>会话管理与持久化</li>
 *   <li>流式对话生成</li>
 * </ul>
 * 
 * @author caoshuai
 * @since 1.0
 */
public interface RagService {
    
    // 常量定义已移至 RagConstant 类
    double SIMILARITY_THRESHOLD = RagConstant.SIMILARITY_THRESHOLD;
    int TOP_K = RagConstant.TOP_K;
    int MEMORY_SIZE = RagConstant.MEMORY_SIZE;
    
    /**
     * 基础RAG对话接口（无持久化）
     * 
     * <p>业务流程:</p>
     * <ol>
     *   <li>网络搜索增强（可选）</li>
     *   <li>RAG向量检索</li>
     *   <li>流式调用LLM生成回复</li>
     * </ol>
     * 
     * @param message 用户消息
     * @param conversationId 会话ID，用于内存中的对话记忆
     * @param enableWebSearch 是否启用网络搜索
     * @return 流式响应内容
     * @throws IOException 网络搜索失败时抛出
     */
    Flux<String> generateResponse(String message, String conversationId, Boolean enableWebSearch) throws IOException;
    
    /**
     * 持久化RAG对话接口
     * 
     * <p>业务流程:</p>
     * <ol>
     *   <li>创建/获取会话</li>
     *   <li>保存用户消息到数据库</li>
     *   <li>构建滑动窗口上下文</li>
     *   <li>网络搜索增强（可选）</li>
     *   <li>RAG向量检索增强</li>
     *   <li>流式调用LLM并保存回复</li>
     * </ol>
     * 
     * @param message 用户消息
     * @param sessionId 会话ID，为空时创建新会话
     * @param userId 用户ID
     * @param enableWebSearch 是否启用网络搜索
     * @return 流式响应，第一条消息包含sessionId
     * @throws IOException 网络搜索失败时抛出
     */
    Flux<String> generateWithPersistence(String message, String sessionId, Long userId, Boolean enableWebSearch) throws IOException;
    
    /**
     * 获取会话的历史消息列表
     * 
     * @param sessionId 会话ID
     * @return 消息列表，按时间正序排列
     */
    List<ChatMessage> getSessionHistory(String sessionId);
    
    /**
     * 获取用户的所有会话列表
     * 
     * @param userId 用户ID
     * @return 会话列表，按更新时间倒序排列
     */
    List<ChatSession> getUserSessions(Long userId);
    
    /**
     * 执行网络搜索增强
     * 
     * @param message 原始消息
     * @return 增强后的消息（附加搜索结果）
     * @throws IOException 搜索失败时抛出
     */
    String enhanceWithWebSearch(String message) throws IOException;
    
    /**
     * 执行RAG向量检索增强
     * 
     * @param message 原始消息
     * @return 增强后的消息（附加知识库内容）
     */
    String enhanceWithRag(String message);
    
    /**
     * 删除会话（逻辑删除）
     * 
     * @param sessionId 会话ID
     * @return 是否删除成功
     */
    boolean deleteSession(String sessionId);
}
