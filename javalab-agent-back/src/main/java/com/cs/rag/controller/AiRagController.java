package com.cs.rag.controller;

import com.cs.rag.common.ApplicationConstant;
import com.cs.rag.entity.ChatMessage;
import com.cs.rag.entity.ChatSession;
import com.cs.rag.pojo.dto.ChatRequestDTO;
import com.cs.rag.pojo.dto.HistoryRequestDTO;
import com.cs.rag.pojo.dto.SessionListRequestDTO;
import com.cs.rag.service.RagService;
import org.springframework.http.MediaType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * RAG对话控制器
 * 
 * <p>该控制器负责处理RAG（检索增强生成）相关的HTTP请求，
 * 将业务逻辑委托给RagService处理，遵循MVC架构的关注点分离原则。</p>
 * 
 * <p>主要功能:</p>
 * <ul>
 *   <li>持久化RAG对话（数据库会话管理）</li>
 *   <li>会话历史查询</li>
 *   <li>用户会话列表查询</li>
 * </ul>
 * 
 * @author caoshuai
 * @since 1.0
 */
@Tag(name = "AiRagController", description = "RAG对话接口")
@Slf4j
@RestController
@RequestMapping(ApplicationConstant.API_VERSION + "/ai")
public class AiRagController {
    
    /** RAG业务服务 */
    @Autowired
    private RagService ragService;
    
    // ==================== 对话接口 ====================
    
    /**
     * 持久化RAG对话接口
     * 
     * <p>使用POST请求体传递参数，支持更长的消息内容，更安全。
     * 支持数据库持久化和滑动窗口上下文管理。</p>
     * 
     * <p>响应格式: 第一条消息为[SESSION_ID:xxx]，后续为LLM流式响应</p>
     * 
     * @param request 对话请求参数（JSON请求体）
     * @return SSE流式响应，首条消息包含sessionId
     */
    @Operation(summary = "chat", description = "持久化RAG对话接口")
    @PostMapping(value = "/rag", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ChatRequestDTO request) {
        
        // 参数校验与默认值处理
        String message = (request.getMessage() != null) ? request.getMessage() : "你好";
        String sessionId = request.getSessionId();
        Long userId = (request.getUserId() != null) ? request.getUserId() : 1L;
        
        log.info("持久化RAG对话请求: message={}, sessionId={}, userId={}", 
                 message, sessionId, userId);
        
        // 委托给Service层处理业务逻辑
        return ragService.chat(message, sessionId, userId);
    }
    
    // ==================== 会话管理接口 ====================
    
    /**
     * 获取会话历史消息列表
     * 
     * @param request 请求参数（JSON请求体）
     * @return 消息列表，按时间正序排列
     */
    @Operation(summary = "getHistory", description = "获取会话历史消息")
    @PostMapping(value = "/rag/history")
    public List<ChatMessage> getHistory(
            @RequestBody HistoryRequestDTO request) {
        
        String sessionId = request.getSessionId();
        
        log.info("获取会话历史: sessionId={}", sessionId);
        return ragService.getHistory(sessionId);
    }

    
    /**
     * 获取用户的会话列表
     * 
     * @param request 请求参数（JSON请求体）
     * @return 会话列表，按更新时间倒序排列
     */
    @Operation(summary = "getSessions", description = "获取用户会话列表")
    @PostMapping(value = "/rag/sessions")
    public List<ChatSession> listSessions(
            @RequestBody SessionListRequestDTO request) {
        
        // 获取userId，DTO中已设置默认值为1
        Long userId = request.getUserId();
        
        log.info("获取用户会话列表: userId={}", userId);
        return ragService.listSessions(userId);
    }
    
    /**
     * 删除会话（逻辑删除）
     * 
     * @param sessionId 会话ID
     * @return 删除结果，true表示成功，false表示失败
     */
    @Operation(summary = "deleteSession", description = "删除会话（逻辑删除）")
    @DeleteMapping(value = "/rag/sessions/{sessionId}")
    public boolean delete(@PathVariable("sessionId") String sessionId) {
        
        log.info("删除会话请求: sessionId={}", sessionId);
        return ragService.delete(sessionId);
    }
}