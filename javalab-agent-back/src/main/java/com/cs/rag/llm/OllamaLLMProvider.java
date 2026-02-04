package com.cs.rag.llm;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import static com.cs.rag.constant.RagConstant.OLLAMA_LLM;

@Component
public class OllamaLLMProvider implements LLMProvider {

    private final ChatModel chatModel;

    public OllamaLLMProvider(@Qualifier("ollamaChatModel") ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public boolean supports(String model) {
        if (model == null) return false;
        String lowerCase = model.toLowerCase();
        return OLLAMA_LLM.contains(lowerCase);
    }

    @Override
    public ChatModel getChatModel() {
        return chatModel;
    }
}
