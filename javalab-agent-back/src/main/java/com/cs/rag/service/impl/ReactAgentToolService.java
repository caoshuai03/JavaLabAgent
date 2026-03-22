package com.cs.rag.service.impl;

import com.cs.rag.entity.ChatMessage;
import com.cs.rag.mcp.McpClientManager;
import com.cs.rag.mcp.McpToolInfo;
import com.cs.rag.service.ChatMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

@Slf4j
@Service
public class ReactAgentToolService {

    private final RagConversationSupport ragConversationSupport;
    private final ChatMessageService chatMessageService;
    /** MCP客户端管理器，负责与外部MCP工具服务器通信 */
    private final McpClientManager mcpClientManager;

    public ReactAgentToolService(RagConversationSupport ragConversationSupport,
                                 ChatMessageService chatMessageService,
                                 McpClientManager mcpClientManager) {
        this.ragConversationSupport = ragConversationSupport;
        this.chatMessageService = chatMessageService;
        this.mcpClientManager = mcpClientManager;
    }

    public ToolExecutionResult execute(String toolName, Map<String, Object> input, String sessionId, Long userId) {
        if ("current_time".equals(toolName)) {
            return currentTime(input);
        }
        if ("calculator".equals(toolName)) {
            return calculator(input);
        }
        if ("knowledge_search".equals(toolName)) {
            return knowledgeSearch(input);
        }
        if ("session_recall".equals(toolName)) {
            return sessionRecall(input, sessionId, userId);
        }

        // 尝试从MCP工具服务器执行（动态扩展的外部工具）
        McpToolInfo mcpTool = mcpClientManager.findTool(toolName);
        if (mcpTool != null) {
            return executeMcpTool(toolName, input);
        }

        return ToolExecutionResult.error(toolName, "不支持的工具");
    }

    /**
     * 根据工具名称获取工具描述，供前端展示工具简介
     * @param toolName 工具名称
     * @return 工具描述，未找到返回null
     */
    public String getToolDescription(String toolName) {
        // 内置工具描述
        Map<String, String> builtinDescriptions = Map.of(
                "current_time", "获取当前时间",
                "calculator", "计算数学表达式",
                "knowledge_search", "查询知识库",
                "session_recall", "回顾当前会话消息"
        );
        if (builtinDescriptions.containsKey(toolName)) {
            return builtinDescriptions.get(toolName);
        }
        // MCP外部工具描述
        McpToolInfo mcpTool = mcpClientManager.findTool(toolName);
        if (mcpTool != null) {
            return mcpTool.getDescription();
        }
        return null;
    }

    public List<Map<String, Object>> toolSchemas() {
        List<Map<String, Object>> tools = new ArrayList<>();
        // 内置工具
        tools.add(toolSchema("current_time", "获取当前时间", List.of("timezone"), "timezone: 时区，示例 Asia/Shanghai，可选"));
        tools.add(toolSchema("calculator", "计算数学表达式", List.of("expression"), "expression: 仅支持 + - * / ( ) 和小数"));
        tools.add(toolSchema("knowledge_search", "查询知识库", List.of("query"), "query: 检索问题"));
        tools.add(toolSchema("session_recall", "回顾当前会话消息", List.of("limit"), "limit: 回顾条数，默认5，最大20"));

        // 动态追加MCP外部工具
        List<McpToolInfo> mcpTools = mcpClientManager.getAllTools();
        for (McpToolInfo mcpTool : mcpTools) {
            Map<String, Object> schema = new HashMap<>();
            schema.put("name", mcpTool.getName());
            schema.put("description", mcpTool.getDescription());
            schema.put("source", "mcp:" + mcpTool.getServerName());
            // 从inputSchema中提取参数名列表
            if (mcpTool.getInputSchema() != null && mcpTool.getInputSchema().containsKey("properties")) {
                Object props = mcpTool.getInputSchema().get("properties");
                if (props instanceof Map<?, ?> propsMap) {
                    schema.put("params", new ArrayList<>(propsMap.keySet()));
                    // 生成参数描述
                    StringBuilder paramDesc = new StringBuilder();
                    propsMap.forEach((k, v) -> {
                        if (v instanceof Map<?, ?> propDef) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> properties = (Map<String, Object>) propDef;
                            String desc = properties.containsKey("description")
                                    ? String.valueOf(properties.get("description"))
                                    : String.valueOf(properties.getOrDefault("type", "any"));
                            paramDesc.append(k).append(": ").append(desc).append("; ");
                        }
                    });
                    schema.put("paramDesc", paramDesc.toString());
                }
            } else {
                schema.put("params", List.of());
                schema.put("paramDesc", "无参数");
            }
            tools.add(schema);
        }

        return tools;
    }

    private Map<String, Object> toolSchema(String name, String desc, List<String> params, String paramDesc) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("description", desc);
        data.put("params", params);
        data.put("paramDesc", paramDesc);
        return data;
    }

    private ToolExecutionResult currentTime(Map<String, Object> input) {
        String timezone = asString(input.get("timezone"));
        ZonedDateTime now = timezone == null || timezone.isBlank()
                ? ZonedDateTime.now()
                : ZonedDateTime.now(java.time.ZoneId.of(timezone));
        Map<String, Object> data = new HashMap<>();
        data.put("timezone", now.getZone().toString());
        data.put("iso", now.toOffsetDateTime().toString());
        data.put("formatted", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return ToolExecutionResult.success("current_time", data);
    }

    private ToolExecutionResult calculator(Map<String, Object> input) {
        String expression = asString(input.get("expression"));
        if (expression == null || expression.isBlank()) {
            return ToolExecutionResult.error("calculator", "缺少 expression 参数");
        }
        try {
            BigDecimal value = evaluate(expression);
            Map<String, Object> data = new HashMap<>();
            data.put("expression", expression);
            data.put("result", value.stripTrailingZeros().toPlainString());
            return ToolExecutionResult.success("calculator", data);
        } catch (Exception e) {
            return ToolExecutionResult.error("calculator", "表达式计算失败: " + e.getMessage());
        }
    }

    private ToolExecutionResult knowledgeSearch(Map<String, Object> input) {
        String query = asString(input.get("query"));
        if (query == null || query.isBlank()) {
            return ToolExecutionResult.error("knowledge_search", "缺少 query 参数");
        }
        List<Document> documents = ragConversationSupport.performSearch(query);
        List<Map<String, Object>> hits = new ArrayList<>();
        if (documents != null) {
            for (Document doc : documents) {
                Map<String, Object> hit = new HashMap<>();
                hit.put("score", doc.getScore());
                String text = doc.getText();
                hit.put("content", text.length() > 400 ? text.substring(0, 400) : text);
                hits.add(hit);
                if (hits.size() >= 5) {
                    break;
                }
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("query", query);
        data.put("count", hits.size());
        data.put("hits", hits);
        return ToolExecutionResult.success("knowledge_search", data);
    }

    private ToolExecutionResult sessionRecall(Map<String, Object> input, String sessionId, Long userId) {
        int limit = asInt(input.get("limit"), 5);
        limit = Math.max(1, Math.min(limit, 20));
        List<ChatMessage> messages = chatMessageService.getRecentMessages(sessionId, userId, limit);
        Collections.reverse(messages);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ChatMessage msg : messages) {
            Map<String, Object> row = new HashMap<>();
            row.put("role", msg.getRole());
            String content = msg.getContent();
            row.put("content", content.length() > 200 ? content.substring(0, 200) : content);
            row.put("createdAt", msg.getCreatedAt());
            rows.add(row);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", sessionId);
        data.put("count", rows.size());
        data.put("messages", rows);
        return ToolExecutionResult.success("session_recall", data);
    }

    /**
     * 执行MCP外部工具调用
     * 将请求转发给McpClientManager，由其与对应的MCP服务器通信
     */
    private ToolExecutionResult executeMcpTool(String toolName, Map<String, Object> input) {
        try {
//            log.info("调用MCP工具: toolName={}, input={}", toolName, input);
            McpClientManager.McpToolResult mcpResult = mcpClientManager.callTool(toolName, input);
            if (mcpResult.isSuccess()) {
                Map<String, Object> data = new HashMap<>();
                data.put("source", "mcp");
                data.put("result", mcpResult.getContent());
                return ToolExecutionResult.success(toolName, data);
            } else {
                return ToolExecutionResult.error(toolName, "MCP工具错误: " + mcpResult.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("MCP工具调用异常: toolName={}, error={}", toolName, e.getMessage(), e);
            return ToolExecutionResult.error(toolName, "MCP工具调用异常: " + e.getMessage());
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int asInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private BigDecimal evaluate(String expression) {
        String expr = expression.replaceAll("\\s+", "");
        if (!expr.matches("[0-9+\\-*/().]+")) {
            throw new IllegalArgumentException("包含非法字符");
        }
        List<String> output = new ArrayList<>();
        Stack<Character> operators = new Stack<>();
        StringBuilder number = new StringBuilder();
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (Character.isDigit(c) || c == '.') {
                number.append(c);
                continue;
            }
            if (number.length() > 0) {
                output.add(number.toString());
                number.setLength(0);
            }
            if (c == '(') {
                operators.push(c);
                continue;
            }
            if (c == ')') {
                while (!operators.isEmpty() && operators.peek() != '(') {
                    output.add(String.valueOf(operators.pop()));
                }
                if (operators.isEmpty() || operators.pop() != '(') {
                    throw new IllegalArgumentException("括号不匹配");
                }
                continue;
            }
            if (isOperator(c)) {
                if ((i == 0 || expr.charAt(i - 1) == '(') && c == '-') {
                    number.append(c);
                    continue;
                }
                while (!operators.isEmpty() && isOperator(operators.peek())
                        && precedence(operators.peek()) >= precedence(c)) {
                    output.add(String.valueOf(operators.pop()));
                }
                operators.push(c);
                continue;
            }
            throw new IllegalArgumentException("表达式格式错误");
        }
        if (number.length() > 0) {
            output.add(number.toString());
        }
        while (!operators.isEmpty()) {
            char op = operators.pop();
            if (op == '(' || op == ')') {
                throw new IllegalArgumentException("括号不匹配");
            }
            output.add(String.valueOf(op));
        }
        Stack<BigDecimal> values = new Stack<>();
        for (String token : output) {
            if (token.matches("-?\\d+(\\.\\d+)?")) {
                values.push(new BigDecimal(token));
                continue;
            }
            if (values.size() < 2) {
                throw new IllegalArgumentException("表达式格式错误");
            }
            BigDecimal b = values.pop();
            BigDecimal a = values.pop();
            values.push(apply(a, b, token.charAt(0)));
        }
        if (values.size() != 1) {
            throw new IllegalArgumentException("表达式格式错误");
        }
        return values.pop();
    }

    private boolean isOperator(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/';
    }

    private int precedence(char op) {
        return (op == '*' || op == '/') ? 2 : 1;
    }

    private BigDecimal apply(BigDecimal a, BigDecimal b, char op) {
        return switch (op) {
            case '+' -> a.add(b);
            case '-' -> a.subtract(b);
            case '*' -> a.multiply(b);
            case '/' -> {
                if (b.compareTo(BigDecimal.ZERO) == 0) {
                    throw new IllegalArgumentException("除数不能为0");
                }
                yield a.divide(b, 10, RoundingMode.HALF_UP);
            }
            default -> throw new IllegalArgumentException("不支持的运算符");
        };
    }

    public static class ToolExecutionResult {
        private final String toolName;
        private final boolean success;
        private final Object data;
        private final String errorMessage;

        private ToolExecutionResult(String toolName, boolean success, Object data, String errorMessage) {
            this.toolName = toolName;
            this.success = success;
            this.data = data;
            this.errorMessage = errorMessage;
        }

        public static ToolExecutionResult success(String toolName, Object data) {
            return new ToolExecutionResult(toolName, true, data, null);
        }

        public static ToolExecutionResult error(String toolName, String errorMessage) {
            return new ToolExecutionResult(toolName, false, null, errorMessage);
        }

        public String getToolName() {
            return toolName;
        }

        public boolean isSuccess() {
            return success;
        }

        public Object getData() {
            return data;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
