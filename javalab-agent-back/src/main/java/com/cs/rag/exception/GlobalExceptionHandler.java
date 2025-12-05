package com.cs.rag.exception;

import com.cs.rag.common.BaseResponse;
import com.cs.rag.common.ErrorCode;
import com.cs.rag.common.ResultUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 处理业务异常和运行时异常，支持 JSON 和 SSE 请求
 * 
 * @author caoshuai
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     * 
     * @param e 业务异常
     * @param request HTTP请求对象
     * @return 错误响应（JSON 或纯文本）
     */
    @ExceptionHandler(BusinessException.class)
    public Object businessExceptionHandler(BusinessException e, HttpServletRequest request) {
        log.error("业务异常: {}", e.getMessage(), e);
        
        // 检查是否是 SSE 请求，如果是则返回纯文本
        if (isEventStreamRequest(request)) {
            return "[ERROR] " + e.getMessage();
        }
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理运行时异常
     * 
     * @param e 运行时异常
     * @param request HTTP请求对象
     * @return 错误响应（JSON 或纯文本）
     */
    @ExceptionHandler(RuntimeException.class)
    public Object runtimeExceptionHandler(RuntimeException e, HttpServletRequest request) {
        log.error("运行时异常: {}", e.getMessage(), e);
        
        // 检查是否是 SSE 请求，如果是则返回纯文本
        if (isEventStreamRequest(request)) {
            return "[ERROR] " + e.getMessage();
        }
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, e.getMessage());
    }
    
    /**
     * 判断请求是否期望 SSE (text/event-stream) 响应
     * 
     * @param request HTTP请求对象
     * @return true 如果是 SSE 请求
     */
    private boolean isEventStreamRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }
}