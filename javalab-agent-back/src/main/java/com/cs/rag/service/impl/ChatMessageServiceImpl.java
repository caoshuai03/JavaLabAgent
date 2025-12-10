package com.cs.rag.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cs.rag.entity.ChatMessage;
import com.cs.rag.mapper.ChatMessageMapper;
import com.cs.rag.service.ChatMessageService;
import com.cs.rag.service.ChatSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
     * @param userId 用户ID
     * @param content 消息内容
     * @return 保存的消息对象
     */
    @Override
    public ChatMessage saveUserMessage(String sessionId, Long userId, String content) {
        return saveMessage(sessionId, userId, ChatMessage.ROLE_USER, content);
    }
    
    /**
     * 保存AI助手回复
     * 同时更新会话的更新时间
     * 
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param content 消息内容
     * @return 保存的消息对象
     */
    @Override
    public ChatMessage saveAssistantMessage(String sessionId, Long userId, String content) {
        return saveMessage(sessionId, userId, ChatMessage.ROLE_ASSISTANT, content);
    }
    
    /**
     * 保存消息的通用方法
     * 
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param role 消息角色
     * @param content 消息内容
     * @return 保存的消息对象
     */
    private ChatMessage saveMessage(String sessionId, Long userId, String role, String content) {
        // 构建消息对象
        ChatMessage message = ChatMessage.builder()
                .sessionId(sessionId)
                .userId(userId)
                .role(role)
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();
        
        // 保存到数据库
        chatMessageMapper.insert(message);
        
        // 更新会话的更新时间
        chatSessionService.updateSessionTime(sessionId);
        
        log.debug("保存消息: sessionId={}, userId={}, role={}, contentLength={}", 
                sessionId, userId, role, content != null ? content.length() : 0);
        
        return message;
    }
    
    /**
     * 滑动窗口：获取最近N条消息作为上下文
     * 
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param limit 滑动窗口大小 (获取消息条数)
     * @return 时间正序的消息列表
     */
    @Override
    public List<ChatMessage> getRecentMessages(String sessionId, Long userId, int limit) {
        // 从数据库获取最近N条消息 (时间倒序)，增加用户校验
        List<ChatMessage> messages = chatMessageMapper.selectRecentMessages(sessionId, userId, limit);

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

        Message aiMessageUser = null;
        Message aiMessageAssistant = null;
        for (int i=0; i < messages.size(); i++) {

            // 跳过第一个用户消息
            ChatMessage msg = messages.get(i);
            if (i == 0 && ChatMessage.ROLE_USER.equals(msg.getRole())) {
                continue;
            }

            
            // 根据角色类型创建对应的Spring AI Message对象
            switch (msg.getRole()) {
                case ChatMessage.ROLE_USER:
                    aiMessageUser = new UserMessage(msg.getContent());
                    break;
                case ChatMessage.ROLE_ASSISTANT:
                    aiMessageAssistant = new AssistantMessage(msg.getContent());
                    break;
//                case ChatMessage.ROLE_SYSTEM:
//                    aiMessage = new SystemMessage(msg.getContent());
//                    break;
                default:
                    log.warn("未知的消息角色: {}", msg.getRole());
                    continue;
            }

            if (aiMessageUser != null && aiMessageAssistant != null) {
                aiMessages.add(aiMessageUser);
                aiMessages.add(aiMessageAssistant);
                aiMessageUser = null;
                aiMessageAssistant = null;
            }
        }
        
        return aiMessages;
    }
    
    /**
     * 获取指定会话的所有消息
     * 增加用户ID校验，确保用户只能访问自己的消息
     * 
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @return 消息列表，按时间正序
     */
    @Override
    public List<ChatMessage> getMessagesBySessionId(String sessionId, Long userId) {
        return chatMessageMapper.selectBySessionId(sessionId, userId);
    }
}
