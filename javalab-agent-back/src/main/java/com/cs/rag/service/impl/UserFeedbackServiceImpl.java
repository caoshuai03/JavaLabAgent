package com.cs.rag.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cs.rag.entity.UserFeedback;
import com.cs.rag.mapper.UserFeedbackMapper;
import com.cs.rag.pojo.dto.UserFeedbackDTO;
import com.cs.rag.service.UserFeedbackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 用户反馈服务实现类
 * 
 * @author caoshuai
 * @since 1.0
 */
@Slf4j
@Service
public class UserFeedbackServiceImpl extends ServiceImpl<UserFeedbackMapper, UserFeedback> implements UserFeedbackService {

    /**
     * 提交用户反馈
     * 
     * @param userId 当前登录用户ID
     * @param feedbackDTO 反馈信息DTO
     * @return 创建的反馈记录
     */
    @Override
    public UserFeedback submitFeedback(Long userId, UserFeedbackDTO feedbackDTO) {
        log.info("用户[{}]提交反馈: type={}, title={}", userId, feedbackDTO.getType(), feedbackDTO.getTitle());
        
        // 构建反馈实体
        UserFeedback feedback = UserFeedback.builder()
                .userId(userId)
                .type(feedbackDTO.getType() != null ? feedbackDTO.getType() : 0)
                .title(feedbackDTO.getTitle())
                .content(feedbackDTO.getContent())
                .status(0)  // 新建状态
                .priority(feedbackDTO.getPriority() != null ? feedbackDTO.getPriority() : 1)  // 默认中优先级
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        
        // 保存到数据库
        this.save(feedback);
        
        log.info("反馈提交成功, id={}", feedback.getId());
        return feedback;
    }
}
