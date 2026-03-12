package com.cs.rag.service.impl;

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
import java.util.concurrent.atomic.AtomicReference;

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

        PlanOutcome planOutcome = runPlanningLoop(message, finalSessionId, userId, effectiveModel, traceId);
        if (planOutcome.finalAnswer != null && !planOutcome.finalAnswer.isBlank()) {
            return Flux.concat(
                    Flux.fromIterable(planOutcome.events),
                    streamTextAsEvents(planOutcome.finalAnswer, finalSessionId, traceId, userId, start, planOutcome.events)
            );
        }

        ChatModel targetChatModel = llmProviderRegistry.getChatModel(effectiveModel);
        ChatClient chatClient = ChatClient.builder(targetChatModel).build();
        ChatClient.ChatClientRequestSpec promptSpec = chatClient.prompt()
                .system(promptService.getChatDefaultPrompt())
                .messages(buildFinalMessages(allMessages, planOutcome.observations))
                .options(ChatOptions.builder().model(effectiveModel).build());

        AtomicReference<StringBuilder> fullResponseRef = new AtomicReference<>(new StringBuilder());
        Flux<String> streamFlux = promptSpec.stream()
                .content()
                .map(chunk -> {
                    fullResponseRef.get().append(chunk);
                    return eventJson("token", finalSessionId, traceId, Map.of("content", chunk));
                })
                .doOnComplete(() -> {
                    String aiResponse = fullResponseRef.get().toString();
                    if (!aiResponse.isEmpty()) {
                        saveMessageWithThinkingProcess(finalSessionId, userId, aiResponse, planOutcome.events);
                        log.info("ReactAgent完成: sessionId={}, traceId={}, length={}, cost={}ms",
                                finalSessionId, traceId, aiResponse.length(), System.currentTimeMillis() - start);
                    }
                })
                .concatWithValues(eventJson("final", finalSessionId, traceId, Map.of("done", true)))
                .onErrorResume(e -> Flux.just(eventJson("error", finalSessionId, traceId, Map.of("message", e.getMessage()))));

        return Flux.concat(Flux.fromIterable(planOutcome.events), streamFlux);
    }

    private PlanOutcome runPlanningLoop(String message, String sessionId, Long userId, String model, String traceId) {
        List<String> events = new ArrayList<>();
        List<String> observations = new ArrayList<>();
        Set<String> toolSignatureHistory = new LinkedHashSet<>();
        Set<String> emptyKnowledgeQueries = new LinkedHashSet<>();
        events.add(eventJson("session", sessionId, traceId, Map.of("sessionId", sessionId)));
        events.add(eventJson("status", sessionId, traceId, Map.of("stage", "thinking")));
        log.info("ReactAgent开始规划: sessionId={}, traceId={}, question={}", sessionId, traceId, message);
        int maxRounds = 3;
        for (int i = 1; i <= maxRounds; i++) {
            ActionDecision decision = decideNextAction(message, observations, model);
            log.info("ReactAgent规划结果: sessionId={}, traceId={}, round={}, action={}, tool={}",
                    sessionId, traceId, i, decision.action, decision.toolName);
            if ("final".equals(decision.action)) {
                String answer = decision.finalAnswer;
                if (answer == null || answer.isBlank()) {
                    answer = "我已完成工具分析，下面给出总结。";
                }
                events.add(eventJson("status", sessionId, traceId, Map.of("stage", "ready_to_answer", "round", i)));
                log.info("ReactAgent结束规划并直接回答: sessionId={}, traceId={}, round={}", sessionId, traceId, i);
                return new PlanOutcome(events, observations, answer);
            }
            if (!"tool".equals(decision.action)) {
                events.add(eventJson("status", sessionId, traceId, Map.of("stage", "ready_to_answer", "round", i)));
                log.info("ReactAgent规划返回非tool动作，转直接回答: sessionId={}, traceId={}, round={}, raw={}",
                        sessionId, traceId, i, decision.rawResponse);
                return new PlanOutcome(events, observations, decision.rawResponse);
            }
            String toolName = decision.toolName == null ? "" : decision.toolName;
            Map<String, Object> toolInput = decision.toolInput == null ? new HashMap<>() : decision.toolInput;
            String toolSignature = toolName + "|" + toJsonQuietly(toolInput);
            if (toolSignatureHistory.contains(toolSignature)) {
                events.add(eventJson("status", sessionId, traceId,
                        Map.of("stage", "stop_repeated_tool", "round", i, "toolName", toolName)));
                observations.add("检测到重复工具调用，停止继续调用并进入最终回答。");
                log.info("ReactAgent阻止重复工具调用: sessionId={}, traceId={}, round={}, signature={}",
                        sessionId, traceId, i, toolSignature);
                return new PlanOutcome(events, observations, null);
            }
            if ("knowledge_search".equals(toolName)) {
                String query = extractKnowledgeQuery(toolInput);
                if (query != null && emptyKnowledgeQueries.contains(query)) {
                    events.add(eventJson("status", sessionId, traceId,
                            Map.of("stage", "skip_redundant_knowledge_search", "round", i, "query", query)));
                    observations.add("同一检索词已无结果，停止重复检索并进入最终回答。");
                    log.info("ReactAgent跳过重复空结果检索: sessionId={}, traceId={}, round={}, query={}",
                            sessionId, traceId, i, query);
                    return new PlanOutcome(events, observations, null);
                }
            }
            toolSignatureHistory.add(toolSignature);
            events.add(eventJson("status", sessionId, traceId, Map.of("stage", "tool_running", "round", i, "toolName", toolName)));
            events.add(eventJson("tool_call", sessionId, traceId, Map.of("round", i, "toolName", toolName, "input", toolInput)));
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
                observations.add("工具 " + result.getToolName() + " 返回: " + toJsonQuietly(result.getData()));
                if (isKnowledgeSearchNoResult(result.getToolName(), result.getData())) {
                    String query = extractKnowledgeQuery(toolInput);
                    if (query != null && !query.isBlank()) {
                        emptyKnowledgeQueries.add(query);
                    }
                }
            } else {
                resultPayload.put("error", result.getErrorMessage());
                observations.add("工具 " + result.getToolName() + " 错误: " + result.getErrorMessage());
            }
            events.add(eventJson("tool_result", sessionId, traceId, resultPayload));
            events.add(eventJson("status", sessionId, traceId, Map.of("stage", "tool_done", "round", i)));
            log.info("ReactAgent工具结果: sessionId={}, traceId={}, round={}, tool={}, success={}, costMs={}",
                    sessionId, traceId, i, result.getToolName(), result.isSuccess(), resultPayload.get("costMs"));
        }
        events.add(eventJson("status", sessionId, traceId, Map.of("stage", "plan_round_limit_reached", "round", maxRounds)));
        log.info("ReactAgent达到最大规划轮次: sessionId={}, traceId={}, maxRounds={}", sessionId, traceId, maxRounds);
        return new PlanOutcome(events, observations, null);
    }

    private ActionDecision decideNextAction(String message, List<String> observations, String model) {
        String toolList = toJsonQuietly(reactAgentToolService.toolSchemas());
        String obs = observations.isEmpty() ? "[]"
                : observations.stream().map(s -> "- " + s).reduce((a, b) -> a + "\n" + b).orElse("[]");
        String plannerPrompt = """
                你是一个ReAct规划器。你只能输出JSON，不要输出任何额外文本。
                可用工具列表:
                %s
                用户问题:
                %s
                已有观察:
                %s
                如果还需要调用工具，输出:
                {"action":"tool","toolName":"工具名","toolInput":{"key":"value"}}
                如果可以直接回答，输出:
                {"action":"final","finalAnswer":"最终答案"}
                """.formatted(toolList, message, obs);
        try {
            ChatModel targetChatModel = llmProviderRegistry.getChatModel(model);
            ChatClient chatClient = ChatClient.builder(targetChatModel).build();
            String content = chatClient.prompt()
                    .system("你必须严格返回JSON。")
                    .user(plannerPrompt)
                    .options(ChatOptions.builder().model(model).temperature(0.1).build())
                    .call()
                    .content();
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

    private List<Message> buildFinalMessages(List<Message> allMessages, List<String> observations) {
        List<Message> messages = new ArrayList<>(allMessages);
        if (!observations.isEmpty()) {
            String joined = observations.stream().reduce((a, b) -> a + "\n" + b).orElse("");
            messages.add(new SystemMessage("以下是工具观察结果，请综合回答:\n" + joined));
        }
        return messages;
    }

    private Flux<String> streamTextAsEvents(String text, String sessionId, String traceId, Long userId, long start, List<String> toolEvents) {
        List<String> events = new ArrayList<>();
        events.add(eventJson("status", sessionId, traceId, Map.of("stage", "finalizing")));
        int chunkSize = 25;
        for (int i = 0; i < text.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, text.length());
            events.add(eventJson("token", sessionId, traceId, Map.of("content", text.substring(i, end))));
        }
        events.add(eventJson("final", sessionId, traceId, Map.of("done", true)));
        return Flux.fromIterable(events).doOnComplete(() -> {
            saveMessageWithThinkingProcess(sessionId, userId, text, toolEvents);
            log.info("ReactAgent完成: sessionId={}, traceId={}, length={}, cost={}ms",
                    sessionId, traceId, text.length(), System.currentTimeMillis() - start);
        });
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
