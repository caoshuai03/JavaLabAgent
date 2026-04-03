package com.cs.rag.service.impl;

import com.cs.rag.constant.RagConstant;
import com.cs.rag.llm.LLMProviderRegistry;
import com.cs.rag.mapper.ChatSessionMapper;
import com.cs.rag.pojo.entity.ChatMessage;
import com.cs.rag.pojo.entity.ChatSession;
import com.cs.rag.service.ChatMessageService;
import com.cs.rag.service.PromptService;
import com.cs.rag.service.ReactAgentService;
import com.cs.rag.service.ReactAgentToolService;
import com.cs.rag.service.SummaryService;
import com.cs.rag.skill.SkillInfo;
import com.cs.rag.skill.SkillMatchService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class ReactAgentServiceImpl implements ReactAgentService {

    private static final String DEFAULT_PLAN_ERROR_ANSWER = "Planning failed. Falling back to direct answer.";
    private static final String DEFAULT_UNKNOWN_ERROR = "Unknown error";

    private final RagConversationSupport ragConversationSupport;
    private final LLMProviderRegistry llmProviderRegistry;
    private final PromptService promptService;
    private final ChatMessageService chatMessageService;
    private final ChatSessionMapper chatSessionMapper;
    private final ReactAgentToolService reactAgentToolService;
    private final SkillMatchService skillMatchService;
    private final SummaryService summaryService;
    private final ToolOutputSummarizer toolOutputSummarizer;
    private final ObjectMapper objectMapper;

    public ReactAgentServiceImpl(RagConversationSupport ragConversationSupport,
                                 LLMProviderRegistry llmProviderRegistry,
                                 PromptService promptService,
                                 ChatMessageService chatMessageService,
                                 ChatSessionMapper chatSessionMapper,
                                 ReactAgentToolService reactAgentToolService,
                                 SkillMatchService skillMatchService,
                                 SummaryService summaryService,
                                 ToolOutputSummarizer toolOutputSummarizer,
                                 ObjectMapper objectMapper) {
        this.ragConversationSupport = ragConversationSupport;
        this.llmProviderRegistry = llmProviderRegistry;
        this.promptService = promptService;
        this.chatMessageService = chatMessageService;
        this.chatSessionMapper = chatSessionMapper;
        this.reactAgentToolService = reactAgentToolService;
        this.skillMatchService = skillMatchService;
        this.summaryService = summaryService;
        this.toolOutputSummarizer = toolOutputSummarizer;
        this.objectMapper = objectMapper;
    }

    @Override
    public Flux<String> chat(String message, String sessionId, Long userId, String model) {
        // 把本轮请求需要的上下文先整理好，后续链路统一围绕这一份上下文工作。
        ReactAgentChatContext chatContext = prepareChatContext(message, sessionId, userId, model);
        String traceId = UUID.randomUUID().toString().replace("-", "");
        long start = System.currentTimeMillis();

        return Flux.create(sink -> {
            Thread thread = new Thread(() -> handleChat(chatContext, traceId, start, sink));
            thread.setName("react-agent-" + traceId);
            thread.setDaemon(true);
            thread.start();
        });
    }

    private ReactAgentChatContext prepareChatContext(String message, String sessionId, Long userId, String model) {
        // 1. 准备会话并拉取历史消息（包含SystemMessage历史摘要和最近对话Message）
        String finalSessionId = ragConversationSupport.prepareSession(message, sessionId, userId);
        List<Message> contextMessages = ragConversationSupport.buildContext(finalSessionId, userId);
        
        // 2. 当前用户消息先落库，保证链路中断时也能追溯输入
        chatMessageService.saveUserMessage(finalSessionId, userId, message);
        
        // 3. 先做知识检索，再决定增强后的用户消息和最终模型
        List<Document> ragDocuments = ragConversationSupport.performSearch(message);
        String enhancedMessage = ragConversationSupport.formatMessageWithDocs(message, ragDocuments);
        String effectiveModel = ragConversationSupport.selectModel(model, ragDocuments);

        // 4. 构建完整Message列表：历史摘要Message → 最近历史对话 → 当前UserMessage
        // 注意：不在此处加入 SystemMessage，规划和回答阶段会分别使用不同的 SystemMessage
        List<Message> allMessages = new ArrayList<>();
        allMessages.addAll(contextMessages);
        allMessages.add(new UserMessage(enhancedMessage));
        
        log.debug("ReAct上下文已构建: 共{}条消息 (历史 + User)", allMessages.size());
        return new ReactAgentChatContext(finalSessionId, userId, message, effectiveModel, allMessages);
    }

    private void handleChat(ReactAgentChatContext chatContext, String traceId, long start, FluxSink<String> sink) {
        try {
            // 规划阶段负责决定“直接回答”还是“先调用工具再回答”。
            ReactAgentPlanOutcome planOutcome = runPlanningLoop(chatContext, traceId, sink);
            
            // 无论规划阶段是否输出了 finalAnswer，都使用 Final prompt 重新生成最终回答
            // 这样可以确保：
            // 1. 规划阶段使用 ReAct prompt（要求 JSON 输出）
            // 2. 回答阶段使用 Final prompt（要求自然语言，综合上下文）
            // 3. 避免 SystemMessage 冲突，提升回答质量
            streamFinalAnswer(chatContext, traceId, start, sink, planOutcome);
        } catch (Exception e) {
            log.error("ReactAgent failed: sessionId={}, traceId={}", chatContext.sessionId(), traceId, e);
            sink.next(eventJson("error", chatContext.sessionId(), traceId,
                    Map.of("message", e.getMessage() != null ? e.getMessage() : DEFAULT_UNKNOWN_ERROR)));
            sink.complete();
        }
    }

    private void streamFinalAnswer(ReactAgentChatContext chatContext,
                                   String traceId,
                                   long start,
                                   FluxSink<String> sink,
                                   ReactAgentPlanOutcome planOutcome) {
        // 基于完整上下文Message列表生成最终回答（已包含所有工具调用的ToolMessage）
        List<Message> finalMessages = buildFinalMessages(chatContext.allMessages());
        log.info("[最终回答] 上下文共{}条消息", finalMessages.size());
        
        ChatModel targetChatModel = llmProviderRegistry.getChatModel(chatContext.effectiveModel());
        ChatClient chatClient = ChatClient.builder(targetChatModel).build();
        ChatClient.ChatClientRequestSpec promptSpec = chatClient.prompt()
                .messages(finalMessages)
                .options(ChatOptions.builder().model(chatContext.effectiveModel()).build());

        StringBuilder fullResponse = new StringBuilder();
        promptSpec.stream()
                .content()
                .doOnNext(chunk -> {
                    fullResponse.append(chunk);
                    sink.next(eventJson("token", chatContext.sessionId(), traceId, Map.of("content", chunk)));
                })
                .doOnComplete(() -> {
                    String aiResponse = fullResponse.toString();
                    if (!aiResponse.isEmpty()) {
                        saveMessageWithThinkingProcess(chatContext.sessionId(), chatContext.userId(), aiResponse, planOutcome.events());
                        checkAndUpdateSummaryAsync(chatContext.sessionId(), chatContext.userId());
                        log.info("[流程完成] 回答长度={}, 总耗时={}ms", aiResponse.length(), System.currentTimeMillis() - start);
                    }
                    sink.next(eventJson("final", chatContext.sessionId(), traceId, Map.of("done", true)));
                    sink.complete();
                })
                .doOnError(e -> {
                    sink.next(eventJson("error", chatContext.sessionId(), traceId, Map.of("message", e.getMessage())));
                    sink.complete();
                })
                .subscribe();
    }

    private ReactAgentPlanOutcome runPlanningLoop(ReactAgentChatContext chatContext, String traceId, FluxSink<String> sink) {
        // events 用于前端展示 thinking_process，observations 用于后续规划和最终回答。
        List<String> events = new ArrayList<>();
        List<String> observations = new ArrayList<>();
        // 避免模型反复调用相同工具造成死循环。
        Set<String> toolSignatureHistory = new LinkedHashSet<>();
        // 记录已经返回空结果的知识库查询，避免重复空检索。
        Set<String> emptyKnowledgeQueries = new LinkedHashSet<>();

        java.util.function.Consumer<String> emit = event -> {
            sink.next(event);
            events.add(event);
        };

        emit.accept(eventJson("session", chatContext.sessionId(), traceId, Map.of("sessionId", chatContext.sessionId())));
        
        // 匹配 Skills 并发送事件通知前端
        List<SkillInfo> matchedSkills = skillMatchService.matchSkills(chatContext.originalMessage());
        if (!matchedSkills.isEmpty()) {
            List<Map<String, Object>> skillEvents = new ArrayList<>();
            for (SkillInfo skill : matchedSkills) {
                Map<String, Object> skillEvent = new LinkedHashMap<>();
                skillEvent.put("name", skill.getMetadata().getName());
                skillEvent.put("description", skill.getMetadata().getDescription());
                skillEvent.put("triggerKeywords", skill.getMetadata().getTriggerKeywords());
                skillEvents.add(skillEvent);
            }
            emit.accept(eventJson("skill_loaded", chatContext.sessionId(), traceId, 
                Map.of("skills", skillEvents, "count", matchedSkills.size())));
            log.info("ReactAgent loaded {} skills for session: {}", matchedSkills.size(), chatContext.sessionId());
        }
        
        emit.accept(eventJson("status", chatContext.sessionId(), traceId, Map.of("stage", "thinking")));
        log.info("[规划开始] sessionId={}, question={}", chatContext.sessionId(), chatContext.originalMessage());

        for (int i = 1; i <= RagConstant.MAX_ROUNDS; i++) {
            // 每一轮都让规划模型根据“问题 + 已有观察结果”决定下一步动作。
            ReactAgentDecision decision = decideNextAction(chatContext, matchedSkills, observations, i);
            log.info("[Round {}] action={}, tool={}", i, decision.getAction(), decision.getToolName());

            if ("final".equals(decision.getAction())) {
                String answer = decision.getFinalAnswer();
                emit.accept(eventJson("status", chatContext.sessionId(), traceId, Map.of("stage", "ready_to_answer", "round", i)));
                return new ReactAgentPlanOutcome(events, observations, answer);
            }

            if (!"tool".equals(decision.getAction())) {
                emit.accept(eventJson("status", chatContext.sessionId(), traceId, Map.of("stage", "ready_to_answer", "round", i)));
                return new ReactAgentPlanOutcome(events, observations, decision.getRawResponse());
            }

            String toolName = decision.getToolName() == null ? "" : decision.getToolName();
            Map<String, Object> toolInput = decision.getToolInput() == null ? new HashMap<>() : decision.getToolInput();
            String toolSignature = normalizeToolSignature(toolName, toolInput);
            if (toolSignatureHistory.contains(toolSignature)) {
                emit.accept(eventJson("status", chatContext.sessionId(), traceId,
                        Map.of("stage", "stop_repeated_tool", "round", i, "toolName", toolName)));
                observations.add(buildRepeatedToolObservation(toolName, toolInput));
                continue;
            }

            if ("knowledge_search".equals(toolName)) {
                String query = extractKnowledgeQuery(toolInput);
                if (query != null && emptyKnowledgeQueries.contains(query)) {
                    emit.accept(eventJson("status", chatContext.sessionId(), traceId,
                            Map.of("stage", "skip_redundant_knowledge_search", "round", i, "query", query)));
                    observations.add("The same knowledge search query already returned empty result.");
                    return new ReactAgentPlanOutcome(events, observations, null);
                }
            }

            toolSignatureHistory.add(toolSignature);
            executeToolRound(chatContext, traceId, emit, observations, emptyKnowledgeQueries, i, toolName, toolInput, decision);
        }

        emit.accept(eventJson("status", chatContext.sessionId(), traceId,
                Map.of("stage", "plan_round_limit_reached", "round", RagConstant.MAX_ROUNDS)));
        return new ReactAgentPlanOutcome(events, observations, null);
    }

    private void executeToolRound(ReactAgentChatContext chatContext,
                                  String traceId,
                                  java.util.function.Consumer<String> emit,
                                  List<String> observations,
                                  Set<String> emptyKnowledgeQueries,
                                  int round,
                                  String toolName,
                                  Map<String, Object> toolInput,
                                  ReactAgentDecision decision) {
        emit.accept(eventJson("status", chatContext.sessionId(), traceId, Map.of("stage", "tool_running", "round", round, "toolName", toolName)));

        Map<String, Object> toolCallPayload = new HashMap<>();
        toolCallPayload.put("round", round);
        toolCallPayload.put("toolName", toolName);
        toolCallPayload.put("input", toolInput);
        String toolDesc = reactAgentToolService.getToolDescription(toolName);
        if (toolDesc != null) {
            toolCallPayload.put("description", toolDesc);
        }
        emit.accept(eventJson("tool_call", chatContext.sessionId(), traceId, toolCallPayload));

        // 记录工具决策轨迹，但不要作为 SystemMessage 注入，避免抬高其指令优先级。
        String decisionRecord = String.format("Agent决策：调用工具 %s，输入参数：%s", toolName, toJsonQuietly(toolInput));
        chatContext.allMessages().add(new AssistantMessage(decisionRecord));
        log.debug("[工具调用前] 已记录决策: {}", decisionRecord);

        long toolStart = System.currentTimeMillis();
        ReactAgentToolService.ToolExecutionResult result = reactAgentToolService.execute(toolName, toolInput, chatContext.sessionId(), chatContext.userId());
        long costMs = System.currentTimeMillis() - toolStart;

        Map<String, Object> resultPayload = new HashMap<>();
        resultPayload.put("round", round);
        resultPayload.put("toolName", result.getToolName());
        resultPayload.put("success", result.isSuccess());
        resultPayload.put("costMs", costMs);

        String toolResponse;
        if (result.isSuccess()) {
            resultPayload.put("data", result.getData());
            if (isKnowledgeSearchNoResult(result.getToolName(), result.getData())) {
                String query = extractKnowledgeQuery(toolInput);
                if (query != null && !query.isBlank()) {
                    emptyKnowledgeQueries.add(query);
                }
                toolResponse = "knowledge_search returned empty result for query: " + (query == null ? "" : query);
            } else {
                String summarizedOutput = toolOutputSummarizer.summarize(result.getToolName(), result.getData());
                resultPayload.put("summary", summarizedOutput);
                toolResponse = "Tool " + result.getToolName() + " returned summary: " + summarizedOutput;
            }
            observations.add(toolResponse);
            log.info("[工具执行成功] tool={}, cost={}ms", toolName, costMs);
        } else {
            resultPayload.put("error", result.getErrorMessage());
            toolResponse = "Tool " + result.getToolName() + " failed: " + result.getErrorMessage();
            observations.add(toolResponse);
            log.warn("[工具执行失败] tool={}, error={}", toolName, result.getErrorMessage());
        }

        // 工具调用后：使用UserMessage记录工具输出结果（避免OpenAI API的tool角色格式限制）
        String toolResultMsg = String.format("工具 %s 执行结果：%s", toolName, toolResponse);
        chatContext.allMessages().add(new UserMessage(toolResultMsg));
        log.debug("[工具调用后] 已记录执行结果, 当前上下文共{}条消息", chatContext.allMessages().size());

        emit.accept(eventJson("tool_result", chatContext.sessionId(), traceId, resultPayload));
        emit.accept(eventJson("status", chatContext.sessionId(), traceId, Map.of("stage", "tool_done", "round", round)));
    }

    private ReactAgentDecision decideNextAction(ReactAgentChatContext chatContext,
                                                List<SkillInfo> matchedSkills,
                                                List<String> observations,
                                                int round) {
        String toolList = toJsonQuietly(reactAgentToolService.toolSchemas());
        
        // 构建当前轮次的决策提示：ReAct SystemMessage → 历史上下文 → 工具调用记录 → 决策 UserMessage
        List<Message> decisionMessages = new ArrayList<>();
        decisionMessages.add(new SystemMessage(promptService.getReactAgentPrompt()));
        decisionMessages.addAll(chatContext.allMessages());

        String structuredPrompt = promptService.buildReactUserPrompt(
                toolList,
                chatContext.originalMessage(),
                formatObservationsForPrompt(observations),
                matchedSkills,
                round
        );
        decisionMessages.add(new UserMessage(structuredPrompt));
        
        logRecentDecisionMessages(round, decisionMessages);
        
        try {
            ChatModel targetChatModel = llmProviderRegistry.getChatModel(chatContext.effectiveModel());
            ChatClient chatClient = ChatClient.builder(targetChatModel).build();
            String content = chatClient.prompt()
                    .messages(decisionMessages)
                    .options(ChatOptions.builder().model(chatContext.effectiveModel()).temperature(0.1).build())
                    .call()
                    .content();
            log.info("[LLM决策响应] 长度={}, 完整内容={}", content.length(), content);
            return parseDecision(content);
        } catch (Exception e) {
            log.error("[决策失败] {}", e.getMessage());
            ReactAgentDecision decision = new ReactAgentDecision();
            decision.setAction("final");
            decision.setFinalAnswer(DEFAULT_PLAN_ERROR_ANSWER);
            return decision;
        }
    }

    private ReactAgentDecision parseDecision(String content) {
        ReactAgentDecision decision = new ReactAgentDecision();
        decision.setRawResponse(content);
        try {
            // 兼容 markdown 代码块、自然语言包裹 JSON 等输出形式，只抽取真正的 JSON 指令部分。
            String json = extractJson(content);
            JsonNode node = objectMapper.readTree(json);
            decision.setAction(node.path("action").asText("final"));
            decision.setToolName(node.path("toolName").asText(null));
            JsonNode toolInputNode = node.path("toolInput");
            if (toolInputNode.isObject()) {
                decision.setToolInput(objectMapper.convertValue(toolInputNode, new TypeReference<>() {
                }));
            }
            decision.setFinalAnswer(node.path("finalAnswer").asText(null));
        } catch (Exception e) {
            // 解析失败时兜底为最终回答，避免因为格式波动导致整轮对话中断。
            decision.setAction("final");
            decision.setFinalAnswer(content);
        }
        return decision;
    }

    private String extractJson(String text) {
        if (text == null) {
            return "{}";
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            // 去掉 ```json 包裹，保留代码块里的实际 JSON 内容
            int first = trimmed.indexOf('\n');
            int last = trimmed.lastIndexOf("```");
            if (first > -1 && last > first) {
                trimmed = trimmed.substring(first + 1, last).trim();
            }
        }
        
        // 防御性检查：如果模型输出了数组格式（违反规则），只取第一个元素
        if (trimmed.startsWith("[")) {
            log.warn("[格式违规] 模型输出了数组格式，只取第一个元素。原始输出：{}", trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed);
            int firstObjStart = trimmed.indexOf('{');
            if (firstObjStart > 0) {
                // 找到第一个对象的结束位置（简单计数花括号）
                int depth = 0;
                for (int i = firstObjStart; i < trimmed.length(); i++) {
                    char c = trimmed.charAt(i);
                    if (c == '{') depth++;
                    else if (c == '}') {
                        depth--;
                        if (depth == 0) {
                            return trimmed.substring(firstObjStart, i + 1);
                        }
                    }
                }
            }
        }
        
        // 只截取最外层 JSON 对象，忽略前后解释性文本
        int left = trimmed.indexOf('{');
        int right = trimmed.lastIndexOf('}');
        if (left >= 0 && right > left) {
            return trimmed.substring(left, right + 1);
        }
        return trimmed;
    }

    private void streamTextDirectly(FluxSink<String> sink, String text, String sessionId, String traceId) {
        // 最终回答统一拆成小块输出，保持前端与工具阶段一致的流式体验。
        sink.next(eventJson("status", sessionId, traceId, Map.of("stage", "finalizing")));
        int chunkSize = 25;
        for (int i = 0; i < text.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, text.length());
            sink.next(eventJson("token", sessionId, traceId, Map.of("content", text.substring(i, end))));
        }
    }

    private void saveMessageWithThinkingProcess(String sessionId, Long userId, String content, List<String> events) {
        String fullContent = content;
        if (events != null && !events.isEmpty()) {
            // 通过注释标记包裹 thinking_process，既不影响原消息存储结构，也方便后续回放。
            String eventsJson = "[" + String.join(",", events) + "]";
            fullContent = "<!-- thinking_process_start -->" + eventsJson + "<!-- thinking_process_end -->\n" + content;
            log.info("保存 ReactAgent assistant 消息: sessionId={}, withThinkingProcess=true, eventCount={}, contentLength={}",
                    sessionId, events.size(), content != null ? content.length() : 0);
        } else {
            log.info("保存 ReactAgent assistant 消息: sessionId={}, withThinkingProcess=false, contentLength={}",
                    sessionId, content != null ? content.length() : 0);
        }
        chatMessageService.saveAssistantMessage(sessionId, userId, fullContent);
    }

    private void checkAndUpdateSummaryAsync(String sessionId, Long userId) {
        CompletableFuture.runAsync(() -> {
            try {
                ChatSession session = chatSessionMapper.selectByIdAndUserId(sessionId, userId);
                if (session == null) {
                    log.warn("跳过 ReactAgent 摘要刷新: sessionId={}, userId={}, reason=session_not_found", sessionId, userId);
                    return;
                }

                long totalMessages = ragConversationSupport.countMessagesBySession(sessionId);
                if (!shouldRefreshSummary(totalMessages)) {
                    log.info("跳过 ReactAgent 摘要刷新: sessionId={}, totalMessages={}, reason=refresh_condition_not_met",
                            sessionId, totalMessages);
                    return;
                }

                log.info("Trigger ReactAgent rolling summary update: sessionId={}, totalMessages={}", sessionId, totalMessages);
                int limit = calculateSummaryRefreshLimit(totalMessages);
                List<ChatMessage> recentMessages = chatMessageService.getRecentMessages(sessionId, userId, limit);
                Collections.reverse(recentMessages);
                log.info("ReactAgent 摘要刷新准备完成: sessionId={}, limit={}, recentMessages={}, oldSummaryLength={}",
                        sessionId,
                        limit,
                        recentMessages.size(),
                        session.getSummary() != null ? session.getSummary().length() : 0);

                String newSummary = summaryService.refreshSummary(session.getSummary(), recentMessages);
                chatSessionMapper.updateSummary(sessionId, newSummary);
                log.info("ReactAgent rolling summary update completed: sessionId={}, newSummaryLength={}",
                        sessionId,
                        newSummary != null ? newSummary.length() : 0);
            } catch (Exception e) {
                log.error("ReactAgent rolling summary update failed: sessionId={}", sessionId, e);
            }
        });
    }

    private boolean shouldRefreshSummary(long totalMessages) {
        return totalMessages > 0 && totalMessages % RagConstant.MEMORY_SIZE <= 1;
    }

    private int calculateSummaryRefreshLimit(long totalMessages) {
        return RagConstant.MEMORY_SIZE + (int) (totalMessages % RagConstant.MEMORY_SIZE);
    }

    private List<Message> buildFinalMessages(List<Message> allMessages) {
        // 构建最终回答上下文：Final SystemMessage → 历史上下文 → 工具调用记录
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(promptService.getReactAgentFinalPrompt()));
        messages.addAll(allMessages);
        return messages;
    }

    private String eventJson(String eventType, String sessionId, String traceId, Map<String, Object> payload) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("eventType", eventType);
        data.put("sessionId", sessionId);
        data.put("traceId", traceId);
        data.put("ts", Instant.now().toEpochMilli());
        data.put("payload", payload);
        return toJsonQuietly(data);
    }

    private String toJsonQuietly(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            // 事件序列化失败时返回最小错误事件，避免把异常继续抛到流式链路里。
            return "{\"eventType\":\"error\",\"payload\":{\"message\":\"json serialization failed\"}}";
        }
    }

    private boolean isKnowledgeSearchNoResult(String toolName, Object data) {
        if (!"knowledge_search".equals(toolName)) {
            return false;
        }
        if (!(data instanceof Map<?, ?> map)) {
            return false;
        }
        Object countObj = map.get("count");
        if (countObj instanceof Number number) {
            return number.intValue() == 0;
        }
        if (countObj != null) {
            try {
                return Integer.parseInt(String.valueOf(countObj)) == 0;
            } catch (Exception ignored) {
                return false;
            }
        }
        Object hitsObj = map.get("hits");
        return hitsObj instanceof List<?> list && list.isEmpty();
    }

    private String extractKnowledgeQuery(Map<String, Object> toolInput) {
        if (toolInput == null) {
            return null;
        }
        Object query = toolInput.get("query");
        return query == null ? null : String.valueOf(query).trim();
    }

    private String normalizeToolSignature(String toolName, Map<String, Object> toolInput) {
        String normalizedToolName = normalizeText(toolName);
        return switch (normalizedToolName) {
            case "knowledge_search" -> normalizedToolName + "|query=" + normalizeText(toolInput.get("query"));
            case "maps_geo" -> normalizedToolName + "|address=" + normalizeText(toolInput.get("address"))
                    + "|city=" + normalizeText(toolInput.get("city"));
            case "maps_regeocode" -> normalizedToolName + "|location=" + normalizeText(toolInput.get("location"));
            default -> normalizedToolName + "|" + canonicalizeJson(toolInput);
        };
    }

    private String canonicalizeJson(Object value) {
        try {
            JsonNode node = objectMapper.valueToTree(value);
            return canonicalizeNode(node).toString();
        } catch (Exception e) {
            return toJsonQuietly(value);
        }
    }

    private JsonNode canonicalizeNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return objectMapper.getNodeFactory().nullNode();
        }
        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            List<String> fieldNames = new ArrayList<>();
            node.fieldNames().forEachRemaining(fieldNames::add);
            Collections.sort(fieldNames);
            for (String fieldName : fieldNames) {
                sorted.set(fieldName, canonicalizeNode(node.get(fieldName)));
            }
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            for (JsonNode item : node) {
                arrayNode.add(canonicalizeNode(item));
            }
            return arrayNode;
        }
        return node;
    }

    private String buildRepeatedToolObservation(String toolName, Map<String, Object> toolInput) {
        return "工具 " + toolName + " 已用相同或等价参数执行过，参数="
                + canonicalizeJson(toolInput)
                + "。不要重复调用该工具；请直接利用已有结果回答，或改用其他工具处理未解决的子问题。";
    }

    private String normalizeText(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value)
                .trim()
                .toLowerCase()
                .replaceAll("\\s+", "");
    }

    private String formatObservationsForPrompt(List<String> observations) {
        if (observations == null || observations.isEmpty()) {
            return "无";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < observations.size(); i++) {
            builder.append(i + 1)
                    .append(". ")
                    .append(observations.get(i));
            if (i < observations.size() - 1) {
                builder.append("\n");
            }
        }
        return builder.toString();
    }

    private void logRecentDecisionMessages(int round, List<Message> decisionMessages) {
        List<String> recentSystemMessages = collectRecentMessages(decisionMessages, SystemMessage.class, 2);
        List<String> recentUserMessages = collectRecentMessages(decisionMessages, UserMessage.class, 2);
        log.info("[Round {}] 决策上下文摘要: totalMessages={}, recentSystemMessages={}, recentUserMessages={}",
                round,
                decisionMessages.size(),
                recentSystemMessages.size(),
                recentUserMessages.size());
        for (int i = 0; i < recentSystemMessages.size(); i++) {
            log.info("[Round {}][System {}] {}", round, i + 1, recentSystemMessages.get(i));
        }
        for (int i = 0; i < recentUserMessages.size(); i++) {
            log.info("[Round {}][User {}] {}", round, i + 1, recentUserMessages.get(i));
        }
    }

    private List<String> collectRecentMessages(List<Message> messages, Class<? extends Message> targetType, int limit) {
        List<String> collected = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0 && collected.size() < limit; i--) {
            Message message = messages.get(i);
            if (targetType.isInstance(message)) {
                collected.add(0, abbreviateLogContent(message.getContent()));
            }
        }
        return collected;
    }

    private String abbreviateLogContent(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        int maxLength = 400;
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    /**
     * ReactAgent 对话上下文。
     * 把一次请求里会反复用到的关键信息打包起来，避免主流程方法参数越来越多。
     */
    private record ReactAgentChatContext(String sessionId, Long userId, String originalMessage, String effectiveModel, List<Message> allMessages) {
    }

    /**
     * 单轮规划执行结果。
     * events 用于前端回放，observations 用于最终回答，finalAnswer 表示模型已经决定结束本轮流程。
     */
    private record ReactAgentPlanOutcome(List<String> events, List<String> observations, String finalAnswer) {
    }

    /**
     * LLM 规划阶段输出的决策对象。
     * 用来承接“继续调用工具”或“直接给最终答案”两类结果。
     */
    private static class ReactAgentDecision {
        private String action;
        private String toolName;
        private Map<String, Object> toolInput;
        private String finalAnswer;
        private String rawResponse;

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getToolName() {
            return toolName;
        }

        public void setToolName(String toolName) {
            this.toolName = toolName;
        }

        public Map<String, Object> getToolInput() {
            return toolInput;
        }

        public void setToolInput(Map<String, Object> toolInput) {
            this.toolInput = toolInput;
        }

        public String getFinalAnswer() {
            return finalAnswer;
        }

        public void setFinalAnswer(String finalAnswer) {
            this.finalAnswer = finalAnswer;
        }

        public String getRawResponse() {
            return rawResponse;
        }

        public void setRawResponse(String rawResponse) {
            this.rawResponse = rawResponse;
        }
    }
}
