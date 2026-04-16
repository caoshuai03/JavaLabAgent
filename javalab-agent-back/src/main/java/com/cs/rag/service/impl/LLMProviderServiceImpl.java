package com.cs.rag.service.impl;

import com.cs.rag.service.LLMProviderService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import static com.cs.rag.constant.RagConstant.OPENAI_LLM;

/**
 * 大模型提供器服务实现。
 * 统一根据模型名称挑选可用的 ChatModel。
 */
@Service
public class LLMProviderServiceImpl implements LLMProviderService {

    private final ChatModel openAiChatModel;
    private final ChatModel ollamaChatModel;

    public LLMProviderServiceImpl(@Qualifier("openAiChatModel") ChatModel openAiChatModel,
                                  @Qualifier("ollamaChatModel") ChatModel ollamaChatModel) {
        this.openAiChatModel = openAiChatModel;
        this.ollamaChatModel = ollamaChatModel;
    }

    @Override
    public ChatModel getChatModel(String model) {
        if (model != null && !model.isEmpty()) {
            String lowerCase = model.toLowerCase();
            // 优先匹配模型名称
            if (OPENAI_LLM.contains(lowerCase)) {
                return openAiChatModel;
            }
            if (lowerCase.contains("ollama") || lowerCase.contains("qwen") || lowerCase.contains("llama")) {
                return ollamaChatModel;
            }
        }

        // 默认返回 OpenAI 模型
        return openAiChatModel;
    }
}

