package com.cs.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.rag.entity.UserFeedback;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户反馈Mapper接口
 * 
 * <p>继承MyBatis-Plus的BaseMapper，提供基础CRUD操作</p>
 * 
 * @author caoshuai
 * @since 1.0
 */
@Mapper
public interface UserFeedbackMapper extends BaseMapper<UserFeedback> {
}
