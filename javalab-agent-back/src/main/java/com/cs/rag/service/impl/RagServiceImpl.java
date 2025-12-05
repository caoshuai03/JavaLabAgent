package com.cs.rag.service.impl;

import com.cs.rag.constant.RagConstant;
import com.cs.rag.entity.ChatMessage;
import com.cs.rag.entity.ChatSession;
import com.cs.rag.service.*;
import com.cs.rag.utils.SearchUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

/**
 * RAG服务实现类
 * 实现RAG对话相关的核心业务逻辑
 * 
 * <p>该类负责:</p>
 * <ul>
 *   <li>RAG向量检索增强</li>
 *   <li>网络搜索增强</li>
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
    
    /** 向量存储，用于RAG检索 */
    private final VectorStore vectorStore;
    
    /** 聊天模型 */
    private final ChatModel chatModel;
    
    /** 内存对话记忆（用于非持久化模式） */
    private final ChatMemory chatMemory = new InMemoryChatMemory();
    
    /** 提示词服务 */
    @Autowired
    private PromptService promptService;
    
    /** 网络搜索工具 */
    @Autowired
    private SearchUtils searchUtils;
    
    /** 会话服务 */
    @Autowired
    private ChatSessionService chatSessionService;
    
    /** 消息服务 */
    @Autowired
    private ChatMessageService chatMessageService;
    
    /**
     * 构造函数注入核心依赖
     * 
     * @param vectorStore 向量存储
     * @param chatModel 聊天模型
     */
    public RagServiceImpl(VectorStore vectorStore, ChatModel chatModel) {
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
    }
    
    // ==================== 核心业务方法 ====================
    
    /**
     * 基础RAG对话（无持久化）
     * 使用内存对话记忆管理上下文
     */
    @Override
    public Flux<String> generateResponse(String message, String conversationId, Boolean enableWebSearch) throws IOException {
        // Step 1: 消息增强（网络搜索 + RAG）
        String enhancedMessage = message;
        if (Boolean.TRUE.equals(enableWebSearch)) {
            enhancedMessage = enhanceWithWebSearch(enhancedMessage);
        }
        enhancedMessage = enhanceWithRag(enhancedMessage);
        
        // Step 2: 构建ChatClient并流式返回
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        String finalConversationId = (conversationId != null && !conversationId.trim().isEmpty())
                ? conversationId : "0";
        
        return chatClient.prompt()
                .system(promptService.getChatDefaultPrompt())
                .advisors(new MessageChatMemoryAdvisor(chatMemory))
                .advisors(a -> a
                        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, finalConversationId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, MEMORY_SIZE))
                .user(enhancedMessage)
                .stream()
                .content();
    }
    
    /**
     * 持久化RAG对话
     * 将会话和消息存储到数据库，支持跨请求的上下文管理
     */
    @Override
    public Flux<String> generateWithPersistence(String message, String sessionId, Long userId, Boolean enableWebSearch) throws IOException {
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
        List<Message> contextMessages = chatMessageService.convertToAiMessages(recentMessages);
        log.info("滑动窗口上下文: 获取最近{}条消息，实际获取{}条", MEMORY_SIZE, contextMessages.size());
        
        // ===== Step 4: 消息增强（网络搜索 + RAG） =====
        String enhancedMessage = message;
        if (Boolean.TRUE.equals(enableWebSearch)) {
            enhancedMessage = enhanceWithWebSearch(enhancedMessage);
        }
        enhancedMessage = enhanceWithRag(enhancedMessage);
        
        // ===== Step 5: 构建消息列表并调用LLM =====
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        StringBuilder fullResponse = new StringBuilder();
        final String currentSessionId = finalSessionId;
        
        // 合并历史上下文和当前消息
        List<Message> allMessages = new ArrayList<>(contextMessages);
        allMessages.add(new UserMessage(enhancedMessage));
        
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
    public List<ChatMessage> getSessionHistory(String sessionId) {
        return chatMessageService.getMessagesBySessionId(sessionId);
    }
    
    @Override
    public List<ChatSession> getUserSessions(Long userId) {
        return chatSessionService.getSessionsByUserId(userId);
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 网络搜索增强
     * 调用Tavily API搜索相关内容，并附加到消息中
     * 
     * @param message 原始消息
     * @return 增强后的消息
     */
    @Override
    public String enhanceWithWebSearch(String message) throws IOException {
        List<Map<String, String>> searchResults = searchUtils.tavilySearch(message);
        if (!searchResults.isEmpty()) {
            StringBuilder webContent = new StringBuilder();
            for (Map<String, String> result : searchResults) {
                webContent.append(RagConstant.WEB_SOURCE_LABEL)
                        .append(result.get("title"))
                        .append("\n")
                        .append(result.get("content"))
                        .append("\n");
            }
            return message + webContent.toString();
        }
        return message;
    }
    
    /**
     * RAG向量检索增强
     * 从向量数据库检索相关文档，并附加到消息中
     * 
     * @param message 原始消息
     * @return 增强后的消息
     */
    @Override
    public String enhanceWithRag(String message) {
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
