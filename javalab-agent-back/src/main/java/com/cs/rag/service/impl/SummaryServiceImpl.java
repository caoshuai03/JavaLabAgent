package com.cs.rag.service.impl;

import com.cs.rag.constant.RagConstant;
import com.cs.rag.pojo.entity.ChatMessage;
import com.cs.rag.service.ChatMessageService;
import com.cs.rag.service.LLMProviderService;
import com.cs.rag.service.PromptService;
import com.cs.rag.service.SummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SummaryServiceImpl implements SummaryService {

    private final LLMProviderService llmProviderService;
    private final PromptService promptService;
    private final ChatMessageService chatMessageService;

    public SummaryServiceImpl(LLMProviderService llmProviderService,
                              PromptService promptService,
                              ChatMessageService chatMessageService) {
        this.llmProviderService = llmProviderService;
        this.promptService = promptService;
        this.chatMessageService = chatMessageService;
    }

    @Override
    public String summarize(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        try {
            String chatHistory = buildChatHistory(messages);
            log.info("Summary input chat history:\n{}", chatHistory);
            return callLlmForSummary(chatHistory);
        } catch (Exception e) {
            log.error("Generate summary failed", e);
            return "";
        }
    }

    @Override
    public String refreshSummary(String oldSummary, List<ChatMessage> newMessages) {
        if (newMessages == null || newMessages.isEmpty()) {
            return oldSummary;
        }

        try {
            String newChatHistory = buildChatHistory(newMessages);
            String combinedInput = buildRefreshInput(oldSummary, newChatHistory);
            log.info("Refresh rolling summary, input length={}", combinedInput.length());
            return callLlmForSummary(combinedInput);
        } catch (Exception e) {
            log.error("Refresh summary failed", e);
            return oldSummary;
        }
    }

    // 
    private String buildChatHistory(List<ChatMessage> messages) {
        List<Message> aiMessages = chatMessageService.convertToAiMessages(messages);
        return aiMessages.stream()
                .map(msg -> msg.getMessageType().getValue() + ": " + msg.getContent())
                .collect(Collectors.joining("\n"));
    }

    // 
    private String buildRefreshInput(String oldSummary, String newChatHistory) {
        if (oldSummary != null && !oldSummary.isEmpty()) {
            return String.format("【已有上下文摘要】\n%s\n\n【新发生的对话】\n%s", oldSummary, newChatHistory);
        }
        return newChatHistory;
    }

    private String callLlmForSummary(String inputContent) {
        String systemPrompt = promptService.getSummarySystemPrompt();
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new IllegalStateException("Summary system prompt is empty: " + com.cs.rag.service.impl.PromptRegistry.SUMMARY_SYSTEM);
        }
        return callLlmForSummary(inputContent, RagConstant.DEFAULT_EXTERNAL_LLM, false, systemPrompt);
    }

    /**
     * 调用摘要模型，并在主模型失败时自动回退到 Ollama。
     * <p>
     * 这里优先保证摘要能力可用，而不是让额度耗尽直接中断业务。
     */
    private String callLlmForSummary(String inputContent, String modelName, boolean fallbackMode, String systemPrompt) {
        try {
            ChatModel chatModel = fallbackMode ? llmProviderService.getOllamaChatModel() : llmProviderService.getChatModel(RagConstant.DEFAULT_EXTERNAL_LLM);
            ChatClient chatClient = ChatClient.builder(chatModel).build();

            String summary = chatClient.prompt()
                    .system(systemPrompt)
                    .user(inputContent)
                    .options(ChatOptions.builder().model(modelName).build())
                    .call()
                    .content();

            log.info("Summary generation completed, output length={}, model={}", summary.length(), modelName);
            return summary;
        } catch (Exception e) {
            if (!fallbackMode) {
                // 主模型失败时回退到本地 Ollama，保证摘要继续可用。
                log.warn("Summary primary model failed, fallback to Ollama: model={}", modelName, e.getMessage());
                return callLlmForSummary(inputContent, llmProviderService.getOllamaModelName(), true, systemPrompt);
            }

            // 兜底模型也失败时，只记录日志并返回空字符串，避免影响主流程。
            log.error("Summary generation failed with fallback model: model={}", modelName, e.getMessage());
            return "";
        }
    }
}

