package com.cs.rag.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * RAG对话请求体
 * 
 * <p>用于POST方式的对话请求，封装用户消息和会话信息。
 * 相比GET请求，POST方式支持更长的消息内容且更安全。</p>
 * 
 * @author caoshuai
 * @since 1.0
 */
@Data
@Schema(description = "RAG对话请求参数")
public class ChatRequestDTO {
    
    /**
     * 用户消息内容
     */
    @Schema(description = "用户消息", example = "你好，请介绍一下你自己", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;
    
    /**
     * 会话ID
     * 为空时创建新会话，后端会返回新的sessionId
     */
    @Schema(description = "会话ID，为空时创建新会话", example = "abc123")
    private String sessionId;
    
    /**
     * 用户ID
     * 默认为1
     */
    @Schema(description = "用户ID", example = "1", defaultValue = "1")
    private Long userId;
}
