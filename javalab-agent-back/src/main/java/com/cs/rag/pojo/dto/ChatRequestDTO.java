package com.cs.rag.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天请求DTO
 * 用于接收前端发送的对话请求
 * 
 * @author caoshuai
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "聊天请求DTO")
public class ChatRequestDTO {
    
    /**
     * 用户消息内容
     */
    @Schema(description = "用户消息内容", example = "你好，请介绍一下Java")
    private String message;
    
    /**
     * 会话ID (可选)
     * 如果为空，将创建新会话
     */
    @Schema(description = "会话ID，为空时创建新会话", example = "550e8400-e29b-41d4-a716-446655440000")
    private String sessionId;
    
    /**
     * 用户ID (可选)
     * 如果为空，使用默认用户
     */
    @Schema(description = "用户ID", example = "1")
    private Long userId;
}
