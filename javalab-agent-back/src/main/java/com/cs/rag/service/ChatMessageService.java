package com.cs.rag.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cs.rag.entity.ChatMessage;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 消息Service接口
 * 针对表【chat_message】的业务操作
 * 包含滑动窗口上下文管理的核心逻辑
 * 
 * @author caoshuai
 */
public interface ChatMessageService extends IService<ChatMessage> {
    
    /**
     * 保存用户消息
     * 
     * @param sessionId 会话ID
     * @param content 消息内容
     * @return 保存的消息对象
     */
    ChatMessage saveUserMessage(String sessionId, String content);
    
    /**
     * 保存AI助手回复
     * 
     * @param sessionId 会话ID
     * @param content 消息内容
     * @return 保存的消息对象
     */
    ChatMessage saveAssistantMessage(String sessionId, String content);
    
    /**
     * 滑动窗口：获取最近N条消息作为上下文
     * 
     * 滑动窗口策略说明：
     * 1. 从数据库查询最近N条消息 (按时间倒序)
     * 2. 将结果反转为时间正序，符合LLM对话顺序要求
     * 
     * @param sessionId 会话ID
     * @param limit 滑动窗口大小 (获取消息条数)
     * @return 时间正序的消息列表
     */
    List<ChatMessage> getRecentMessages(String sessionId, int limit);
    
    /**
     * 将数据库消息列表转换为Spring AI的Message列表
     * 用于构建发送给LLM的上下文
     * 
     * @param messages 数据库消息列表
     * @return Spring AI Message列表
     */
    List<Message> convertToAiMessages(List<ChatMessage> messages);
    
    /**
     * 获取指定会话的所有消息
     * 
     * @param sessionId 会话ID
     * @return 消息列表，按时间正序
     */
    List<ChatMessage> getMessagesBySessionId(String sessionId);
}
