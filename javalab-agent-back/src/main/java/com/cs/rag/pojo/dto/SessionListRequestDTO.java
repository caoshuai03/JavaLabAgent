package com.cs.rag.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 获取用户会话列表请求体
 * 
 * @author caoshuai
 * @since 1.0
 */
@Data
@Schema(description = "获取用户会话列表请求参数")
public class SessionListRequestDTO {
    
    /**
     * 用户ID，默认为1
     */
    @Schema(description = "用户ID", example = "1", defaultValue = "1")
    private Long userId = 1L;
}
