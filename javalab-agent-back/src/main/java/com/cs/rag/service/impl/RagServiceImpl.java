package com.cs.rag.service.impl;

import com.cs.rag.pojo.entity.ChatMessage;
import com.cs.rag.pojo.entity.ChatSession;
import com.cs.rag.llm.LLMProviderRegistry;
import com.cs.rag.mapper.ChatSessionMapper;
import com.cs.rag.service.ChatMessageService;
import com.cs.rag.service.ChatSessionService;
import com.cs.rag.service.PromptService;
import com.cs.rag.service.RagService;
import com.cs.rag.service.SummaryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.cs.rag.constant.RagConstant.MEMORY_SIZE;

@Slf4j
@Service
public class RagServiceImpl implements RagService {

    private final VectorStore vectorStore;
    private final LLMProviderRegistry llmProviderRegistry;
    private final PromptService promptService;
    private final ChatSessionService chatSessionService;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageService chatMessageService;
    private final ObjectMapper objectMapper;
    private final SummaryService summaryService;
    private final RagConversationSupport ragConversationSupport;

    public RagServiceImpl(VectorStore vectorStore,
                          LLMProviderRegistry llmProviderRegistry,
                          PromptService promptService,
                          ChatSessionService chatSessionService,
                          ChatSessionMapper chatSessionMapper,
                          ChatMessageService chatMessageService,
                          ObjectMapper objectMapper,
                          SummaryService summaryService,
                          RagConversationSupport ragConversationSupport) {
        this.vectorStore = vectorStore;
        this.llmProviderRegistry = llmProviderRegistry;
        this.promptService = promptService;
        this.chatSessionService = chatSessionService;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageService = chatMessageService;
        this.objectMapper = objectMapper;
        this.summaryService = summaryService;
        this.ragConversationSupport = ragConversationSupport;
    }

    @Override
    public Flux<String> chat(String message, String sessionId, Long userId, String model) {
        // 1. 准备会话并读取历史上下文，保证本轮对话有完整背景。
        String finalSessionId = prepareSession(message, sessionId, userId);
        List<Message> contextMessages = buildContext(finalSessionId, userId);

        // 2. 用户消息先落库，便于问题追踪和异常恢复。
        chatMessageService.saveUserMessage(finalSessionId, userId, message);
        log.info("User message saved: sessionId={}, userId={}", finalSessionId, userId);

        // 3. 做知识检索，并拼出最终发给模型的用户消息。
        List<Document> ragDocuments = performSearch(message);
        String enhancedMessage = formatMessageWithDocs(message, ragDocuments);

        // 4. 检索结果出来后，再确定真正使用的模型。
        String effectiveModel = selectModel(model, ragDocuments);

        // 5. 组装完整消息列表并开始流式输出。
        List<Message> allMessages = new ArrayList<>(contextMessages);
        allMessages.add(new UserMessage(enhancedMessage));

        logChatHistory(allMessages);

        return streamResponse(allMessages, effectiveModel, finalSessionId, userId)
                .doOnComplete(() -> checkAndUpdateSummaryAsync(finalSessionId, userId));
    }

    // 摘要更新放到异步线程，避免阻塞主对话链路。
    private void checkAndUpdateSummaryAsync(String sessionId, Long userId) {
        CompletableFuture.runAsync(() -> {
            try {
                ChatSession session = chatSessionMapper.selectByIdAndUserId(sessionId, userId);
                if (session == null) {
                    return;
                }

                long totalMessages = ragConversationSupport.countMessagesBySession(sessionId);
                if (!shouldRefreshSummary(totalMessages)) {
                    return;
                }

                log.info("Trigger rolling summary update: sessionId={}, totalMessages={}", sessionId, totalMessages);
                int limit = calculateSummaryRefreshLimit(totalMessages);
                List<ChatMessage> recentMessages = chatMessageService.getRecentMessages(sessionId, userId, limit);
                Collections.reverse(recentMessages);

                String newSummary = summaryService.refreshSummary(session.getSummary(), recentMessages);
                chatSessionMapper.updateSummary(sessionId, newSummary);
                log.info("Rolling summary update completed: sessionId={}", sessionId);
            } catch (Exception e) {
                log.error("Rolling summary update failed: sessionId={}", sessionId, e);
            }
        });
    }

    // 保持原有触发规则不变，只是把意图表达得更清楚。
    private boolean shouldRefreshSummary(long totalMessages) {
        return totalMessages > 0 && totalMessages % MEMORY_SIZE <= 1;
    }

    // 保持原有容错策略，兼容消息总数奇偶偏移。
    private int calculateSummaryRefreshLimit(long totalMessages) {
        return MEMORY_SIZE + (int) (totalMessages % MEMORY_SIZE);
    }

    private String prepareSession(String message, String sessionId, Long userId) {
        return ragConversationSupport.prepareSession(message, sessionId, userId);
    }

    /**
     * 构建上下文：结合摘要和最近历史
     */
    private List<Message> buildContext(String sessionId, Long userId) {
        return ragConversationSupport.buildContext(sessionId, userId);
    }

    /**
     * 选择模型
     */
    private String selectModel(String model, List<Document> ragDocuments) {
        return ragConversationSupport.selectModel(model, ragDocuments);
    }

    // 这里保留完整上下文日志，方便线上排查具体输入链路。
    private void logChatHistory(List<Message> allMessages) {
        StringBuilder messagesLog = new StringBuilder();
        messagesLog.append("\n==================== Conversation Context START ====================\n");
        for (int i = 0; i < allMessages.size(); i++) {
            Message msg = allMessages.get(i);
            String content = msg.getContent();
            String role = msg.getMessageType().getValue();

            messagesLog.append(String.format("[%d] Role: %s\n", i, role));
            if (i == allMessages.size() - 1) {
                messagesLog.append("Content (User Input): ").append(content).append("\n");
            } else {
                String displayContent = content.length() > 100
                        ? content.substring(0, 100) + "...(length: " + content.length() + ")"
                        : content;
                messagesLog.append("Content: ").append(displayContent).append("\n");
            }
            messagesLog.append("--------------------------------------------------\n");
        }
        messagesLog.append("==================== Conversation Context END ====================\n");
        log.info(messagesLog.toString());
    }

    // 统一处理模型流式输出，并在结束后落库 assistant 消息。
    private Flux<String> streamResponse(List<Message> allMessages, String model, String sessionId, Long userId) {
        long llmStartTime = System.currentTimeMillis();

        ChatModel targetChatModel = llmProviderRegistry.getChatModel(model);
        ChatClient chatClient = ChatClient.builder(targetChatModel).build();
        ChatClient.ChatClientRequestSpec promptSpec = chatClient.prompt()
                .system(promptService.getRagAnswerSystemPrompt())
                .messages(allMessages)
                .options(ChatOptions.builder().model(model).build());

        StringBuilder fullResponse = new StringBuilder();

        return Flux.concat(
                Flux.just("{\"sessionId\":\"" + sessionId + "\"}"),
                promptSpec.stream()
                        .content()
                        .doOnNext(fullResponse::append)
                        .map(chunk -> {
                            try {
                                Map<String, String> data = new HashMap<>();
                                data.put("content", chunk);
                                return objectMapper.writeValueAsString(data);
                            } catch (Exception e) {
                                return "{\"content\":\"\"}";
                            }
                        })
                        .doOnComplete(() -> {
                            String aiResponse = fullResponse.toString();
                            if (!aiResponse.isEmpty()) {
                                chatMessageService.saveAssistantMessage(sessionId, userId, aiResponse);
                                log.info("LLM stream completed: sessionId={}, length={}, cost={}ms",
                                        sessionId, aiResponse.length(), System.currentTimeMillis() - llmStartTime);
                            }
                        })
                        .doOnError(e -> log.error("LLM stream failed: sessionId={}, error={}", sessionId, e.getMessage()))
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
}
