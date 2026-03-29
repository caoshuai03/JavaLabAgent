package com.cs.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cs.rag.constant.RagConstant;
import com.cs.rag.pojo.entity.ChatMessage;
import com.cs.rag.pojo.entity.ChatSession;
import com.cs.rag.mapper.ChatSessionMapper;
import com.cs.rag.service.ChatMessageService;
import com.cs.rag.service.ChatSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.cs.rag.constant.RagConstant.DEFAULT_EXTERNAL_LLM;
import static com.cs.rag.constant.RagConstant.MEMORY_SIZE;
import static com.cs.rag.constant.RagConstant.OLLAMA_LLM;
import static com.cs.rag.constant.RagConstant.SIMILARITY_THRESHOLD;
import static com.cs.rag.constant.RagConstant.TOP_K;

@Slf4j
@Service
public class RagConversationSupport {

    private final VectorStore vectorStore;
    private final ChatSessionService chatSessionService;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageService chatMessageService;

    public RagConversationSupport(VectorStore vectorStore,
                                  ChatSessionService chatSessionService,
                                  ChatSessionMapper chatSessionMapper,
                                  ChatMessageService chatMessageService) {
        this.vectorStore = vectorStore;
        this.chatSessionService = chatSessionService;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageService = chatMessageService;
    }

    public String prepareSession(String message, String sessionId, Long userId) {
        String title = message.length() > 20 ? message.substring(0, 20) + "..." : message;
        ChatSession session = chatSessionService.getOrCreateSession(sessionId, userId, title);
        return session.getId();
    }

    public List<Message> buildContext(String sessionId, Long userId) {
        ChatSession session = chatSessionMapper.selectByIdAndUserId(sessionId, userId);
        String existingSummary = session != null ? session.getSummary() : null;
        List<Message> contextMessages = new ArrayList<>();
        if (existingSummary != null && !existingSummary.isEmpty()) {
            contextMessages.add(new SystemMessage("以下是早期对话的摘要总结，请基于此背景继续对话：\n" + existingSummary));
            log.info("历史会话: 使用预存的滚动摘要 (长度: {})", existingSummary.length());
        }
        List<ChatMessage> recentMessages = chatMessageService.getRecentMessages(sessionId, userId, MEMORY_SIZE);
        Collections.reverse(recentMessages);
        if (!recentMessages.isEmpty()) {
            contextMessages.addAll(chatMessageService.convertToAiMessages(recentMessages));
            log.info("历史会话: 保留最近{}条详细消息", recentMessages.size());
        }
        return contextMessages;
    }

    public String selectModel(String model, List<Document> ragDocuments) {
        String effectiveModel = model;
        if (OLLAMA_LLM.contains(effectiveModel) && (ragDocuments == null || ragDocuments.isEmpty())) {
            log.info("未检索到相关文档，强制切换为{}大模型", DEFAULT_EXTERNAL_LLM);
            effectiveModel = DEFAULT_EXTERNAL_LLM;
        }
        return (effectiveModel == null || effectiveModel.isEmpty()) ? DEFAULT_EXTERNAL_LLM : effectiveModel;
    }

    public List<Document> performSearch(String message) {
        long startTime = System.currentTimeMillis();
        SearchRequest ragSearchRequest = SearchRequest.builder()
                .query(message)
                .topK(TOP_K)
                .similarityThreshold(SIMILARITY_THRESHOLD)
                .build();
        log.info("RAG检索开始: 相似度阈值={}, 检索数量={}", SIMILARITY_THRESHOLD, TOP_K);
        List<Document> ragDocuments = vectorStore.similaritySearch(ragSearchRequest);
        long endTime = System.currentTimeMillis();
        log.info("RAG检索完成: 命中{}条文档, 耗时{}ms",
                ragDocuments != null ? ragDocuments.size() : 0,
                endTime - startTime);
        return ragDocuments;
    }

    public String formatMessageWithDocs(String message, List<Document> ragDocuments) {
        if (ragDocuments != null && !ragDocuments.isEmpty()) {
            for (int i = 0; i < ragDocuments.size(); i++) {
                Document doc = ragDocuments.get(i);
                String title = doc.getText().split("\n")[0];
                log.info("{}、文档标题: {}, 相似度: {}", (i + 1), title, doc.getScore());
            }
            StringBuilder knowledgeContent = new StringBuilder(RagConstant.KNOWLEDGE_SOURCE_LABEL);
            for (Document doc : ragDocuments) {
                knowledgeContent.append(doc.getText()).append("\n\n");
            }
            return message + knowledgeContent;
        }
        log.info("未检索到相关文档");
        return message + RagConstant.NO_KNOWLEDGE_FOUND_LABEL;
    }

    public List<ChatMessage> getRecentMessages(String sessionId, Long userId, int limit) {
        return chatMessageService.getRecentMessages(sessionId, userId, limit);
    }

    public long countMessagesBySession(String sessionId) {
        return chatMessageService.count(new LambdaQueryWrapper<ChatMessage>()
                .apply("session_id = {0}::uuid", sessionId));
    }
}
