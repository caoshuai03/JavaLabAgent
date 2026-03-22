package com.cs.rag.service.impl;

import com.cs.rag.constant.RagConstant;
import com.cs.rag.llm.LLMProviderRegistry;
import com.cs.rag.service.ChatMessageService;
import com.cs.rag.service.PromptService;
import com.cs.rag.service.ReactAgentService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import reactor.core.publisher.FluxSink;

@Slf4j
@Service
public class ReactAgentServiceImpl implements ReactAgentService {

    private final RagConversationSupport ragConversationSupport;
    private final LLMProviderRegistry llmProviderRegistry;
    private final PromptService promptService;
    private final ChatMessageService chatMessageService;
    private final ReactAgentToolService reactAgentToolService;
    private final ObjectMapper objectMapper;

    public ReactAgentServiceImpl(RagConversationSupport ragConversationSupport,
                                 LLMProviderRegistry llmProviderRegistry,
                                 PromptService promptService,
                                 ChatMessageService chatMessageService,
                                 ReactAgentToolService reactAgentToolService,
                                 ObjectMapper objectMapper) {
        this.ragConversationSupport = ragConversationSupport;
        this.llmProviderRegistry = llmProviderRegistry;
        this.promptService = promptService;
        this.chatMessageService = chatMessageService;
        this.reactAgentToolService = reactAgentToolService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Flux<String> chat(String message, String sessionId, Long userId, String model) {
        // 预处理阶段（同步，快速完成）
        String finalSessionId = ragConversationSupport.prepareSession(message, sessionId, userId);
        List<Message> contextMessages = ragConversationSupport.buildContext(finalSessionId, userId);
        chatMessageService.saveUserMessage(finalSessionId, userId, message);
        List<Document> ragDocuments = ragConversationSupport.performSearch(message);
        String enhancedMessage = ragConversationSupport.formatMessageWithDocs(message, ragDocuments);
        String effectiveModel = ragConversationSupport.selectModel(model, ragDocuments);

        List<Message> allMessages = new ArrayList<>(contextMessages);
        allMessages.add(new UserMessage(enhancedMessage));

        String traceId = UUID.randomUUID().toString().replace("-", "");
        long start = System.currentTimeMillis();

        // 使用 Flux.create 实现实时流式推送，工具调用事件逐个发送到前端
        return Flux.<String>create(sink -> {
            Thread thread = new Thread(() -> {
                try {
                    // 在子线程中执行规划循环，每个事件实时推送
                    PlanOutcome planOutcome = runPlanningLoop(message, finalSessionId, userId, effectiveModel, traceId, sink);

                    if (planOutcome.finalAnswer != null && !planOutcome.finalAnswer.isBlank()) {
                        // 规划器直接给出了最终答案，流式推送 token 事件
                        streamTextDirectly(sink, planOutcome.finalAnswer, finalSessionId, traceId);
                        // 保存消息并发送 final 事件
                        saveMessageWithThinkingProcess(finalSessionId, userId, planOutcome.finalAnswer, planOutcome.events);
                        sink.next(eventJson("final", finalSessionId, traceId, Map.of("done", true)));
                        log.info("ReactAgent完成(直接回答): sessionId={}, traceId={}, cost={}ms",
                                finalSessionId, traceId, System.currentTimeMillis() - start);
                        sink.complete();
                        return;
                    }

                    // 需要 LLM 流式生成最终回答，切换到 Flux 订阅模式
                    ChatModel targetChatModel = llmProviderRegistry.getChatModel(effectiveModel);
                    ChatClient chatClient = ChatClient.builder(targetChatModel).build();
                    // ReactAgent最终回答使用通用prompt，不限定Java领域，综合工具观察结果回答
                    String reactFinalSystemPrompt = buildReactFinalSystemPrompt();
                    ChatClient.ChatClientRequestSpec promptSpec = chatClient.prompt()
                            .system(reactFinalSystemPrompt)
                            .messages(buildFinalMessages(allMessages, planOutcome.observations))
                            .options(ChatOptions.builder().model(effectiveModel).build());

                    // 收集完整回复用于保存
                    StringBuilder fullResponse = new StringBuilder();
                    // 订阅 LLM 流式输出，逐 token 推送
                    promptSpec.stream()
                            .content()
                            .doOnNext(chunk -> {
                                fullResponse.append(chunk);
                                sink.next(eventJson("token", finalSessionId, traceId, Map.of("content", chunk)));
                            })
                            .doOnComplete(() -> {
                                String aiResponse = fullResponse.toString();
                                if (!aiResponse.isEmpty()) {
                                    saveMessageWithThinkingProcess(finalSessionId, userId, aiResponse, planOutcome.events);
                                    log.info("ReactAgent完成: sessionId={}, traceId={}, length={}, cost={}ms",
                                            finalSessionId, traceId, aiResponse.length(), System.currentTimeMillis() - start);
                                }
                                sink.next(eventJson("final", finalSessionId, traceId, Map.of("done", true)));
                                sink.complete();
                            })
                            .doOnError(e -> {
                                sink.next(eventJson("error", finalSessionId, traceId, Map.of("message", e.getMessage())));
                                sink.complete();
                            })
                            .subscribe();
                } catch (Exception e) {
                    log.error("ReactAgent异常: sessionId={}, traceId={}", finalSessionId, traceId, e);
                    sink.next(eventJson("error", finalSessionId, traceId, Map.of("message", e.getMessage() != null ? e.getMessage() : "未知错误")));
                    sink.complete();
                }
            });
            thread.setName("react-agent-" + traceId);
            thread.setDaemon(true);
            thread.start();
        });
    }

    /**
     * 执行规划循环，每个事件通过 sink 实时推送到前端（逐个显示工具调用）
     *
     * @param sink SSE 流的 sink，用于实时推送事件
     * @return 规划结果（events 列表仅用于持久化，推送已通过 sink 完成）
     */
    private PlanOutcome runPlanningLoop(String message, String sessionId, Long userId, String model, String traceId, FluxSink<String> sink) {
        // events 列表仅用于最终持久化保存，实时推送通过 sink 完成
        List<String> events = new ArrayList<>();
        List<String> observations = new ArrayList<>();
        Set<String> toolSignatureHistory = new LinkedHashSet<>();
        Set<String> emptyKnowledgeQueries = new LinkedHashSet<>();

        // 辅助方法：同时推送到 sink 和记录到 events 列表
        java.util.function.Consumer<String> emit = (event) -> {
            sink.next(event);
            events.add(event);
        };

        emit.accept(eventJson("session", sessionId, traceId, Map.of("sessionId", sessionId)));
        emit.accept(eventJson("status", sessionId, traceId, Map.of("stage", "thinking")));
        log.info("ReactAgent开始规划: sessionId={}, traceId={}, question={}", sessionId, traceId, message);
        log.info("ReactAgent当前可调用工具列表: sessionId={}, traceId={}, tools={}",
                sessionId, traceId, toJsonQuietly(reactAgentToolService.toolSchemas()));
        int maxRounds = RagConstant.MAX_ROUNDS;
        for (int i = 1; i <= maxRounds; i++) {
            ActionDecision decision = decideNextAction(message, observations, model);
            log.info("ReactAgent规划结果: sessionId={}, traceId={}, round={}, action={}, tool={}",
                    sessionId, traceId, i, decision.action, decision.toolName);
            if ("final".equals(decision.action)) {
                String answer = decision.finalAnswer;
                if (answer == null || answer.isBlank()) {
                    answer = "我已完成工具分析，下面给出总结。";
                }
                emit.accept(eventJson("status", sessionId, traceId, Map.of("stage", "ready_to_answer", "round", i)));
                log.info("ReactAgent结束规划并直接回答: sessionId={}, traceId={}, round={}", sessionId, traceId, i);
                return new PlanOutcome(events, observations, answer);
            }
            if (!"tool".equals(decision.action)) {
                emit.accept(eventJson("status", sessionId, traceId, Map.of("stage", "ready_to_answer", "round", i)));
                log.info("ReactAgent规划返回非tool动作，转直接回答: sessionId={}, traceId={}, round={}, raw={}",
                        sessionId, traceId, i, decision.rawResponse);
                return new PlanOutcome(events, observations, decision.rawResponse);
            }
            String toolName = decision.toolName == null ? "" : decision.toolName;
            Map<String, Object> toolInput = decision.toolInput == null ? new HashMap<>() : decision.toolInput;
            String toolSignature = toolName + "|" + toJsonQuietly(toolInput);
            if (toolSignatureHistory.contains(toolSignature)) {
                emit.accept(eventJson("status", sessionId, traceId,
                        Map.of("stage", "stop_repeated_tool", "round", i, "toolName", toolName)));
                observations.add("检测到重复工具调用，停止继续调用并进入最终回答。");
                log.info("ReactAgent阻止重复工具调用: sessionId={}, traceId={}, round={}, signature={}",
                        sessionId, traceId, i, toolSignature);
                return new PlanOutcome(events, observations, null);
            }
            if ("knowledge_search".equals(toolName)) {
                String query = extractKnowledgeQuery(toolInput);
                if (query != null && emptyKnowledgeQueries.contains(query)) {
                    emit.accept(eventJson("status", sessionId, traceId,
                            Map.of("stage", "skip_redundant_knowledge_search", "round", i, "query", query)));
                    observations.add("同一检索词已无结果，停止重复检索并进入最终回答。");
                    log.info("ReactAgent跳过重复空结果检索: sessionId={}, traceId={}, round={}, query={}",
                            sessionId, traceId, i, query);
                    return new PlanOutcome(events, observations, null);
                }
            }
            toolSignatureHistory.add(toolSignature);
            // 实时推送 tool_running 状态，前端立即显示该工具调用（带 loading 动画）
            emit.accept(eventJson("status", sessionId, traceId, Map.of("stage", "tool_running", "round", i, "toolName", toolName)));
            // 构建 tool_call 事件，包含工具描述供前端展示工具简介
            Map<String, Object> toolCallPayload = new HashMap<>();
            toolCallPayload.put("round", i);
            toolCallPayload.put("toolName", toolName);
            toolCallPayload.put("input", toolInput);
            String toolDesc = reactAgentToolService.getToolDescription(toolName);
            if (toolDesc != null) {
                toolCallPayload.put("description", toolDesc);
            }
            emit.accept(eventJson("tool_call", sessionId, traceId, toolCallPayload));
            log.info("ReactAgent调用工具: sessionId={}, traceId={}, round={}, tool={}, input={}",
                    sessionId, traceId, i, toolName, toJsonQuietly(toolInput));
            long toolStart = System.currentTimeMillis();
            ReactAgentToolService.ToolExecutionResult result = reactAgentToolService.execute(toolName, toolInput, sessionId, userId);
            Map<String, Object> resultPayload = new LinkedHashMap<>();
            resultPayload.put("round", i);
            resultPayload.put("toolName", result.getToolName());
            resultPayload.put("success", result.isSuccess());
            resultPayload.put("costMs", System.currentTimeMillis() - toolStart);
            if (result.isSuccess()) {
                resultPayload.put("data", result.getData());
                if (isKnowledgeSearchNoResult(result.getToolName(), result.getData())) {
                    // 知识库无结果时，给出更明确的引导信息，帮助planner选择其他工具
                    String query = extractKnowledgeQuery(toolInput);
                    if (query != null && !query.isBlank()) {
                        emptyKnowledgeQueries.add(query);
                    }
                    observations.add("工具 knowledge_search 对查询\"" + (query != null ? query : "") +
                            "\"返回0条结果，知识库中无相关内容。请勿再次调用knowledge_search，应尝试其他可用工具（如联网搜索类MCP工具）或直接基于通用知识回答。");
                } else {
                    observations.add("工具 " + result.getToolName() + " 返回: " + toJsonQuietly(result.getData()));
                }
            } else {
                resultPayload.put("error", result.getErrorMessage());
                observations.add("工具 " + result.getToolName() + " 错误: " + result.getErrorMessage());
            }
            // 实时推送工具结果，前端立即更新该工具调用状态（去掉 loading）
            emit.accept(eventJson("tool_result", sessionId, traceId, resultPayload));
            emit.accept(eventJson("status", sessionId, traceId, Map.of("stage", "tool_done", "round", i)));
//            log.info("ReactAgent工具结果: sessionId={}, traceId={}, round={}, tool={}, success={}, costMs={}",
//                    sessionId, traceId, i, result.getToolName(), result.isSuccess(), resultPayload.get("costMs"));
        }
        emit.accept(eventJson("status", sessionId, traceId, Map.of("stage", "plan_round_limit_reached", "round", maxRounds)));
        log.info("ReactAgent达到最大规划轮次: sessionId={}, traceId={}, maxRounds={}", sessionId, traceId, maxRounds);
        return new PlanOutcome(events, observations, null);
    }

    private ActionDecision decideNextAction(String message, List<String> observations, String model) {
        String toolList = toJsonQuietly(reactAgentToolService.toolSchemas());
        String obs = observations.isEmpty() ? "无"
                : observations.stream().map(s -> "- " + s).reduce((a, b) -> a + "\n" + b).orElse("无");

        // 使用外部化的 ReAct 系统提示词（react-system-prompt.md）
        String systemPrompt = promptService.getReactAgentPrompt();

        // 用户消息：提供可用工具列表、用户问题、已有观察
        String userPrompt = """
                可用工具列表:
                %s

                用户问题:
                %s

                已有观察:
                %s

                请输出你的决策JSON:
                """.formatted(toolList, message, obs);
        try {
            ChatModel targetChatModel = llmProviderRegistry.getChatModel(model);
            ChatClient chatClient = ChatClient.builder(targetChatModel).build();
            String content = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .options(ChatOptions.builder().model(model).temperature(0.1).build())
                    .call()
                    .content();
            // 记录LLM原始响应，便于排查规划异常（如不调用工具直接回答）
            log.debug("ReactAgent规划器LLM原始响应: model={}, response={}", model, content);
            return parseDecision(content);
        } catch (Exception e) {
            ActionDecision decision = new ActionDecision();
            decision.action = "final";
            decision.finalAnswer = "规划阶段异常，切换为直接回答。";
            return decision;
        }
    }

    private ActionDecision parseDecision(String content) {
        ActionDecision decision = new ActionDecision();
        decision.rawResponse = content;
        try {
            String json = extractJson(content);
            JsonNode node = objectMapper.readTree(json);
            decision.action = node.path("action").asText("final");
            decision.toolName = node.path("toolName").asText(null);
            JsonNode toolInputNode = node.path("toolInput");
            if (toolInputNode.isObject()) {
                decision.toolInput = objectMapper.convertValue(toolInputNode, new TypeReference<>() {
                });
            }
            decision.finalAnswer = node.path("finalAnswer").asText(null);
        } catch (Exception e) {
            decision.action = "final";
            decision.finalAnswer = content;
        }
        return decision;
    }

    /**
     * 构建 ReactAgent 最终回答阶段的系统提示词
     * 比 chat-default.md 更通用，不限定 Java 领域，允许综合工具结果回答各类问题
     */
    private String buildReactFinalSystemPrompt() {
        return """
                你是一个智能助手。你需要根据用户的问题和整个对话上下文（包括工具调用及其结果）提供全面、准确的回答。

                ## 处理工具调用上下文
                当对话中包含工具调用及其结果时，你需要：
                1. 仔细分析工具调用的目的和返回结果
                2. 将工具返回的信息与用户的原始问题结合起来
                3. 基于综合信息提供完整的回答
                4. 如果工具返回了联网搜索结果，请整理并呈现关键信息

                ## 回答要求
                - 全面性：考虑整个对话历史和工具调用结果
                - 准确性：基于工具返回的真实信息进行回答
                - 清晰性：表达清晰，逻辑连贯，适当使用 Markdown 格式
                - 实用性：提供有实际帮助的信息
                - 如果涉及知识库检索到的内容，加上前缀"【根据知识库】："
                - 如果涉及联网搜索到的内容，加上前缀"【根据联网搜索】："
                - 如果是基于通用知识回答，加上前缀"【根据通用知识】："
                """;
    }

    private List<Message> buildFinalMessages(List<Message> allMessages, List<String> observations) {
        List<Message> messages = new ArrayList<>(allMessages);
        if (!observations.isEmpty()) {
            String joined = observations.stream().reduce((a, b) -> a + "\n" + b).orElse("");
            messages.add(new SystemMessage("以下是工具观察结果，请综合回答:\n" + joined));
        }
        return messages;
    }

    /**
     * 将文本分块作为 token 事件直接推送到 sink（用于规划器直接回答的场景）
     */
    private void streamTextDirectly(FluxSink<String> sink, String text, String sessionId, String traceId) {
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
            // 将事件列表拼接成 JSON 数组字符串
            String eventsJson = "[" + String.join(",", events) + "]";
            // 添加特殊标记，用于前端解析
            fullContent = "<!-- thinking_process_start -->" + eventsJson + "<!-- thinking_process_end -->\n" + content;
        }
        chatMessageService.saveAssistantMessage(sessionId, userId, fullContent);
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

    private String extractJson(String text) {
        if (text == null) {
            return "{}";
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int first = trimmed.indexOf('\n');
            int last = trimmed.lastIndexOf("```");
            if (first > -1 && last > first) {
                trimmed = trimmed.substring(first + 1, last).trim();
            }
        }
        int left = trimmed.indexOf('{');
        int right = trimmed.lastIndexOf('}');
        if (left >= 0 && right > left) {
            return trimmed.substring(left, right + 1);
        }
        return trimmed;
    }

    private String toJsonQuietly(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"eventType\":\"error\",\"payload\":{\"message\":\"json序列化失败\"}}";
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

    private static class ActionDecision {
        String action;
        String toolName;
        Map<String, Object> toolInput;
        String finalAnswer;
        String rawResponse;
    }

    private record PlanOutcome(List<String> events, List<String> observations, String finalAnswer) {
    }
}
