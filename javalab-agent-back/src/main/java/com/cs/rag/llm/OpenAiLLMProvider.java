package com.cs.rag.llm;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.cs.rag.constant.RagConstant.OPENAI_LLM;

@Component
public class OpenAiLLMProvider implements LLMProvider {

    private final ChatModel chatModel;

    public OpenAiLLMProvider(@Qualifier("openAiChatModel") ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public boolean supports(String model) {
        if (model == null) return false;
        String lowerCase = model.toLowerCase();
        return OPENAI_LLM.contains(lowerCase);
    }

    @Override
    public ChatModel getChatModel() {
        return chatModel;
    }
}
