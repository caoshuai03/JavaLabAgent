package com.cs.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cs.rag.constant.RagConstant;
import com.cs.rag.pojo.entity.ChatMessage;
import com.cs.rag.pojo.entity.ChatSession;
import com.cs.rag.mapper.ChatSessionMapper;
import com.cs.rag.service.ChatMessageService;
import com.cs.rag.service.ChatSessionService;
import com.cs.rag.service.PromptService;
import com.cs.rag.service.SummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.cs.rag.constant.RagConstant.DEFAULT_EXTERNAL_LLM;
import static com.cs.rag.constant.RagConstant.MEMORY_SIZE;
import static com.cs.rag.constant.RagConstant.OLLAMA_LLM;
import static com.cs.rag.constant.RagConstant.SIMILARITY_THRESHOLD;
import static com.cs.rag.constant.RagConstant.TOP_K;

@Slf4j
@Service
public class RagConversationSupport {

    private static final Pattern CORE_TERM_PATTERN = Pattern.compile("\\b[A-Za-z][A-Za-z0-9_]*\\b");

    private final VectorStore vectorStore;
    private final ChatSessionService chatSessionService;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageService chatMessageService;
    private final PromptService promptService;
    private final SummaryService summaryService;

    public RagConversationSupport(VectorStore vectorStore,
                                  ChatSessionService chatSessionService,
                                  ChatSessionMapper chatSessionMapper,
                                  ChatMessageService chatMessageService,
                                  PromptService promptService,
                                  SummaryService summaryService) {
        this.vectorStore = vectorStore;
        this.chatSessionService = chatSessionService;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageService = chatMessageService;
        this.promptService = promptService;
        this.summaryService = summaryService;
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

    public void refreshSummaryAsync(String sessionId, Long userId, String source) {
        CompletableFuture.runAsync(() -> refreshSummary(sessionId, userId, source));
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
        ragDocuments = filterByTermConsistency(message, ragDocuments);
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
            return promptService.buildRagUserMessage(message, buildKnowledgeBlock(ragDocuments));
        }
        log.info("未检索到相关文档");
        return promptService.buildRagUserMessage(message, "");
    }

    private String buildKnowledgeBlock(List<Document> ragDocuments) {
        StringBuilder knowledgeContent = new StringBuilder();
        for (Document doc : ragDocuments) {
            knowledgeContent.append(doc.getText()).append("\n\n");
        }
        return knowledgeContent.toString();
    }

    private List<Document> filterByTermConsistency(String userMessage, List<Document> ragDocuments) {
        if (ragDocuments == null || ragDocuments.isEmpty()) {
            return ragDocuments;
        }

        Set<String> userTerms = extractCoreTerms(userMessage);
        if (userTerms.isEmpty()) {
            return ragDocuments;
        }

        List<Document> filtered = ragDocuments.stream()
                .filter(doc -> hasConsistentTerms(userTerms, doc))
                .collect(Collectors.toList());

        if (filtered.size() != ragDocuments.size()) {
            log.info("术语一致性过滤完成: 原始命中{}条, 过滤后{}条", ragDocuments.size(), filtered.size());
        }
        return filtered;
    }

    private boolean hasConsistentTerms(Set<String> userTerms, Document doc) {
        String text = doc.getText();
        if (text == null || text.isBlank()) {
            return false;
        }

        String firstLine = text.lines().findFirst().orElse(text);
        Set<String> docTerms = extractCoreTerms(firstLine);
        if (docTerms.isEmpty()) {
            return true;
        }

        Set<String> intersection = new HashSet<>(userTerms);
        intersection.retainAll(docTerms);
        return !intersection.isEmpty();
    }

    private Set<String> extractCoreTerms(String text) {
        Set<String> terms = new HashSet<>();
        if (text == null || text.isBlank()) {
            return terms;
        }

        Matcher matcher = CORE_TERM_PATTERN.matcher(text);
        while (matcher.find()) {
            String term = matcher.group().toLowerCase(Locale.ROOT);
            if (term.length() > 1) {
                terms.add(term);
            }
        }

        Arrays.stream(text.split("[^\\p{IsHan}A-Za-z0-9_]+"))
                .map(String::trim)
                .filter(token -> token.length() > 1)
                .filter(token -> token.chars().anyMatch(Character::isLetter))
                .map(token -> token.toLowerCase(Locale.ROOT))
                .forEach(terms::add);

        return terms;
    }

    public List<ChatMessage> getRecentMessages(String sessionId, Long userId, int limit) {
        return chatMessageService.getRecentMessages(sessionId, userId, limit);
    }

    public long countMessagesBySession(String sessionId) {
        return chatMessageService.count(new LambdaQueryWrapper<ChatMessage>()
                .apply("session_id = {0}::uuid", sessionId));
    }

    private void refreshSummary(String sessionId, Long userId, String source) {
        try {
            ChatSession session = chatSessionMapper.selectByIdAndUserId(sessionId, userId);
            if (session == null) {
                log.warn("Skip {} summary refresh: sessionId={}, userId={}, reason=session_not_found", source, sessionId, userId);
                return;
            }

            long totalMessages = countMessagesBySession(sessionId);
            if (!shouldRefreshSummary(totalMessages)) {
                log.info("Skip {} summary refresh: sessionId={}, totalMessages={}, reason=refresh_condition_not_met",
                        source, sessionId, totalMessages);
                return;
            }

            int limit = calculateSummaryRefreshLimit(totalMessages);
            List<ChatMessage> recentMessages = chatMessageService.getRecentMessages(sessionId, userId, limit);
            Collections.reverse(recentMessages);
            log.info("{} summary refresh prepared: sessionId={}, limit={}, recentMessages={}, oldSummaryLength={}",
                    source,
                    sessionId,
                    limit,
                    recentMessages.size(),
                    session.getSummary() != null ? session.getSummary().length() : 0);

            String newSummary = summaryService.refreshSummary(session.getSummary(), recentMessages);
            chatSessionMapper.updateSummary(sessionId, newSummary);
            log.info("{} summary refresh completed: sessionId={}, newSummaryLength={}",
                    source,
                    sessionId,
                    newSummary != null ? newSummary.length() : 0);
        } catch (Exception e) {
            log.error("{} summary refresh failed: sessionId={}", source, sessionId, e);
        }
    }

    private boolean shouldRefreshSummary(long totalMessages) {
        return totalMessages > 0 && totalMessages % MEMORY_SIZE <= 1;
    }

    private int calculateSummaryRefreshLimit(long totalMessages) {
        return MEMORY_SIZE + (int) (totalMessages % MEMORY_SIZE);
    }
}
