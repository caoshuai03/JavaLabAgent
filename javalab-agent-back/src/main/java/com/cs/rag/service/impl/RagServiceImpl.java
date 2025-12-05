package com.cs.rag.service.impl;

import com.cs.rag.constant.RagConstant;
import com.cs.rag.entity.ChatMessage;
import com.cs.rag.entity.ChatSession;
import com.cs.rag.pojo.dto.ChatMessageDTO;
import com.cs.rag.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;


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
     * 聊天模型
     */
    private final ChatModel chatModel;

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

    /**
     * 消息服务
     */
    @Autowired
    private ChatMessageService chatMessageService;

    /**
     * 构造函数注入核心依赖
     *
     * @param vectorStore 向量存储
     * @param chatModel   聊天模型
     */
    public RagServiceImpl(VectorStore vectorStore, ChatModel chatModel) {
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
    }

    // ==================== 核心业务方法 ====================

    /**
     * 持久化RAG对话
     * 将会话和消息存储到数据库，支持跨请求的上下文管理
     */
    @Override
    public Flux<String> chat(String message, String sessionId, Long userId) {
        // ===== Step 1: 创建/获取会话 =====
        String title = message.length() > 20 ? message.substring(0, 20) + "..." : message;
        ChatSession session = chatSessionService.getOrCreateSession(sessionId, userId, title);
        String finalSessionId = session.getId();
        boolean isNewSession = (sessionId == null || sessionId.trim().isEmpty());

        log.info("会话信息: sessionId={}, isNew={}, userId={}", finalSessionId, isNewSession, userId);

        // ===== Step 2: 保存用户消息 =====
        chatMessageService.saveUserMessage(finalSessionId, message);
        log.info("已保存用户消息: sessionId={}", finalSessionId);

        // ===== Step 3: 构建滑动窗口上下文 =====
        List<ChatMessage> recentMessages = chatMessageService.getRecentMessages(finalSessionId, MEMORY_SIZE);
        // 转换为DTO形式
//        List<ChatMessageDTO> contextMessageDTOs = chatMessageService.convertToMessageDTOs(recentMessages);
        // 同时保留AI Message用于LLM调用
        List<Message> contextMessages = chatMessageService.convertToAiMessages(recentMessages);
        log.info("历史会话: 获取最近{}条消息，实际获取{}条", MEMORY_SIZE, contextMessages.size());

        // ===== Step 4: RAG消息增强 =====
        String enhancedMessage = enhance(message);

        // ===== Step 5: 构建消息列表并调用LLM =====
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        StringBuilder fullResponse = new StringBuilder();
        final String currentSessionId = finalSessionId;

        // 合并历史上下文和当前消息
        List<Message> allMessages = new ArrayList<>(contextMessages);
        allMessages.add(new UserMessage(enhancedMessage));


        StringBuilder messagesLog = new StringBuilder();

        for (int i = 0; i < allMessages.size(); i++) {
            Message msg = allMessages.get(i);
            String content = msg.getContent();
            if (i == allMessages.size() - 1) {
                messagesLog.append("Role: ").append(msg.getMessageType())
                        .append(", Content: ").append(content)
                        .append("\n");
                break;
            }
            String truncatedContent = content.length() > 20 ? content.substring(0, 20) + "..." : content;
            messagesLog.append("Role: ").append(msg.getMessageType())
                    .append(", Content: ").append(truncatedContent)
                    .append("\n");
        }
        log.info("历史会话：\n{}", messagesLog.toString());

        // 流式返回：先返回sessionId，再返回LLM响应
        return Flux.concat(
                // 首条消息返回sessionId供前端使用
                Flux.just(RagConstant.SESSION_ID_PREFIX + finalSessionId + RagConstant.SESSION_ID_SUFFIX),

                // LLM流式响应
                chatClient.prompt()
                        .system(promptService.getChatDefaultPrompt())
                        .messages(allMessages)
                        .stream()
                        .content()
                        .doOnNext(chunk -> {
                            // 收集响应片段
                            fullResponse.append(chunk);
                        })
                        .doOnComplete(() -> {
                            // 流结束后保存AI回复
                            String aiResponse = fullResponse.toString();
                            if (!aiResponse.isEmpty()) {
                                chatMessageService.saveAssistantMessage(currentSessionId, aiResponse);
                                log.info("已保存AI回复: sessionId={}, length={}",
                                        currentSessionId, aiResponse.length());
                            }
                        })
                        .doOnError(error -> {
                            log.error("LLM调用失败: sessionId={}, error={}",
                                    currentSessionId, error.getMessage());
                        })
        );
    }

    // ==================== 会话管理方法 ====================

    @Override
    public List<ChatMessage> getHistory(String sessionId) {
        return chatMessageService.getMessagesBySessionId(sessionId);
    }

    @Override
    public List<ChatSession> listSessions(Long userId) {
        return chatSessionService.getSessionsByUserId(userId);
    }

    /**
     * 删除会话（逻辑删除）
     *
     * @param sessionId 会话ID
     * @return 是否删除成功
     */
    @Override
    public boolean delete(String sessionId) {
        return chatSessionService.deleteSession(sessionId);
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
        // 构建检索请求
        SearchRequest ragSearchRequest = SearchRequest.builder()
                .query(message)
                .topK(TOP_K)
                .similarityThreshold(SIMILARITY_THRESHOLD)
                .build();

        log.info("RAG检索: 相似度阈值={}, 检索数量={}", SIMILARITY_THRESHOLD, TOP_K);

        // 执行向量检索
        List<Document> ragDocuments = vectorStore.similaritySearch(ragSearchRequest);
        log.info("RAG检索结果: 命中{}条文档", ragDocuments != null ? ragDocuments.size() : 0);

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
