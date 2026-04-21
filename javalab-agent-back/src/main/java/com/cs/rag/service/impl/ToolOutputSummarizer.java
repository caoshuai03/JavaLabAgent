package com.cs.rag.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
public class ToolOutputSummarizer {

    private static final int MAX_LIST_ITEMS = 3;
    // 摘要文本截断长度，保留足够上下文信息
    private static final int MAX_TEXT_LENGTH = 300;

    private final ObjectMapper objectMapper;

    public ToolOutputSummarizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String summarize(String toolName, Object data) {
        if (data == null) {
            return "工具返回空结果。";
        }
        JsonNode root = objectMapper.valueToTree(data);
        // 统一走通用摘要逻辑
        return summarizeGenericTool(toolName, root);
    }

    /**
     * 通用工具输出摘要。
     * 设计原则：简洁优先，只保留核心信息，让模型自行推理细节。
     */
    private String summarizeGenericTool(String toolName, JsonNode root) {
        // 1. 查找列表字段，列出条目标题
        JsonNode items = firstArray(root, "results", "items", "list", "data", "hits", "return");
        if (items != null && items.isArray() && !items.isEmpty()) {
            StringBuilder summary = new StringBuilder(toolName)
                    .append(" 返回 ").append(items.size()).append(" 条结果。");
            for (int i = 0; i < Math.min(items.size(), MAX_LIST_ITEMS); i++) {
                JsonNode item = items.get(i);
                String title = firstNonBlank(
                        text(item, "title"), text(item, "name"),
                        text(item, "url"), text(item, "id"),
                        "结果" + (i + 1));
                summary.append("\n- ").append(abbreviate(title));
            }
            return summary.toString();
        }

        // 2. 尝试从根节点或一层嵌套中提取主要文本
        JsonNode target = root;
        if (root.has("result") && root.path("result").isObject()) {
            target = root.path("result");
        } else if (root.has("data") && root.path("data").isObject()) {
            target = root.path("data");
        }
        String primaryText = firstNonBlank(
                text(target, "result"), text(target, "content"),
                text(target, "summary"), text(target, "message"),
                text(target, "text"), text(target, "output"),
                scalarSummary(target));
        if (primaryText != null) {
            return toolName + " 返回：" + abbreviate(primaryText);
        }

        // 3. 兆底：直接截断 JSON
        return toolName + " 返回：" + abbreviate(root.toString());
    }

    private JsonNode firstArray(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode node = root.path(fieldName);
            if (node.isArray()) {
                return node;
            }
        }
        return null;
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text.trim();
    }

    private String scalarSummary(JsonNode node) {
        if (!node.isObject()) {
            return node.isValueNode() ? node.asText() : null;
        }
        List<String> parts = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
        while (iterator.hasNext() && parts.size() < MAX_LIST_ITEMS) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            JsonNode value = entry.getValue();
            if (value != null && value.isValueNode()) {
                String text = value.asText("");
                if (!text.isBlank()) {
                    parts.add(entry.getKey() + "=" + abbreviate(text));
                }
            }
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "无";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MAX_TEXT_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_TEXT_LENGTH) + "...";
    }
}
