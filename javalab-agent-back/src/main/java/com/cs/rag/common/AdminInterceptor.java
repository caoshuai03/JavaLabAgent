package com.cs.rag.common;

import com.cs.rag.context.BaseContext;
import com.cs.rag.entity.User;
import com.cs.rag.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理员权限拦截器
 * 拦截需要管理员权限的接口（如知识库上传）
 *
 * @author caoshuai
 */
@Component
@Slf4j
public class AdminInterceptor implements HandlerInterceptor {

    @Autowired
    private UserMapper userMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取当前用户ID
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            log.warn("管理员权限校验失败：用户未登录");
            response.setStatus(401);
            return false;
        }

        // 查询用户角色
        User user = userMapper.selectById(userId);
        if (user == null || user.getRole() == null || user.getRole() != 1) {
            // 非管理员，返回403
            log.warn("管理员权限校验失败：用户ID={} 不是管理员", userId);
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":40300,\"message\":\"仅管理员可上传，请联系管理员\"}");
            return false;
        }

        log.info("管理员权限校验通过：用户ID={}", userId);
        return true;
    }
}
