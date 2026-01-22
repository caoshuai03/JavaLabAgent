package com.cs.rag.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cs.rag.entity.UserFeedback;
import com.cs.rag.pojo.dto.UserFeedbackDTO;

/**
 * 用户反馈服务接口
 * 
 * @author caoshuai
 * @since 1.0
 */
public interface UserFeedbackService extends IService<UserFeedback> {

    /**
     * 提交用户反馈
     * 
     * @param userId 当前登录用户ID
     * @param feedbackDTO 反馈信息DTO
     * @return 创建的反馈记录
     */
    UserFeedback submitFeedback(Long userId, UserFeedbackDTO feedbackDTO);
}
