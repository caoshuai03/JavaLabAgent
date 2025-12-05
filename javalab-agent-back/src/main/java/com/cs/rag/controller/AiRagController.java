package com.cs.rag.controller;

import com.cs.rag.common.ApplicationConstant;
import com.cs.rag.entity.ChatMessage;
import com.cs.rag.entity.ChatSession;
import com.cs.rag.service.RagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;

/**
 * RAG对话控制器
 * 
 * <p>该控制器负责处理RAG（检索增强生成）相关的HTTP请求，
 * 将业务逻辑委托给RagService处理，遵循MVC架构的关注点分离原则。</p>
 * 
 * <p>主要功能:</p>
 * <ul>
 *   <li>基础RAG对话（内存会话记忆）</li>
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
     * 基础RAG对话接口
     * 
     * <p>使用内存会话记忆管理上下文，不持久化到数据库。
     * 适用于临时对话场景。</p>
     * 
     * @param message 用户消息
     * @param conversationId 会话ID，用于内存中的对话记忆，默认为"0"
     * @param enableWebSearch 是否启用网络搜索增强，默认false
     * @return 流式响应内容
     * @throws IOException 网络搜索失败时抛出
     */
    @Operation(summary = "rag", description = "基础RAG对话接口（内存会话记忆）")
    @GetMapping(value = "/rag")
    public Flux<String> generate(
            @RequestParam(value = "message", defaultValue = "你好") String message,
            @RequestParam(value = "conversationId", required = false) String conversationId,
            @RequestParam(value = "enableWebSearch", required = false, defaultValue = "false") Boolean enableWebSearch)
            throws IOException {
        
        log.info("RAG对话请求: message={}, conversationId={}, enableWebSearch={}", 
                message, conversationId, enableWebSearch);
        
        // 委托给Service层处理业务逻辑
        return ragService.generateResponse(message, conversationId, enableWebSearch);
    }
    
    /**
     * 持久化RAG对话接口
     * 
     * <p>支持数据库持久化和滑动窗口上下文管理。
     * 适用于需要保存对话历史的场景。</p>
     * 
     * <p>响应格式: 第一条消息为[SESSION_ID:xxx]，后续为LLM流式响应</p>
     * 
     * @param message 用户消息
     * @param sessionId 会话ID，为空时创建新会话
     * @param userId 用户ID，默认为1
     * @param enableWebSearch 是否启用网络搜索增强，默认false
     * @return 流式响应，首条消息包含sessionId
     * @throws IOException 网络搜索失败时抛出
     */
    @Operation(summary = "ragPersistent", description = "持久化RAG对话接口（数据库会话管理）")
    @GetMapping(value = "/rag/persistent")
    public Flux<String> generateWithPersistence(
            @RequestParam(value = "message", defaultValue = "你好") String message,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "userId", required = false, defaultValue = "1") Long userId,
            @RequestParam(value = "enableWebSearch", required = false, defaultValue = "false") Boolean enableWebSearch)
            throws IOException {
        
        log.info("持久化RAG对话请求: message={}, sessionId={}, userId={}, enableWebSearch={}", 
                message, sessionId, userId, enableWebSearch);
        
        // 委托给Service层处理业务逻辑
        return ragService.generateWithPersistence(message, sessionId, userId, enableWebSearch);
    }
    
    // ==================== 会话管理接口 ====================
    
    /**
     * 获取会话历史消息列表
     * 
     * @param sessionId 会话ID
     * @return 消息列表，按时间正序排列
     */
    @Operation(summary = "getHistory", description = "获取会话历史消息")
    @GetMapping(value = "/rag/history")
    public List<ChatMessage> getSessionHistory(
            @RequestParam(value = "sessionId") String sessionId) {
        
        log.info("获取会话历史: sessionId={}", sessionId);
        return ragService.getSessionHistory(sessionId);
    }
    
    /**
     * 获取用户的会话列表
     * 
     * @param userId 用户ID
     * @return 会话列表，按更新时间倒序排列
     */
    @Operation(summary = "getSessions", description = "获取用户会话列表")
    @GetMapping(value = "/rag/sessions")
    public List<ChatSession> getUserSessions(
            @RequestParam(value = "userId", defaultValue = "1") Long userId) {
        
        log.info("获取用户会话列表: userId={}", userId);
        return ragService.getUserSessions(userId);
    }
}