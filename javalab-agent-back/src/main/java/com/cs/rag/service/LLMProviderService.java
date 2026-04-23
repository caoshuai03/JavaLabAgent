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

    /**
     * 获取稳定的本地兜底模型。
     * <p>
     * 当百度/外部模型额度耗尽或请求失败时，调用方可以切换到该模型继续服务。
     */
    ChatModel getOllamaChatModel();

    /**
     * 获取 Ollama 兜底模型的名称。
     * <p>
     * 用于设置 ChatOptions，确保回退后的模型配置与本地提供方一致。
     */
    String getOllamaModelName();
}
