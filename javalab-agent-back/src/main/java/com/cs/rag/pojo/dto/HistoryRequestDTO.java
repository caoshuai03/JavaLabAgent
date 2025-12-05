package com.cs.rag.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 获取会话历史消息请求体
 * 
 * @author caoshuai
 * @since 1.0
 */
@Data
@Schema(description = "获取会话历史消息请求参数")
public class HistoryRequestDTO {
    
    /**
     * 会话ID
     */
    @Schema(description = "会话ID", example = "abc123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sessionId;
}
