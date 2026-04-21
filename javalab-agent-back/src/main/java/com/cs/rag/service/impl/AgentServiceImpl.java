package com.cs.rag.service.impl;

import com.cs.rag.constant.RagConstant;
import com.cs.rag.pojo.entity.SkillInfo;
import com.cs.rag.service.AgentService;
import com.cs.rag.service.ChatMessageService;
import com.cs.rag.service.LLMProviderService;
import com.cs.rag.service.PromptService;
import com.cs.rag.service.SkillService;
import com.cs.rag.service.ToolService;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class AgentServiceImpl implements AgentService {

    private static final String DEFAULT_PLAN_ERROR_ANSWER = "Planning failed. Falling back to direct answer.";
    private static final String DEFAULT_UNKNOWN_ERROR = "Unknown error";
    private static final int MAX_TOTAL_WEB_READ_CALLS = 3;
    private static final int MAX_SUCCESSFUL_WEB_READ_CALLS = 2;
    // 同一工具累计失败次数上限，超过后不再重试
    private static final int MAX_TOOL_FAILURES = 2;

    private final RagConversationSupport ragConversationSupport;
    private final LLMProviderService llmProviderService;
    private final PromptService promptService;
    private final ChatMessageService chatMessageService;
    private final ToolService toolService;
    private final SkillService skillService;
    private final ToolOutputSummarizer toolOutputSummarizer;
    private final ObjectMapper objectMapper;

    public AgentServiceImpl(RagConversationSupport ragConversationSupport,
                            LLMProviderService llmProviderService,
                            PromptService promptService,
                            ChatMessageService chatMessageService,
                            ToolService toolService,
                            SkillService skillService,
                            ToolOutputSummarizer toolOutputSummarizer,
                            ObjectMapper objectMapper) {
        this.ragConversationSupport = ragConversationSupport;
        this.llmProviderService = llmProviderService;
        this.promptService = promptService;
        this.chatMessageService = chatMessageService;
        this.toolService = toolService;
        this.skillService = skillService;
        this.toolOutputSummarizer = toolOutputSummarizer;
        this.objectMapper = objectMapper;
    }

    @Override
    public Flux<String> chat(String message, String sessionId, Long userId, String model) {
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
        String finalSessionId = ragConversationSupport.prepareSession(message, sessionId, userId);
        List<Message> contextMessages = ragConversationSupport.buildContext(finalSessionId, userId);

        chatMessageService.saveUserMessage(finalSessionId, userId, message);

        List<Document> ragDocuments = ragConversationSupport.performSearch(message);
        String enhancedMessage = ragConversationSupport.formatMessageWithDocs(message, ragDocuments);
        String effectiveModel = ragConversationSupport.selectModel(model, ragDocuments);

        List<Message> allMessages = new ArrayList<>();
        allMessages.addAll(contextMessages);
        allMessages.add(new UserMessage(enhancedMessage));

        log.debug("ReAct context prepared: totalMessages={}", allMessages.size());
        return new ReactAgentChatContext(finalSessionId, userId, message, effectiveModel, allMessages);
    }

    private void handleChat(ReactAgentChatContext chatContext, String traceId, long start, FluxSink<String> sink) {
        try {
            ReactAgentPlanOutcome planOutcome = runPlanningLoop(chatContext, traceId, sink);
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
        List<Message> finalMessages = buildFinalMessages(chatContext.allMessages());
        log.info("[Final Answer] totalMessages={}", finalMessages.size());

        ChatModel targetChatModel = llmProviderService.getChatModel(chatContext.effectiveModel());
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
                        ragConversationSupport.refreshSummaryAsync(chatContext.sessionId(), chatContext.userId(), "ReactAgent");
                        log.info("[Flow Completed] answerLength={}, cost={}ms", aiResponse.length(), System.currentTimeMillis() - start);
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
        List<String> events = new ArrayList<>();
        List<String> observations = new ArrayList<>();
        // 重复工具检测，主要拦截 web_search 等工具的重复调用
        Set<String> toolSignatureHistory = new HashSet<>();
        // 按工具名统计失败次数，防止换 query 绕过签名去重
        Map<String, Integer> toolFailureCounts = new HashMap<>();
        int totalWebReadCalls = 0;
        int successfulWebReadCalls = 0;

        java.util.function.Consumer<String> emit = event -> {
            sink.next(event);
            events.add(event);
        };

        emit.accept(eventJson("session", chatContext.sessionId(), traceId, Map.of("sessionId", chatContext.sessionId())));

        List<SkillInfo> matchedSkills = skillService.matchSkills(chatContext.originalMessage());
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

        for (int i = 1; i <= RagConstant.MAX_ROUNDS; i++) {
            ReactAgentDecision decision = decideNextAction(chatContext, matchedSkills, observations, i);

            if ("final".equals(decision.getAction())) {
                emit.accept(eventJson("status", chatContext.sessionId(), traceId, Map.of("stage", "ready_to_answer", "round", i)));
                return new ReactAgentPlanOutcome(events, observations, decision.getFinalAnswer());
            }

            if (!"tool".equals(decision.getAction())) {
                emit.accept(eventJson("status", chatContext.sessionId(), traceId, Map.of("stage", "ready_to_answer", "round", i)));
                return new ReactAgentPlanOutcome(events, observations, decision.getRawResponse());
            }

            String toolName = decision.getToolName() == null ? "" : decision.getToolName();
            Map<String, Object> toolInput = decision.getToolInput() == null ? new HashMap<>() : decision.getToolInput();

            // 重复工具检测：签名相同则跳过
            String toolSignature = buildToolSignature(toolName, toolInput);
            if (toolSignatureHistory.contains(toolSignature)) {
                emit.accept(eventJson("status", chatContext.sessionId(), traceId,
                        Map.of("stage", "stop_repeated_tool", "round", i, "toolName", toolName)));
                observations.add(buildRepeatedToolObservation(toolName, toolInput));
                continue;
            }

            // 同一工具累计失败次数超限，停止重试（解决换 query 绕过签名去重的问题）
            int failures = toolFailureCounts.getOrDefault(toolName, 0);
            if (failures >= MAX_TOOL_FAILURES) {
                emit.accept(eventJson("status", chatContext.sessionId(), traceId,
                        Map.of("stage", "stop_tool_max_failures", "round", i, "toolName", toolName, "failures", failures)));
                observations.add("工具 " + toolName + " 已连续失败 " + failures + " 次，请基于已有信息作答或使用其他工具。");
                continue;
            }

            if ("web_read".equals(toolName)
                    && (totalWebReadCalls >= MAX_TOTAL_WEB_READ_CALLS || successfulWebReadCalls >= MAX_SUCCESSFUL_WEB_READ_CALLS)) {
                emit.accept(eventJson("status", chatContext.sessionId(), traceId,
                        Map.of("stage", "stop_excessive_web_read", "round", i, "toolName", toolName,
                                "totalWebReadCalls", totalWebReadCalls, "successfulWebReadCalls", successfulWebReadCalls)));
                observations.add("已经有足够的网页观察，请直接基于现有结果作答，不要继续猜测新的网页链接。");
                return new ReactAgentPlanOutcome(events, observations, null);
            }

            toolSignatureHistory.add(toolSignature);
            ToolService.ToolExecutionResult result = executeToolRound(
                    chatContext,
                    traceId,
                    emit,
                    observations,
                    i,
                    toolName,
                    toolInput
            );

            // 统计工具失败次数
            if (!result.isSuccess()) {
                toolFailureCounts.merge(toolName, 1, Integer::sum);
            }
            if ("web_read".equals(toolName)) {
                totalWebReadCalls++;
                if (result.isSuccess()) {
                    successfulWebReadCalls++;
                }
            }
        }

        emit.accept(eventJson("status", chatContext.sessionId(), traceId,
                Map.of("stage", "plan_round_limit_reached", "round", RagConstant.MAX_ROUNDS)));
        return new ReactAgentPlanOutcome(events, observations, null);
    }

    private ToolService.ToolExecutionResult executeToolRound(ReactAgentChatContext chatContext,
                                                             String traceId,
                                                             java.util.function.Consumer<String> emit,
                                                             List<String> observations,
                                                             int round,
                                                             String toolName,
                                                             Map<String, Object> toolInput) {
        emit.accept(eventJson("status", chatContext.sessionId(), traceId, Map.of("stage", "tool_running", "round", round, "toolName", toolName)));

        Map<String, Object> toolCallPayload = new HashMap<>();
        toolCallPayload.put("round", round);
        toolCallPayload.put("toolName", toolName);
        toolCallPayload.put("input", toolInput);
        String toolDesc = toolService.getToolDescription(toolName);
        if (toolDesc != null) {
            toolCallPayload.put("description", toolDesc);
        }

        String decisionRecord = String.format("Agent decision: call tool %s with input %s", toolName, toJsonQuietly(toolInput));
        chatContext.allMessages().add(new AssistantMessage(decisionRecord));
        log.debug("[Before Tool Call] {}", decisionRecord);

        long toolStart = System.currentTimeMillis();
        ToolService.ToolExecutionResult result = toolService.execute(toolName, toolInput, chatContext.sessionId(), chatContext.userId());
        long costMs = System.currentTimeMillis() - toolStart;

        Map<String, Object> resultPayload = new HashMap<>();
        resultPayload.put("round", round);
        resultPayload.put("toolName", result.getToolName());
        resultPayload.put("success", result.isSuccess());
        resultPayload.put("costMs", costMs);
        if (result.getCostMs() > 0) {
            resultPayload.put("executorCostMs", result.getCostMs());
        }
        if (result.getSource() != null && !result.getSource().isBlank()) {
            resultPayload.put("source", result.getSource());
        }
        if (result.getMetadata() != null && !result.getMetadata().isEmpty()) {
            resultPayload.put("metadata", result.getMetadata());
        }

        String toolResponse;
        if (result.isSuccess()) {
            resultPayload.put("data", result.getData());
            // 统一走摘要逻辑，不再区分 knowledge_search
            String summarizedOutput = result.getSummary();
            if (summarizedOutput == null || summarizedOutput.isBlank()) {
                summarizedOutput = toolOutputSummarizer.summarize(result.getToolName(), result.getData());
            }
            resultPayload.put("summary", summarizedOutput);
            toolResponse = summarizedOutput;
            observations.add(toolResponse);
            log.info("[Tool Success] tool={}, cost={}ms", toolName, costMs);
        } else {
            resultPayload.put("error", result.getErrorMessage());
            toolResponse = "Tool " + result.getToolName() + " failed: " + result.getErrorMessage();
            observations.add(toolResponse);
            log.warn("[Tool Failed] tool={}, error={}", toolName, result.getErrorMessage());
        }

        String toolResultMsg = String.format("Tool %s execution result: %s", toolName, toolResponse);
        chatContext.allMessages().add(new UserMessage(toolResultMsg));
        log.debug("[After Tool Call] totalMessages={}", chatContext.allMessages().size());

        emit.accept(eventJson("tool_call", chatContext.sessionId(), traceId, toolCallPayload));
        emit.accept(eventJson("tool_result", chatContext.sessionId(), traceId, resultPayload));
        emit.accept(eventJson("status", chatContext.sessionId(), traceId, Map.of(
                "stage", "tool_done",
                "round", round,
                "toolName", toolName,
                "success", result.isSuccess(),
                "visibleToUser", true
        )));
        return result;
    }

    private ReactAgentDecision decideNextAction(ReactAgentChatContext chatContext,
                                                List<SkillInfo> matchedSkills,
                                                List<String> observations,
                                                int round) {
        String toolList = toJsonQuietly(toolService.toolSchemas());

        List<Message> decisionMessages = new ArrayList<>();
        decisionMessages.add(new SystemMessage(promptService.getReactPlanSystemPrompt()));
        decisionMessages.addAll(chatContext.allMessages());

        String structuredPrompt = promptService.buildReactPlanUserPrompt(
                toolList,
                chatContext.originalMessage(),
                formatObservationsForPrompt(observations),
                matchedSkills,
                round
        );
        decisionMessages.add(new UserMessage(structuredPrompt));

        logRecentDecisionMessages(round, decisionMessages);

        try {
            ChatModel targetChatModel = llmProviderService.getChatModel(chatContext.effectiveModel());
            ChatClient chatClient = ChatClient.builder(targetChatModel).build();
            String content = chatClient.prompt()
                    .messages(decisionMessages)
                    .options(ChatOptions.builder().model(chatContext.effectiveModel()).temperature(0.1).build())
                    .call()
                    .content();
            log.info("[Round {}] [LLM Decision Response] length={}, content={}", round, content == null ? 0 : content.length(), content);
            return parseDecision(content);
        } catch (Exception e) {
            log.error("[Decision Failed] {}", e.getMessage());
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
            String json = extractJson(content);
            JsonNode node = objectMapper.readTree(json);
            String rawAction = node.path("action").asText("final");
            String toolName = node.path("toolName").asText(null);
            decision.setAction(rawAction);
            decision.setToolName(toolName);
            JsonNode toolInputNode = node.path("toolInput");
            if (toolInputNode.isObject()) {
                decision.setToolInput(objectMapper.convertValue(toolInputNode, new TypeReference<>() {
                }));
            }
            decision.setFinalAnswer(node.path("finalAnswer").asText(null));
            normalizeToolDecision(decision);
        } catch (Exception e) {
            decision.setAction("final");
            decision.setFinalAnswer(content);
        }
        return decision;
    }

    private void normalizeToolDecision(ReactAgentDecision decision) {
        if (decision == null) {
            return;
        }
        String action = decision.getAction();
        String toolName = decision.getToolName();
        if (toolName != null && !toolName.isBlank()) {
            return;
        }
        if (action == null || action.isBlank() || "tool".equals(action) || "final".equals(action)) {
            return;
        }
        if (toolService.getToolDescription(action) == null) {
            return;
        }
        decision.setAction("tool");
        decision.setToolName(action);
        log.warn("[Decision Normalized] normalized action '{}' to tool call", action);
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

        if (trimmed.startsWith("[")) {
            log.warn("[Format Violation] model returned array, extracting first object. raw={}",
                    trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed);
            int firstObjStart = trimmed.indexOf('{');
            if (firstObjStart > 0) {
                int depth = 0;
                for (int i = firstObjStart; i < trimmed.length(); i++) {
                    char c = trimmed.charAt(i);
                    if (c == '{') {
                        depth++;
                    } else if (c == '}') {
                        depth--;
                        if (depth == 0) {
                            return trimmed.substring(firstObjStart, i + 1);
                        }
                    }
                }
            }
        }

        int left = trimmed.indexOf('{');
        int right = trimmed.lastIndexOf('}');
        if (left >= 0 && right > left) {
            return trimmed.substring(left, right + 1);
        }
        return trimmed;
    }

    private void saveMessageWithThinkingProcess(String sessionId, Long userId, String content, List<String> events) {
        String fullContent = content;
        if (events != null && !events.isEmpty()) {
            String eventsJson = "[" + String.join(",", events) + "]";
            fullContent = "<!-- thinking_process_start -->" + eventsJson + "<!-- thinking_process_end -->\n" + content;
            log.info("Save ReactAgent assistant message: sessionId={}, withThinkingProcess=true, eventCount={}, contentLength={}",
                    sessionId, events.size(), content != null ? content.length() : 0);
        } else {
            log.info("Save ReactAgent assistant message: sessionId={}, withThinkingProcess=false, contentLength={}",
                    sessionId, content != null ? content.length() : 0);
        }
        chatMessageService.saveAssistantMessage(sessionId, userId, fullContent);
    }

    private List<Message> buildFinalMessages(List<Message> allMessages) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(promptService.getReactAnswerSystemPrompt()));
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
            return "{\"eventType\":\"error\",\"payload\":{\"message\":\"json serialization failed\"}}";
        }
    }


    /**
     * 构建工具调用签名，用于重复调用检测。
     * web_search 按 query 去重，其他工具按完整输入去重。
     */
    private String buildToolSignature(String toolName, Map<String, Object> toolInput) {
        if ("web_search".equals(toolName)) {
            Object query = toolInput.get("query");
            return "web_search|" + (query == null ? "" : String.valueOf(query).trim().toLowerCase());
        }
        return toolName + "|" + canonicalizeJson(toolInput);
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
        return "Tool " + toolName + " already ran with equivalent input " + canonicalizeJson(toolInput)
                + ". Do not call it again; answer with existing result or choose another tool.";
    }


    private String formatObservationsForPrompt(List<String> observations) {
        if (observations == null || observations.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < observations.size(); i++) {
            builder.append(i + 1).append(". ").append(observations.get(i));
            if (i < observations.size() - 1) {
                builder.append("\n");
            }
        }
        return builder.toString();
    }

    private void logRecentDecisionMessages(int round, List<Message> decisionMessages) {
        List<String> recentSystemMessages = collectRecentMessages(decisionMessages, SystemMessage.class, 2);
        List<String> recentUserMessages = collectRecentMessages(decisionMessages, UserMessage.class, 2);
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

    private record ReactAgentChatContext(String sessionId, Long userId, String originalMessage, String effectiveModel,
                                         List<Message> allMessages) {
    }

    private record ReactAgentPlanOutcome(List<String> events, List<String> observations, String finalAnswer) {
    }

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

