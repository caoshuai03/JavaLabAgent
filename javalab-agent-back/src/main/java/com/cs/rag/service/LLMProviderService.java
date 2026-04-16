package com.cs.rag.service;

import org.springframework.ai.chat.model.ChatModel;

/**
 * 大模型提供器服务。
 * 统一根据模型名称选择对应的 ChatModel。
 */
public interface LLMProviderService {

    /**
     * 根据模型名称获取可用的 ChatModel。
     */
    ChatModel getChatModel(String model);
}
