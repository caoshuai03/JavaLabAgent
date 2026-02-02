package com.cs.rag.llm;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LLMProviderRegistry {

    private final List<LLMProvider> providers;

    public LLMProviderRegistry(List<LLMProvider> providers) {
        this.providers = providers;
    }

    public ChatModel getChatModel(String model) {
        // If model is provided, try to find a matching provider
        if (model != null && !model.isEmpty()) {
            for (LLMProvider provider : providers) {
                if (provider.supports(model)) {
                    return provider.getChatModel();
                }
            }
        }
        
        // Fallback: return OpenAiLLMProvider as default if available, otherwise the first one
        return providers.stream()
                .filter(p -> p instanceof OpenAiLLMProvider)
                .findFirst()
                .map(LLMProvider::getChatModel)
                .orElse(providers.isEmpty() ? null : providers.get(0).getChatModel());
    }
}
