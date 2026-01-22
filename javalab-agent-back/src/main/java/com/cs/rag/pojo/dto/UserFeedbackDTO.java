package com.cs.rag.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户反馈提交DTO
 * 
 * <p>用于接收前端提交的反馈信息</p>
 * 
 * @author caoshuai
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFeedbackDTO {

    /**
     * 反馈类型：0-其它 1-BUG 2-建议 3-投诉
     */
    private Integer type;

    /**
     * 反馈标题（可选）
     */
    private String title;

    /**
     * 反馈内容（必填）
     */
    private String content;

    /**
     * 优先级：0-低 1-中 2-高（可选，默认1-中）
     */
    private Integer priority;


}
