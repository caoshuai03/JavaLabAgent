package com.cs.rag.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天响应VO
 * 用于返回对话结果给前端
 * 
 * @author caoshuai
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "聊天响应VO")
public class ChatResponseVO {
    
    /**
     * 会话ID
     * 新会话时返回生成的ID，前端后续请求需携带此ID
     */
    @Schema(description = "会话ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String sessionId;
    
    /**
     * AI回复内容
     */
    @Schema(description = "AI回复内容")
    private String content;
    
    /**
     * 是否为新会话
     */
    @Schema(description = "是否为新会话", example = "true")
    private Boolean isNewSession;
}
