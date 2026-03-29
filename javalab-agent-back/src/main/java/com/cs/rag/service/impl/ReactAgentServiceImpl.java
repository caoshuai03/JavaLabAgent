package com.cs.rag.service.impl;

import com.cs.rag.constant.RagConstant;
import com.cs.rag.llm.LLMProviderRegistry;
import com.cs.rag.service.ChatMessageService;
import com.cs.rag.service.PromptService;
import com.cs.rag.service.ReactAgentService;
import com.cs.rag.service.ReactAgentToolService;
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
import reactor.core.publisher.FluxSink;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class ReactAgentServiceImpl implements ReactAgentService {

    private static final String DEFAULT_PLAN_COMPLETED_ANSWER = "Tool analysis completed. Preparing final answer.";
    private static final String DEFAULT_PLAN_ERROR_ANSWER = "Planning failed. Falling back to direct answer.";
    private static final String DEFAULT_UNKNOWN_ERROR = "Unknown error";

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
        // 1. 准备会话并拉取历史消息。
        String finalSessionId = ragConversationSupport.prepareSession(message, sessionId, userId);
        List<Message> contextMessages = ragConversationSupport.buildContext(finalSessionId, userId);
        // 2. 当前用户消息先落库，保证链路中断时也能追溯输入。
        chatMessageService.saveUserMessage(finalSessionId, userId, message);
        // 3. 先做知识检索，再决定增强后的用户消息和最终模型。
        List<Document> ragDocuments = ragConversationSupport.performSearch(message);
        String enhancedMessage = ragConversationSupport.formatMessageWithDocs(message, ragDocuments);
        String effectiveModel = ragConversationSupport.selectModel(model, ragDocuments);

        List<Message> allMessages = new ArrayList<>(contextMessages);
        allMessages.add(new UserMessage(enhancedMessage));
        return new ReactAgentChatContext(finalSessionId, userId, message, effectiveModel, allMessages);
    }

    private void handleChat(ReactAgentChatContext chatContext, String traceId, long start, FluxSink<String> sink) {
        try {
            // 规划阶段负责决定“直接回答”还是“先调用工具再回答”。
            ReactAgentPlanOutcome planOutcome = runPlanningLoop(chatContext, traceId, sink);
            if (planOutcome.finalAnswer() != null && !planOutcome.finalAnswer().isBlank()) {
                // 规划阶段已经拿到最终答案时，直接输出即可。
                completeWithDirectAnswer(chatContext, traceId, start, sink, planOutcome);
                return;
            }
            // 没有最终答案时，基于规划阶段产出的观察结果生成最后回复。
            streamFinalAnswer(chatContext, traceId, start, sink, planOutcome);
        } catch (Exception e) {
            log.error("ReactAgent failed: sessionId={}, traceId={}", chatContext.sessionId(), traceId, e);
            sink.next(eventJson("error", chatContext.sessionId(), traceId,
                    Map.of("message", e.getMessage() != null ? e.getMessage() : DEFAULT_UNKNOWN_ERROR)));
            sink.complete();
        }
    }

    private void completeWithDirectAnswer(ReactAgentChatContext chatContext,
                                          String traceId,
                                          long start,
                                          FluxSink<String> sink,
                                          ReactAgentPlanOutcome planOutcome) {
        streamTextDirectly(sink, planOutcome.finalAnswer(), chatContext.sessionId(), traceId);
        saveMessageWithThinkingProcess(chatContext.sessionId(), chatContext.userId(), planOutcome.finalAnswer(), planOutcome.events());
        sink.next(eventJson("final", chatContext.sessionId(), traceId, Map.of("done", true)));
        log.info("ReactAgent completed with direct answer: sessionId={}, traceId={}, cost={}ms",
                chatContext.sessionId(), traceId, System.currentTimeMillis() - start);
        sink.complete();
    }

    private void streamFinalAnswer(ReactAgentChatContext chatContext,
                                   String traceId,
                                   long start,
                                   FluxSink<String> sink,
                                   ReactAgentPlanOutcome planOutcome) {
        ChatModel targetChatModel = llmProviderRegistry.getChatModel(chatContext.effectiveModel());
        ChatClient chatClient = ChatClient.builder(targetChatModel).build();
        ChatClient.ChatClientRequestSpec promptSpec = chatClient.prompt()
                .system(promptService.getReactAgentFinalPrompt())
                .messages(buildFinalMessages(chatContext.allMessages(), planOutcome.observations()))
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
                        log.info("ReactAgent completed: sessionId={}, traceId={}, length={}, cost={}ms",
                                chatContext.sessionId(), traceId, aiResponse.length(), System.currentTimeMillis() - start);
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
        emit.accept(eventJson("status", chatContext.sessionId(), traceId, Map.of("stage", "thinking")));
        log.info("ReactAgent planning started: sessionId={}, traceId={}, question={}",
                chatContext.sessionId(), traceId, chatContext.originalMessage());
        log.info("ReactAgent available tools: sessionId={}, traceId={}, tools={}",
                chatContext.sessionId(), traceId, toJsonQuietly(reactAgentToolService.toolSchemas()));

        for (int i = 1; i <= RagConstant.MAX_ROUNDS; i++) {
            // 每一轮都让规划模型根据“问题 + 已有观察结果”决定下一步动作。
            ReactAgentDecision decision = decideNextAction(chatContext.originalMessage(), observations, chatContext.effectiveModel());
            log.info("ReactAgent decision: sessionId={}, traceId={}, round={}, action={}, tool={}",
                    chatContext.sessionId(), traceId, i, decision.getAction(), decision.getToolName());

            if ("final".equals(decision.getAction())) {
                String answer = decision.getFinalAnswer();
                if (answer == null || answer.isBlank()) {
                    answer = DEFAULT_PLAN_COMPLETED_ANSWER;
                }
                emit.accept(eventJson("status", chatContext.sessionId(), traceId, Map.of("stage", "ready_to_answer", "round", i)));
                return new ReactAgentPlanOutcome(events, observations, answer);
            }

            if (!"tool".equals(decision.getAction())) {
                emit.accept(eventJson("status", chatContext.sessionId(), traceId, Map.of("stage", "ready_to_answer", "round", i)));
                return new ReactAgentPlanOutcome(events, observations, decision.getRawResponse());
            }

            String toolName = decision.getToolName() == null ? "" : decision.getToolName();
            Map<String, Object> toolInput = decision.getToolInput() == null ? new HashMap<>() : decision.getToolInput();
            String toolSignature = toolName + "|" + toJsonQuietly(toolInput);
            if (toolSignatureHistory.contains(toolSignature)) {
                emit.accept(eventJson("status", chatContext.sessionId(), traceId,
                        Map.of("stage", "stop_repeated_tool", "round", i, "toolName", toolName)));
                observations.add("Repeated tool call detected. Stop planning and move to final answer.");
                return new ReactAgentPlanOutcome(events, observations, null);
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
            executeToolRound(chatContext, traceId, emit, observations, emptyKnowledgeQueries, i, toolName, toolInput);
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
                                  Map<String, Object> toolInput) {
        // 先发工具运行事件，让前端能立刻展示当前执行中的工具。
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

        long toolStart = System.currentTimeMillis();
        ReactAgentToolService.ToolExecutionResult result = reactAgentToolService.execute(toolName, toolInput, chatContext.sessionId(), chatContext.userId());

        Map<String, Object> resultPayload = new HashMap<>();
        resultPayload.put("round", round);
        resultPayload.put("toolName", result.getToolName());
        resultPayload.put("success", result.isSuccess());
        resultPayload.put("costMs", System.currentTimeMillis() - toolStart);

        if (result.isSuccess()) {
            // 成功结果会沉淀到 observations，供下一轮规划或最终回答使用。
            resultPayload.put("data", result.getData());
            if (isKnowledgeSearchNoResult(result.getToolName(), result.getData())) {
                String query = extractKnowledgeQuery(toolInput);
                if (query != null && !query.isBlank()) {
                    emptyKnowledgeQueries.add(query);
                }
                observations.add("knowledge_search returned empty result for query: " + (query == null ? "" : query));
            } else {
                observations.add("Tool " + result.getToolName() + " returned: " + toJsonQuietly(result.getData()));
            }
        } else {
            resultPayload.put("error", result.getErrorMessage());
            observations.add("Tool " + result.getToolName() + " failed: " + result.getErrorMessage());
        }

        emit.accept(eventJson("tool_result", chatContext.sessionId(), traceId, resultPayload));
        emit.accept(eventJson("status", chatContext.sessionId(), traceId, Map.of("stage", "tool_done", "round", round)));
    }

    private ReactAgentDecision decideNextAction(String message, List<String> observations, String model) {
        // 规划模型收到的是统一 JSON 协议：工具列表、用户问题、已有观察结果。
        String toolList = toJsonQuietly(reactAgentToolService.toolSchemas());
        String obs = observations.isEmpty() ? "none" : observations.stream().map(s -> "- " + s).reduce((a, b) -> a + "\n" + b).orElse("none");
        String systemPrompt = promptService.getReactAgentPrompt();
        String userPrompt = """
                Available tools:
                %s

                User question:
                %s

                Existing observations:
                %s

                Please output your decision JSON:
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
            log.debug("ReactAgent planner raw response: model={}, response={}", model, content);
            return parseDecision(content);
        } catch (Exception e) {
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
            // 去掉 ```json 包裹，保留代码块里的实际 JSON 内容。
            int first = trimmed.indexOf('\n');
            int last = trimmed.lastIndexOf("```");
            if (first > -1 && last > first) {
                trimmed = trimmed.substring(first + 1, last).trim();
            }
        }
        // 只截取最外层 JSON 对象，忽略前后解释性文本。
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
        }
        chatMessageService.saveAssistantMessage(sessionId, userId, fullContent);
    }

    private List<Message> buildFinalMessages(List<Message> allMessages, List<String> observations) {
        List<Message> messages = new ArrayList<>(allMessages);
        if (!observations.isEmpty()) {
            // 把工具观察结果以系统消息追加给模型，让最终回答阶段只关注“基于观察结果作答”。
            String joined = observations.stream().reduce((a, b) -> a + "\n" + b).orElse("");
            messages.add(new SystemMessage("Below are tool observations, please answer based on them.\n" + joined));
        }
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
