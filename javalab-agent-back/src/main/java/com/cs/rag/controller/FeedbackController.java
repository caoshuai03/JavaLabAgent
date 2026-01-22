package com.cs.rag.controller;

import com.cs.rag.common.ApplicationConstant;
import com.cs.rag.common.BaseResponse;
import com.cs.rag.common.ErrorCode;
import com.cs.rag.common.ResultUtils;
import com.cs.rag.context.BaseContext;
import com.cs.rag.entity.UserFeedback;
import com.cs.rag.pojo.dto.UserFeedbackDTO;
import com.cs.rag.service.UserFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 用户反馈控制器
 * 
 * <p>负责处理用户反馈相关的HTTP请求</p>
 * 
 * @author caoshuai
 * @since 1.0
 */
@Tag(name = "FeedbackController", description = "用户反馈管理")
@Slf4j
@RestController
@RequestMapping(ApplicationConstant.API_VERSION + "/feedback")
public class FeedbackController {

    @Autowired
    private UserFeedbackService userFeedbackService;

    /**
     * 提交用户反馈
     * 
     * @param feedbackDTO 反馈信息
     * @return 提交结果
     */
    @PostMapping("/submit")
    @Operation(summary = "submitFeedback", description = "提交用户反馈")
    public BaseResponse<UserFeedback> submitFeedback(@RequestBody UserFeedbackDTO feedbackDTO) {
        log.info("收到用户反馈请求: {}", feedbackDTO);
        
        // 1. 获取当前登录用户ID
        Long currentUserId = BaseContext.getCurrentId();
        if (currentUserId == null) {
            return ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR, "请先登录后再提交反馈");
        }
        
        // 2. 校验必填字段
        if (feedbackDTO.getContent() == null || feedbackDTO.getContent().trim().isEmpty()) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "反馈内容不能为空");
        }
        
        // 3. 提交反馈
        try {
            UserFeedback feedback = userFeedbackService.submitFeedback(currentUserId, feedbackDTO);
            return ResultUtils.success(feedback);
        } catch (Exception e) {
            log.error("提交反馈失败: ", e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "提交反馈失败，请稍后重试");
        }
    }
}
