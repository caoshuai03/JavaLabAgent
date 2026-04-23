package com.cs.rag.service.impl;

import com.cs.rag.service.LLMProviderService;
import com.cs.rag.constant.RagConstant;
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

    @Override
    public ChatModel getOllamaChatModel() {
        // 本地 Ollama 作为稳定兜底模型，主模型失败时统一回退到这里。
        return ollamaChatModel;
    }

    @Override
    public String getOllamaModelName() {
        // 兜底时使用本地默认模型名，确保 ChatOptions 与 Ollama 提供方一致。
        return RagConstant.OLLAMA_LLM.isEmpty() ? "ollama" : RagConstant.OLLAMA_LLM.get(0);
    }
}

