package com.cs.rag.config;

import com.cs.rag.common.AdminInterceptor;
import com.cs.rag.common.JwtTokenUserInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvc配置类
 * 配置拦截器
 * 
 * @author caoshuai
 * @date 2025/01/15
 */
@Configuration
@Slf4j
public class WebMvcConfiguration implements WebMvcConfigurer {

    @Autowired
    private JwtTokenUserInterceptor jwtTokenUserInterceptor;

    @Autowired
    private AdminInterceptor adminInterceptor;

    /**
     * 注册自定义拦截器
     * 
     * @param registry 拦截器注册器
     */
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("开始注册自定义拦截器...");
        
        // 注册JWT token拦截器
        registry.addInterceptor(jwtTokenUserInterceptor)
                .addPathPatterns("/api/v1/**")  // 拦截所有v1 API路径
                .excludePathPatterns(
                        "/api/v1/user/login",       // 排除登录接口
                        "/api/v1/user/register",    // 排除注册接口
                        "/doc.html",            // 排除Swagger文档
                        "/webjars/**",          // 排除Swagger静态资源
                        "/swagger-resources/**", // 排除Swagger资源
                        "/v2/api-docs",         // 排除API文档
                        "/favicon.ico"          // 排除网站图标
                );
        
        log.info("JWT token拦截器注册完成");

        // 注册管理员权限拦截器 - 只拦截知识库上传接口
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/v1/knowledge/file/upload");  // 只拦截上传路径
        
        log.info("管理员权限拦截器注册完成");
    }
}
