package com.cs.rag.controller;

import com.cs.rag.common.ApplicationConstant;
import com.cs.rag.pojo.dto.ChatRequestDTO;
import com.cs.rag.pojo.dto.DeleteSessionRequestDTO;
import com.cs.rag.pojo.dto.HistoryRequestDTO;
import com.cs.rag.pojo.dto.SessionListRequestDTO;
import com.cs.rag.pojo.entity.ChatMessage;
import com.cs.rag.pojo.entity.ChatSession;
import com.cs.rag.pojo.vo.ChatMessageVO;
import com.cs.rag.pojo.vo.ChatSessionVO;
import com.cs.rag.service.AgentService;
import com.cs.rag.service.AskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI 对话控制器。
 */
@Tag(name = "AiRagController", description = "AI 对话接口")
@Slf4j
@RestController
@RequestMapping(ApplicationConstant.API_VERSION + "/ai")
public class AiRagController {

    @Autowired
    private AskService askService;

    @Autowired
    private AgentService agentService;

    @Operation(summary = "chat", description = "RAG 对话接口")
    @PostMapping(value = "/rag", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ChatRequestDTO request) {
        String message = request.getMessage() != null ? request.getMessage() : "你好";
        String sessionId = request.getSessionId();
        Long userId = request.getUserId() != null ? request.getUserId() : 1L;
        String model = request.getModel();

        log.info("RAG 对话请求: message={}, sessionId={}, userId={}, model={}",
                message, sessionId, userId, model);
        return askService.chat(message, sessionId, userId, model);
    }

    @Operation(summary = "agentChat", description = "Agent 对话接口")
    @PostMapping(value = "/react-agent", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> agentChat(@RequestBody ChatRequestDTO request) {
        String message = request.getMessage() != null ? request.getMessage() : "你好";
        String sessionId = request.getSessionId();
        Long userId = request.getUserId() != null ? request.getUserId() : 1L;
        String model = request.getModel();

        log.info("Agent 对话请求: message={}, sessionId={}, userId={}, model={}",
                message, sessionId, userId, model);
        return agentService.chat(message, sessionId, userId, model);
    }

    @Operation(summary = "getHistory", description = "获取会话历史消息")
    @PostMapping("/rag/history")
    public List<ChatMessageVO> getHistory(@RequestBody HistoryRequestDTO request) {
        List<ChatMessage> messages = askService.getHistory(request.getSessionId(), request.getUserId());
        return messages.stream()
                .map(msg -> ChatMessageVO.builder()
                        .id(msg.getId())
                        .sessionId(msg.getSessionId())
                        .role(msg.getRole())
                        .content(msg.getContent())
                        .createdAt(msg.getCreatedAt())
                        .build())
                .toList();
    }

    @Operation(summary = "getSessions", description = "获取用户会话列表")
    @PostMapping("/rag/sessions")
    public List<ChatSessionVO> listSessions(@RequestBody SessionListRequestDTO request) {
        List<ChatSession> sessions = askService.listSessions(request.getUserId());
        return sessions.stream()
                .map(session -> ChatSessionVO.builder()
                        .id(session.getId())
                        .title(session.getTitle())
                        .createdAt(session.getCreatedAt())
                        .updatedAt(session.getUpdatedAt())
                        .build())
                .toList();
    }

    @Operation(summary = "deleteSession", description = "删除会话，支持批量删除")
    @PostMapping("/rag/sessions/delete")
    public boolean delete(@RequestBody DeleteSessionRequestDTO request) {
        Long userId = request.getUserId();
        if (request.getSessionIds() != null && !request.getSessionIds().isEmpty()) {
            return askService.deleteBatch(request.getSessionIds(), userId);
        }
        String sessionId = request.getSessionId();
        if (sessionId != null && !sessionId.isEmpty()) {
            return askService.delete(sessionId, userId);
        }
        log.warn("删除会话失败，sessionId 和 sessionIds 都为空");
        return false;
    }
}

