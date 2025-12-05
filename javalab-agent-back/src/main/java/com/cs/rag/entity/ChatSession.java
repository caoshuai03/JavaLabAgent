package com.cs.rag.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 对话会话实体类
 * 用于存储用户与AI助手的对话会话信息
 * 
 * @TableName chat_session
 * @author caoshuai
 */
@TableName(value = "chat_session")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {
    
    /**
     * 会话ID (UUID主键)
     */
    @TableId
    private String id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 会话标题
     * 通常取自用户第一条消息的摘要
     */
    private String title;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     * 每次新增消息时更新此字段
     */
    private LocalDateTime updatedAt;
}
