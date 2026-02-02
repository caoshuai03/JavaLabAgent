package com.cs.rag.llm;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OllamaLLMProvider implements LLMProvider {

    private final ChatModel chatModel;

    public OllamaLLMProvider(@Qualifier("ollamaChatModel") ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public boolean supports(String model) {
        List<String> supportedModels = List.of("qwen3:8b");
        if (model == null) return false;
        String lowerCase = model.toLowerCase();
        return supportedModels.contains(lowerCase);
    }

    @Override
    public ChatModel getChatModel() {
        return chatModel;
    }
}
