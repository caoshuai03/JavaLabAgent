package com.cs.rag.llm;

import org.springframework.ai.chat.model.ChatModel;

/**
 * Large Language Model Provider Interface
 * Defines how to retrieve a ChatModel based on a model name.
 */
public interface LLMProvider {
    
    /**
     * Checks if the provider supports the given model name.
     * @param model The name of the model (e.g., "ernie-4.0-8k", "qwen3:8b")
     * @return true if supported, false otherwise
     */
    boolean supports(String model);

    /**
     * Returns the ChatModel instance for this provider.
     * @return ChatModel instance
     */
    ChatModel getChatModel();
}
