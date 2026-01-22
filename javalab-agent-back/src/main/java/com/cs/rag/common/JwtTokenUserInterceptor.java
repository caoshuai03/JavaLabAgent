package com.cs.rag.common;

import com.cs.rag.config.JwtProperties;
import com.cs.rag.constant.JwtClaimsConstant;
import com.cs.rag.context.BaseContext;
import com.cs.rag.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;


/**
 * jwt令牌校验的拦截器
 */
@Component
@Slf4j
public class JwtTokenUserInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 校验jwt
     *
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //判断当前拦截到的是Controller的方法还是其他资源
        if (!(handler instanceof HandlerMethod)) {
            //当前拦截到的不是动态方法，直接放行
            return true;
        }

        //1、从请求头中获取令牌
        String token = request.getHeader(jwtProperties.getUserTokenName());

        if (token != null && token.startsWith("Bearer ")) {
            // 2. 从第7位开始截取，拿到纯JWT令牌（Bearer 后面有个空格，共7个字符）
            token = token.substring(7);
//            log.info("jwt校验:{}", token);
            try {

                Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
                Long userId = Long.valueOf(claims.get(JwtClaimsConstant.USER_ID).toString());
//                log.info("当前用户的id：{}", userId);
                BaseContext.setCurrentId(userId);
                //3、通过，放行
                return true;
            } catch (Exception ex) {
                //4、不通过，响应401状态码
                response.setStatus(ErrorCode.NO_AUTH_ERROR.getCode());
                return false;
            }
        }else {
            response.setStatus(ErrorCode.NO_AUTH_ERROR.getCode());
            return false;
        }
    }
}