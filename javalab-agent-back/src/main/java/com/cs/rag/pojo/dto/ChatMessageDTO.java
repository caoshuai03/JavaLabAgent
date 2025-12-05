package com.cs.rag.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天消息DTO
 * 用于传输对话上下文，仅包含必要的role和content字段
 * 
 * @author caoshuai
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {
    
    /**
     * 消息角色
     * user: 用户消息
     * assistant: AI助手回复
     * system: 系统消息
     */
    private String role;
    
    /**
     * 消息内容
     */
    private String content;
}
