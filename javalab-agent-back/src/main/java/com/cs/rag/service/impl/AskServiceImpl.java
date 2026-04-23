package com.cs.rag.service.impl;



import com.cs.rag.pojo.entity.ChatMessage;

import com.cs.rag.pojo.entity.ChatSession;

import com.cs.rag.service.AskService;

import com.cs.rag.service.ChatMessageService;

import com.cs.rag.service.ChatSessionService;

import com.cs.rag.service.LLMProviderService;

import com.cs.rag.service.PromptService;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.chat.client.ChatClient;

import org.springframework.ai.chat.messages.Message;

import org.springframework.ai.chat.messages.SystemMessage;

import org.springframework.ai.chat.messages.UserMessage;

import org.springframework.ai.chat.model.ChatModel;

import org.springframework.ai.chat.prompt.ChatOptions;

import org.springframework.ai.document.Document;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;



import java.time.Instant;

import java.util.ArrayList;

import java.util.LinkedHashMap;

import java.util.List;

import java.util.Map;

import java.util.UUID;



@Slf4j

@Service

public class AskServiceImpl implements AskService {



    private final LLMProviderService llmProviderService;

    private final PromptService promptService;

    private final ChatSessionService chatSessionService;

    private final ChatMessageService chatMessageService;

    private final ObjectMapper objectMapper;

    private final RagConversationSupport ragConversationSupport;



    public AskServiceImpl(LLMProviderService llmProviderService,

                          PromptService promptService,

                          ChatSessionService chatSessionService,

                          ChatMessageService chatMessageService,

                          ObjectMapper objectMapper,

                          RagConversationSupport ragConversationSupport) {

        this.llmProviderService = llmProviderService;

        this.promptService = promptService;

        this.chatSessionService = chatSessionService;

        this.chatMessageService = chatMessageService;

        this.objectMapper = objectMapper;

        this.ragConversationSupport = ragConversationSupport;

    }



    @Override

    public Flux<String> chat(String message, String sessionId, Long userId, String model) {

        RagChatContext chatContext = prepareChatContext(message, sessionId, userId, model);

        String traceId = UUID.randomUUID().toString().replace("-", "");

        long start = System.currentTimeMillis();



        log.info("RAG chat started: sessionId={}, traceId={}, model={}, originalMessageLength={}, enhancedMessageLength={}, ragDocCount={}",

                chatContext.sessionId(),

                traceId,

                chatContext.effectiveModel(),

                chatContext.originalMessage().length(),

                chatContext.enhancedMessage().length(),

                chatContext.ragDocuments().size());

        logContextMessages("RAG context", chatContext.allMessages());



        return streamResponse(chatContext, traceId, start);

    }



    private RagChatContext prepareChatContext(String message, String sessionId, Long userId, String model) {

        String finalSessionId = ragConversationSupport.prepareSession(message, sessionId, userId);

        List<Message> contextMessages = ragConversationSupport.buildContext(finalSessionId, userId);



        chatMessageService.saveUserMessage(finalSessionId, userId, message);

        log.info("RAG user message saved: sessionId={}, userId={}", finalSessionId, userId);



        List<Document> ragDocuments = ragConversationSupport.performSearch(message);

        String enhancedMessage = ragConversationSupport.formatMessageWithDocs(message, ragDocuments);

        String effectiveModel = ragConversationSupport.selectModel(model, ragDocuments);



        List<Message> allMessages = new ArrayList<>(contextMessages);

        allMessages.add(new UserMessage(enhancedMessage));



        return new RagChatContext(finalSessionId, userId, message, enhancedMessage, effectiveModel, ragDocuments, allMessages);

    }



    private Flux<String> streamResponse(RagChatContext chatContext, String traceId, long start) {
        String finalModelName = chatContext.effectiveModel();
        String fallbackModelName = llmProviderService.getOllamaModelName();
        String fallbackReason = "外部模型失败，自动切换到本地 Ollama 模型继续回答。";

        // 先构建主模型流；若在构建或订阅阶段失败，再切换到本地稳定模型。
        return buildStreamingResponse(chatContext, traceId, start, finalModelName, false)
                .onErrorResume(primaryError -> {
                    log.warn("RAG primary model failed, fallback to Ollama: sessionId={}, traceId={}, model={}, fallbackModel={}",
                            chatContext.sessionId(), traceId, finalModelName, fallbackModelName, primaryError);
                    return Flux.concat(
                            Flux.just(eventJson("status", chatContext.sessionId(), traceId, Map.of(
                                    "stage", "fallback_to_ollama",
                                    "model", fallbackModelName,
                                    "reason", fallbackReason
                            ))),
                            buildStreamingResponse(chatContext, traceId, start, fallbackModelName, true)
                    );
                });

    }

    /**
     * 构建一次完整的流式回答 Flux。
     * <p>
     * 当 `fallbackMode` 为 true 时，使用 Ollama 模型名和本地模型 bean。
     */
    private Flux<String> buildStreamingResponse(RagChatContext chatContext,
                                                String traceId,
                                                long start,
                                                String modelName,
                                                boolean fallbackMode) {
        ChatModel targetChatModel = fallbackMode ? llmProviderService.getOllamaChatModel() : llmProviderService.getChatModel(chatContext.effectiveModel());
        ChatClient chatClient = ChatClient.builder(targetChatModel).build();
        ChatClient.ChatClientRequestSpec promptSpec = chatClient.prompt()
                .messages(buildFinalMessages(chatContext.allMessages()))
                .options(ChatOptions.builder().model(modelName).build());

        StringBuilder fullResponse = new StringBuilder();

        return Flux.concat(
                Flux.just(eventJson("session", chatContext.sessionId(), traceId, Map.of("sessionId", chatContext.sessionId()))),
                Flux.just(eventJson("status", chatContext.sessionId(), traceId, Map.of(
                        "stage", fallbackMode ? "fallback_answer_streaming" : "retrieval_completed",
                        "ragDocCount", chatContext.ragDocuments().size(),
                        "model", modelName
                ))),
                Flux.just(eventJson("status", chatContext.sessionId(), traceId, Map.of(
                        "stage", "answer_streaming"
                ))),
                promptSpec.stream()
                        .content()
                        .doOnNext(fullResponse::append)
                        .map(chunk -> eventJson("token", chatContext.sessionId(), traceId, Map.of("content", chunk)))
                        .doOnComplete(() -> {
                            String aiResponse = fullResponse.toString();
                            if (!aiResponse.isEmpty()) {
                                chatMessageService.saveAssistantMessage(chatContext.sessionId(), chatContext.userId(), aiResponse);
                                ragConversationSupport.refreshSummaryAsync(chatContext.sessionId(), chatContext.userId(), "RAG");
                                log.info("RAG chat completed: sessionId={}, traceId={}, model={}, answerLength={}, cost={}ms",
                                        chatContext.sessionId(), traceId, modelName, aiResponse.length(), System.currentTimeMillis() - start);
                            }
                        })
                        .concatWithValues(eventJson("final", chatContext.sessionId(), traceId, Map.of("done", true)))
                        .onErrorResume(e -> {
                            // 主模型失败时抛出错误给上层触发 fallback；兜底模型才返回 error 事件结束。
                            log.error("RAG stream failed: sessionId={}, traceId={}, model={}, fallbackMode={}",
                                    chatContext.sessionId(), traceId, modelName, fallbackMode, e);
                            if (fallbackMode) {
                                return Flux.just(eventJson("error", chatContext.sessionId(), traceId, Map.of(
                                        "message", e.getMessage() != null ? e.getMessage() : "Unknown error"
                                )));
                            }
                            return Flux.error(e);
                        })
        );
    }



    @Override

    public List<ChatMessage> getHistory(String sessionId, Long userId) {

        return chatMessageService.getMessagesBySessionId(sessionId, userId);

    }



    @Override

    public List<ChatSession> listSessions(Long userId) {

        return chatSessionService.getSessionsByUserId(userId);

    }



    @Override

    public boolean delete(String sessionId, Long userId) {

        log.info("Delete session logically: sessionId={}, userId={}", sessionId, userId);

        return chatSessionService.deleteSession(sessionId, userId);

    }



    @Override

    public boolean deleteBatch(List<String> sessionIds, Long userId) {

        log.info("Delete sessions logically in batch: sessionIds={}, userId={}", sessionIds, userId);

        return chatSessionService.deleteSessions(sessionIds, userId);

    }



    @Override

    public String enhance(String message) {

        List<Document> ragDocuments = performSearch(message);

        return formatMessageWithDocs(message, ragDocuments);

    }



    private List<Document> performSearch(String message) {

        return ragConversationSupport.performSearch(message);

    }



    private String formatMessageWithDocs(String message, List<Document> ragDocuments) {

        return ragConversationSupport.formatMessageWithDocs(message, ragDocuments);

    }



    private List<Message> buildFinalMessages(List<Message> allMessages) {

        List<Message> messages = new ArrayList<>();

        messages.add(new SystemMessage(promptService.getRagAnswerSystemPrompt()));

        messages.addAll(allMessages);

        return messages;

    }



    private void logContextMessages(String label, List<Message> messages) {

        List<String> recentSystemMessages = collectRecentMessages(messages, SystemMessage.class, 2);

        List<String> recentUserMessages = collectRecentMessages(messages, UserMessage.class, 3);

        log.info("{}: totalMessages={}, recentSystemMessages={}, recentUserMessages={}",

                label, messages.size(), recentSystemMessages.size(), recentUserMessages.size());

        for (int i = 0; i < recentSystemMessages.size(); i++) {

            log.info("[Context][System {}] {}", i + 1, recentSystemMessages.get(i));

        }

        for (int i = 0; i < recentUserMessages.size(); i++) {

            log.info("[Context][User {}] {}", i + 1, recentUserMessages.get(i));

        }

    }



    private List<String> collectRecentMessages(List<Message> messages, Class<? extends Message> targetType, int limit) {

        List<String> collected = new ArrayList<>();

        for (int i = messages.size() - 1; i >= 0 && collected.size() < limit; i--) {

            Message message = messages.get(i);

            if (targetType.isInstance(message)) {

                collected.add(0, abbreviateLogContent(message.getContent()));

            }

        }

        return collected;

    }



    private String abbreviateLogContent(String content) {

        if (content == null || content.isBlank()) {

            return "";

        }

        String normalized = content.replaceAll("\\s+", " ").trim();

        int maxLength = 400;

        if (normalized.length() <= maxLength) {

            return normalized;

        }

        return normalized.substring(0, maxLength) + "...";

    }



    private String eventJson(String eventType, String sessionId, String traceId, Map<String, Object> payload) {

        Map<String, Object> data = new LinkedHashMap<>();

        data.put("eventType", eventType);

        data.put("sessionId", sessionId);

        data.put("traceId", traceId);

        data.put("ts", Instant.now().toEpochMilli());

        data.put("payload", payload);

        return toJsonQuietly(data);

    }



    private String toJsonQuietly(Object obj) {

        try {

            return objectMapper.writeValueAsString(obj);

        } catch (Exception e) {

            return "{\"eventType\":\"error\",\"payload\":{\"message\":\"json serialization failed\"}}";

        }

    }



    private record RagChatContext(String sessionId,

                                  Long userId,

                                  String originalMessage,

                                  String enhancedMessage,

                                  String effectiveModel,

                                  List<Document> ragDocuments,

                                  List<Message> allMessages) {

    }

}



