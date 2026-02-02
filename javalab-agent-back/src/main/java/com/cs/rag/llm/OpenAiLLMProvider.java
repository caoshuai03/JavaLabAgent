package com.cs.rag.llm;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OpenAiLLMProvider implements LLMProvider {

    private final ChatModel chatModel;

    public OpenAiLLMProvider(@Qualifier("openAiChatModel") ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public boolean supports(String model) {
        List<String> supportedModels = List.of("ernie-4.5-turbo-128k-preview", "deepseek-v3", "deepseek-r1", "qwen3-235b-a22b", "llama-2-70b");
        if (model == null) return false;
        String lowerCase = model.toLowerCase();
        return supportedModels.contains(lowerCase);
    }

    @Override
    public ChatModel getChatModel() {
        return chatModel;
    }
}
