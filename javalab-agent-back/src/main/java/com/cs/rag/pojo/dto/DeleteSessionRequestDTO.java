package com.cs.rag.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 删除会话请求体
 * 
 * @author caoshuai
 * @since 1.0
 */
@Data
@Schema(description = "删除会话请求参数")
public class DeleteSessionRequestDTO {
    
    /**
     * 会话ID
     */
    @Schema(description = "会话ID", example = "abc123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sessionId;
    
    /**
     * 批量删除的会话ID列表
     */
    @Schema(description = "批量删除的会话ID列表", example = "[\"abc123\", \"def456\"]")
    private java.util.List<String> sessionIds;
    
    /**
     * 用户ID（用于权限校验）
     */
    @Schema(description = "用户ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userId = 1L;
}
