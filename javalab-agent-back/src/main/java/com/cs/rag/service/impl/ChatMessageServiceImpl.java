package com.cs.rag.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cs.rag.entity.ChatMessage;
import com.cs.rag.mapper.ChatMessageMapper;
import com.cs.rag.service.ChatMessageService;
import com.cs.rag.service.ChatSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 消息Service实现类
 * 针对表【chat_message】的业务操作实现
 * 包含滑动窗口上下文管理的核心逻辑
 * 
 * @author caoshuai
 */
@Slf4j
@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage>
        implements ChatMessageService {
    
    @Autowired
    private ChatMessageMapper chatMessageMapper;
    
    @Autowired
    private ChatSessionService chatSessionService;
    
    /**
     * 保存用户消息
     * 同时更新会话的更新时间
     * 
     * @param sessionId 会话ID
     * @param content 消息内容
     * @return 保存的消息对象
     */
    @Override
    public ChatMessage saveUserMessage(String sessionId, String content) {
        return saveMessage(sessionId, ChatMessage.ROLE_USER, content);
    }
    
    /**
     * 保存AI助手回复
     * 同时更新会话的更新时间
     * 
     * @param sessionId 会话ID
     * @param content 消息内容
     * @return 保存的消息对象
     */
    @Override
    public ChatMessage saveAssistantMessage(String sessionId, String content) {
        return saveMessage(sessionId, ChatMessage.ROLE_ASSISTANT, content);
    }
    
    /**
     * 保存消息的通用方法
     * 
     * @param sessionId 会话ID
     * @param role 消息角色
     * @param content 消息内容
     * @return 保存的消息对象
     */
    private ChatMessage saveMessage(String sessionId, String role, String content) {
        // 构建消息对象
        ChatMessage message = ChatMessage.builder()
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();
        
        // 保存到数据库
        chatMessageMapper.insert(message);
        
        // 更新会话的更新时间
        chatSessionService.updateSessionTime(sessionId);
        
        log.debug("保存消息: sessionId={}, role={}, contentLength={}", 
                sessionId, role, content != null ? content.length() : 0);
        
        return message;
    }
    
    /**
     * 滑动窗口：获取最近N条消息作为上下文
     * 
     * @param sessionId 会话ID
     * @param limit 滑动窗口大小 (获取消息条数)
     * @return 时间正序的消息列表
     */
    @Override
    public List<ChatMessage> getRecentMessages(String sessionId, int limit) {
        // 从数据库获取最近N条消息 (时间倒序)
        List<ChatMessage> messages = chatMessageMapper.selectRecentMessages(sessionId, limit);
        
        log.info("滑动窗口查询: sessionId={}, limit={}, actualCount={}", 
                sessionId, limit, messages.size());
        
        // ===== 关键步骤: 反转为时间正序 =====
        // 数据库查询结果是 DESC (新->旧)
        // LLM需要 ASC (旧->新) 才能正确理解对话顺序
        Collections.reverse(messages);
        
        return messages;
    }
    
    /**
     * 将数据库消息列表转换为Spring AI的Message列表
     * 用于构建发送给LLM的上下文
     * 
     * @param messages 数据库消息列表
     * @return Spring AI Message列表
     */
    @Override
    public List<Message> convertToAiMessages(List<ChatMessage> messages) {
        List<Message> aiMessages = new ArrayList<>();
        
        for (ChatMessage msg : messages) {
            Message aiMessage;
            
            // 根据角色类型创建对应的Spring AI Message对象
            switch (msg.getRole()) {
                case ChatMessage.ROLE_USER:
                    aiMessage = new UserMessage(msg.getContent());
                    break;
                case ChatMessage.ROLE_ASSISTANT:
                    aiMessage = new AssistantMessage(msg.getContent());
                    break;
                case ChatMessage.ROLE_SYSTEM:
                    aiMessage = new SystemMessage(msg.getContent());
                    break;
                default:
                    log.warn("未知的消息角色: {}", msg.getRole());
                    continue;
            }
            
            aiMessages.add(aiMessage);
        }
        
        return aiMessages;
    }
    
    /**
     * 获取指定会话的所有消息
     * 
     * @param sessionId 会话ID
     * @return 消息列表，按时间正序
     */
    @Override
    public List<ChatMessage> getMessagesBySessionId(String sessionId) {
        return chatMessageMapper.selectBySessionId(sessionId);
    }
}
