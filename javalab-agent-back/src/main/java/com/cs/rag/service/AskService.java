package com.cs.rag.service;

import com.cs.rag.pojo.entity.ChatMessage;
import com.cs.rag.pojo.entity.ChatSession;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 问答服务接口。
 * 统一负责 RAG 问答、会话历史和会话管理能力。
 */
public interface AskService {

    /**
     * 执行持久化 RAG 对话。
     */
    Flux<String> chat(String message, String sessionId, Long userId, String model);

    /**
     * 获取指定会话的历史消息。
     */
    List<ChatMessage> getHistory(String sessionId, Long userId);

    /**
     * 获取用户的会话列表。
     */
    List<ChatSession> listSessions(Long userId);

    /**
     * 执行 RAG 增强，返回拼接知识后的消息。
     */
    String enhance(String message);

    /**
     * 删除单个会话。
     */
    boolean delete(String sessionId, Long userId);

    /**
     * 批量删除会话。
     */
    boolean deleteBatch(List<String> sessionIds, Long userId);
}

