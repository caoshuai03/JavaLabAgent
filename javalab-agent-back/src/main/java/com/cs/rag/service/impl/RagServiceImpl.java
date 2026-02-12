package com.cs.rag.service.impl;

import com.cs.rag.constant.RagConstant;
import com.cs.rag.entity.ChatMessage;
import com.cs.rag.entity.ChatSession;
import com.cs.rag.llm.LLMProviderRegistry;
import com.cs.rag.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

import static com.cs.rag.constant.RagConstant.*;


import java.util.concurrent.CompletableFuture;

import com.cs.rag.mapper.ChatSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * RAG服务实现类
 * 实现RAG对话相关的核心业务逻辑
 *
 * <p>该类负责:</p>
 * <ul>
 *   <li>RAG向量检索增强</li>
 *   <li>会话管理与消息持久化</li>
 *   <li>LLM流式对话生成</li>
 * </ul>
 *
 * @author caoshuai
 * @since 1.0
 */
@Slf4j
@Service
public class RagServiceImpl implements RagService {

    // ==================== 依赖注入 ====================

    /**
     * 向量存储，用于RAG检索
     */
    private final VectorStore vectorStore;

    /**
     * LLM Provider Registry
     */
    private final LLMProviderRegistry llmProviderRegistry;

    /**
     * 提示词服务
     */
    @Autowired
    private PromptService promptService;

    /**
     * 会话服务
     */
    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    /**
     * 消息服务
     */
    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 摘要生成服务
     */
    @Autowired
    private SummaryService summaryService;

    /**
     * 构造函数注入核心依赖
     *
     * @param vectorStore         向量存储
     * @param llmProviderRegistry LLM Provider Registry
     */
    public RagServiceImpl(VectorStore vectorStore,
                          LLMProviderRegistry llmProviderRegistry) {
        this.vectorStore = vectorStore;
        this.llmProviderRegistry = llmProviderRegistry;
    }

    // ==================== 核心业务方法 ====================

    /**
     * 持久化RAG对话
     * 将会话和消息存储到数据库，支持跨请求的上下文管理
     */
    @Override
    public Flux<String> chat(String message, String sessionId, Long userId, String model) {
        // 1. 准备会话
        String finalSessionId = prepareSession(message, sessionId, userId);

        // 2. 构建上下文 (摘要 + 最近历史)
        List<Message> contextMessages = buildContext(finalSessionId, userId);

        // 3. 保存当前用户消息
        chatMessageService.saveUserMessage(finalSessionId, userId, message);
        log.info("已保存用户消息: sessionId={}, userId={}", finalSessionId, userId);

        // 4. RAG检索
        List<Document> ragDocuments = performSearch(message);
        String enhancedMessage = formatMessageWithDocs(message, ragDocuments);

        // 5. 选择模型
        String effectiveModel = selectModel(model, ragDocuments);

        // 6. 构建完整消息列表
        List<Message> allMessages = new ArrayList<>(contextMessages);
        allMessages.add(new UserMessage(enhancedMessage));

        // 7. 记录对话日志
        logChatHistory(allMessages);

        // 8. 执行流式对话
        return streamResponse(allMessages, effectiveModel, finalSessionId, userId)
                .doOnComplete(() -> {
                    // 9. 对话完成后，异步检查是否需要更新摘要 (滚动摘要)
                    checkAndUpdateSummaryAsync(finalSessionId, userId);
                });
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 异步检查并更新摘要
     * 策略：每积攒 10 条新消息，触发一次滚动更新
     */
    private void checkAndUpdateSummaryAsync(String sessionId, Long userId) {
        CompletableFuture.runAsync(() -> {
            try {
                // 1. 获取会话当前信息
                ChatSession session = chatSessionMapper.selectByIdAndUserId(sessionId, userId);
                if (session == null) return;

                // 2. 获取所有消息数量

                long totalMessages = chatMessageService.count(new LambdaQueryWrapper<ChatMessage>()
                        .apply("session_id = {0}::uuid", sessionId));

                // 每 10 条触发一次更新，容忍奇偶差异（余数0或1均触发）
                // 这样即使因历史原因或中断导致消息总数变成奇数，也能在后续对话中触发更新
                if (totalMessages > 0 && totalMessages % MEMORY_SIZE <= 1) {
                    log.info("触发滚动摘要更新: sessionId={}, totalMessages={}", sessionId, totalMessages);

                    // 动态计算需要获取的消息数量，确保覆盖所有新增消息（防止奇数偏移导致遗漏）
                    // 如果余数是1，则多取1条；如果是0，则取标准长度
                    int limit = MEMORY_SIZE + (int) (totalMessages % MEMORY_SIZE);

                    // 获取最近的消息 (作为增量)
                    List<ChatMessage> recentMessages = chatMessageService.getRecentMessages(sessionId, userId, limit);
                    // 注意：getRecentMessages 返回的是时间倒序的，需要反转
                    Collections.reverse(recentMessages); // 转为正序

                    // 获取当前摘要
                    String oldSummary = session.getSummary();

                    // 生成新摘要
                    String newSummary = summaryService.refreshSummary(oldSummary, recentMessages);

                    // 更新数据库
                    chatSessionMapper.updateSummary(sessionId, newSummary);
                    log.info("滚动摘要更新完成: sessionId={}", sessionId);
                }
            } catch (Exception e) {
                log.error("异步更新摘要失败: sessionId={}", sessionId, e);
            }
        });
    }

    /**
     * 准备会话：创建或获取现有会话
     */
    private String prepareSession(String message, String sessionId, Long userId) {
        String title = message.length() > 20 ? message.substring(0, 20) + "..." : message;
        ChatSession session = chatSessionService.getOrCreateSession(sessionId, userId, title);
        return session.getId();
    }

    /**
     * 构建上下文：结合摘要和最近历史
     */
    private List<Message> buildContext(String sessionId, Long userId) {
        // 获取会话信息（包含摘要）
        ChatSession session = chatSessionMapper.selectByIdAndUserId(sessionId, userId);
        String existingSummary = (session != null) ? session.getSummary() : null;

        List<Message> contextMessages = new ArrayList<>();

        // 1. 如果有预存的摘要，直接作为 System Message 添加
        if (existingSummary != null && !existingSummary.isEmpty()) {
            contextMessages.add(new SystemMessage("以下是早期对话的摘要总结，请基于此背景继续对话：\n" + existingSummary));
            log.info("历史会话: 使用预存的滚动摘要 (长度: {})", existingSummary.length());
        }

        // 2. 获取最近的详细消息 (保留最近 10 条作为短期记忆)
        // 无论是否有摘要，最近的 10 条都保留原文，以保证对话流畅性
        int recentCount = MEMORY_SIZE;
        List<ChatMessage> recentMessages = chatMessageService.getRecentMessages(sessionId, userId, recentCount);

        // getRecentMessages 返回的是倒序的，需要反转为正序
        Collections.reverse(recentMessages);

        if (!recentMessages.isEmpty()) {
            contextMessages.addAll(chatMessageService.convertToAiMessages(recentMessages));
            log.info("历史会话: 保留最近{}条详细消息", recentMessages.size());
        }

        return contextMessages;
    }

    /**
     * 选择模型
     */
    private String selectModel(String model, List<Document> ragDocuments) {
        String effectiveModel = model;
        if (OLLAMA_LLM.contains(effectiveModel) && (ragDocuments == null || ragDocuments.isEmpty())) {
            log.info("未检索到相关文档，强制切换为{}大模型", DEFAULT_EXTERNAL_LLM);
            effectiveModel = DEFAULT_EXTERNAL_LLM;
        }
        return (effectiveModel == null || effectiveModel.isEmpty()) ? DEFAULT_EXTERNAL_LLM : effectiveModel;
    }

    /**
     * 记录对话日志
     */
    private void logChatHistory(List<Message> allMessages) {
        StringBuilder messagesLog = new StringBuilder();
        messagesLog.append("\n==================== 完整会话上下文 START ====================\n");
        for (int i = 0; i < allMessages.size(); i++) {
            Message msg = allMessages.get(i);
            String content = msg.getContent();
            String role = msg.getMessageType().getValue();

            messagesLog.append(String.format("[%d] Role: %s\n", i, role));

            // 对于摘要类型的 SystemMessage，通常比较长，但我们也希望看到内容
            // 对于最后一条用户消息，肯定要完整显示
            // 对于其他历史消息，如果太长可以适当截断，但用户要求“看到log输出”，为了大厂调试风格，我们提供较长的预览
            if (i == allMessages.size() - 1) {
                messagesLog.append("Content (User Input): ").append(content).append("\n");
            } else if (role.equals("system") && content.contains("摘要总结")) {
                messagesLog.append("Content (Summary): ").append(content).append("\n");
            } else {
                // 增加截断长度到 100，并显示总长度
                String displayContent = content.length() > 100
                        ? content.substring(0, 100) + "...(length: " + content.length() + ")"
                        : content;
                messagesLog.append("Content: ").append(displayContent).append("\n");
            }
            messagesLog.append("--------------------------------------------------\n");
        }
        messagesLog.append("==================== 完整会话上下文 END ====================\n");
        log.info(messagesLog.toString());
    }

    /**
     * 执行流式响应
     */
    private Flux<String> streamResponse(List<Message> allMessages, String model, String sessionId, Long userId) {
        long llmStartTime = System.currentTimeMillis();

        // 构建 ChatClient
        ChatModel targetChatModel = llmProviderRegistry.getChatModel(model);
        ChatClient chatClient = ChatClient.builder(targetChatModel).build();

        // 构建 PromptSpec
        ChatClient.ChatClientRequestSpec promptSpec = chatClient.prompt()
                .system(promptService.getChatDefaultPrompt())
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
                                log.info("LLM调用完成: sessionId={}, 长度={}, 耗时{}ms",
                                        sessionId, aiResponse.length(), System.currentTimeMillis() - llmStartTime);
                            }
                        })
                        .doOnError(e -> log.error("LLM调用失败: sessionId={}, error={}", sessionId, e.getMessage()))
        );
    }

    // ==================== 会话管理方法 ====================

    @Override
    public List<ChatMessage> getHistory(String sessionId, Long userId) {
        return chatMessageService.getMessagesBySessionId(sessionId, userId);
    }

    @Override
    public List<ChatSession> listSessions(Long userId) {
        return chatSessionService.getSessionsByUserId(userId);
    }

    /**
     * 删除会话（逻辑删除）
     * 增加用户ID校验，确保用户只能删除自己的会话
     *
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @return 是否删除成功
     */
    @Override
    public boolean delete(String sessionId, Long userId) {
        log.info("执行会话逻辑删除: sessionId={}, userId={}", sessionId, userId);
        return chatSessionService.deleteSession(sessionId, userId);
    }

    /**
     * 批量删除会话（逻辑删除）
     *
     * @param sessionIds 会话ID列表
     * @param userId     用户ID
     * @return 是否删除成功
     */
    @Override
    public boolean deleteBatch(List<String> sessionIds, Long userId) {
        log.info("执行会话批量逻辑删除: sessionIds={}, userId={}", sessionIds, userId);
        return chatSessionService.deleteSessions(sessionIds, userId);
    }

    // ==================== 辅助方法 ====================

    /**
     * RAG向量检索增强
     * 从向量数据库检索相关文档，并附加到消息中
     *
     * @param message 原始消息
     * @return 增强后的消息
     */
    @Override
    public String enhance(String message) {
        List<Document> ragDocuments = performSearch(message);
        return formatMessageWithDocs(message, ragDocuments);
    }

    /**
     * 执行检索
     */
    private List<Document> performSearch(String message) {
        long startTime = System.currentTimeMillis();

        // 构建检索请求
        SearchRequest ragSearchRequest = SearchRequest.builder()
                .query(message)
                .topK(TOP_K)
                .similarityThreshold(SIMILARITY_THRESHOLD)
                .build();

        log.info("RAG检索开始: 相似度阈值={}, 检索数量={}", SIMILARITY_THRESHOLD, TOP_K);

        // 执行向量检索
        List<Document> ragDocuments = vectorStore.similaritySearch(ragSearchRequest);

        long endTime = System.currentTimeMillis();
        log.info("RAG检索完成: 命中{}条文档, 耗时{}ms",
                ragDocuments != null ? ragDocuments.size() : 0,
                endTime - startTime);
        return ragDocuments;
    }

    /**
     * 格式化消息
     */
    private String formatMessageWithDocs(String message, List<Document> ragDocuments) {
        // 记录检索到的文档信息
        if (ragDocuments != null && !ragDocuments.isEmpty()) {
            for (int i = 0; i < ragDocuments.size(); i++) {
                Document doc = ragDocuments.get(i);
                String title = doc.getText().split("\n")[0];
                log.info("{}、文档标题: {}, 相似度: {}", (i + 1), title, doc.getScore());
            }

            // 将检索结果附加到消息
            StringBuilder knowledgeContent = new StringBuilder(RagConstant.KNOWLEDGE_SOURCE_LABEL);
            for (Document doc : ragDocuments) {
                knowledgeContent.append(doc.getText()).append("\n\n");
            }
            return message + knowledgeContent.toString();
        } else {
            log.info("未检索到相关文档");
            // 明确告知LLM没有检索到知识库内容，使用情况C的回答方式
            return message + RagConstant.NO_KNOWLEDGE_FOUND_LABEL;
        }
    }
}
