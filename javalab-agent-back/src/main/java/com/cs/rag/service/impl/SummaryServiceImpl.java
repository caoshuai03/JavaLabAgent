package com.cs.rag.service.impl;

import com.cs.rag.constant.RagConstant;
import com.cs.rag.entity.ChatMessage;
import com.cs.rag.llm.LLMProviderRegistry;
import com.cs.rag.service.PromptService;
import com.cs.rag.service.SummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.cs.rag.service.ChatMessageService;
import org.springframework.ai.chat.messages.Message;
import java.util.stream.Collectors;

import java.util.List;

/**
 * 摘要生成服务实现类
 *
 * @author caoshuai
 * @since 1.0
 */
@Slf4j
@Service
public class SummaryServiceImpl implements SummaryService {

    @Autowired
    private LLMProviderRegistry llmProviderRegistry;

    @Autowired
    private PromptService promptService;

    @Autowired
    private ChatMessageService chatMessageService;

    /**
     * 生成对话历史摘要
     * 
     * @param messages 对话历史消息列表
     * @return 生成的摘要文本
     */
    @Override
    public String summarize(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        try {
            // 1. 构建对话文本 (使用标准的消息转换服务)
            List<Message> aiMessages = chatMessageService.convertToAiMessages(messages);
            
            String chatHistory = aiMessages.stream()
                    .map(msg -> msg.getMessageType().getValue() + ": " + msg.getContent())
                    .collect(Collectors.joining("\n"));

            log.info("用于生成摘要的对话历史原文：\n{}", chatHistory);

            // 2. 调用LLM
            return callLlmForSummary(chatHistory);

        } catch (Exception e) {
            log.error("生成对话摘要失败", e);
            return "";
        }
    }

    /**
     * 基于已有摘要和新消息生成更新后的摘要 (滚动更新)
     */
    @Override
    public String refreshSummary(String oldSummary, List<ChatMessage> newMessages) {
        if (newMessages == null || newMessages.isEmpty()) {
            return oldSummary;
        }

        try {
            // 1. 构建新消息文本
            List<Message> aiMessages = chatMessageService.convertToAiMessages(newMessages);
            String newChatHistory = aiMessages.stream()
                    .map(msg -> msg.getMessageType().getValue() + ": " + msg.getContent())
                    .collect(Collectors.joining("\n"));

            // 2. 构建合并后的输入：旧摘要 + 新对话
            String combinedInput;
            if (oldSummary != null && !oldSummary.isEmpty()) {
                combinedInput = String.format("【已知上下文摘要】:\n%s\n\n【新发生的对话】:\n%s", oldSummary, newChatHistory);
            } else {
                combinedInput = newChatHistory;
            }

            log.info("执行滚动摘要更新，输入长度: {}", combinedInput.length());
            
            // 3. 调用LLM
            return callLlmForSummary(combinedInput);

        } catch (Exception e) {
            log.error("滚动更新摘要失败", e);
            return oldSummary; // 失败时返回旧摘要，保证不丢失
        }
    }

    private String callLlmForSummary(String inputContent) {
        // 获取Prompt
        String systemPrompt = promptService.getChatSummaryPrompt();
        
        // 调用LLM生成摘要
        ChatModel chatModel = llmProviderRegistry.getChatModel(RagConstant.DEFAULT_EXTERNAL_LLM);
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        
        String summary = chatClient.prompt()
                .system(systemPrompt)
                .user(inputContent)
                .options(ChatOptions.builder().model(RagConstant.DEFAULT_EXTERNAL_LLM).build())
                .call()
                .content();

        log.info("摘要生成完成，输出长度: {}", summary.length());
        return summary;
    }
}
