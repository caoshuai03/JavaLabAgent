package com.cs.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 对话消息实体类
 * 用于存储每条对话消息记录，包括用户消息和AI回复
 * 
 * @TableName chat_message
 * @author caoshuai
 */
@TableName(value = "chat_message")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    
    /**
     * 消息ID (自增主键)
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    
    /**
     * 会话ID (外键关联chat_session表)
     */
    private String sessionId;
    
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
    
    /**
     * 向量嵌入 (预留字段，用于后续语义检索功能)
     * 注意: MyBatis-Plus默认不支持vector类型，需要单独处理
     * 本次暂不使用
     */
    // private float[] embedding;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 消息角色常量定义
     */
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_SYSTEM = "system";
}
