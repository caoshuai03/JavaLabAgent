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
    private static final int MAX_TEXT_LENGTH = 100;

    private final ObjectMapper objectMapper;

    public ToolOutputSummarizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String summarize(String toolName, Object data) {
        if (data == null) {
            return "工具返回空结果。";
        }
        JsonNode root = objectMapper.valueToTree(data);
        return switch (toolName) {
            case "knowledge_search" -> summarizeKnowledgeSearch(root);
            default -> summarizeGenericTool(toolName, root);
        };
    }

    private String summarizeKnowledgeSearch(JsonNode root) {
        int count = root.path("count").asInt(root.path("hits").isArray() ? root.path("hits").size() : 0);
        JsonNode hits = root.path("hits");
        if (!hits.isArray() || hits.isEmpty()) {
            return "知识检索结果：未找到相关文档。";
        }

        StringBuilder summary = new StringBuilder("知识检索结果：找到 ")
                .append(count)
                .append(" 条相关文档。");
        for (int i = 0; i < Math.min(hits.size(), MAX_LIST_ITEMS); i++) {
            JsonNode hit = hits.get(i);
            String title = firstNonBlank(
                    text(hit, "title"),
                    text(hit.path("metadata"), "title"),
                    text(hit.path("metadata"), "fileName"),
                    text(hit.path("metadata"), "source"),
                    "文档" + (i + 1)
            );
            String score = firstNonBlank(
                    scalar(hit, "score"),
                    scalar(hit.path("metadata"), "score"),
                    scalar(hit.path("metadata"), "relevanceScore"),
                    null
            );
            String snippet = abbreviate(firstNonBlank(
                    text(hit, "snippet"),
                    text(hit, "summary"),
                    text(hit, "content"),
                    text(hit, "text"),
                    text(hit.path("metadata"), "summary"),
                    "无摘要"
            ));
            summary.append("\n- ")
                    .append(title);
            if (score != null) {
                summary.append("（score=").append(score).append("）");
            }
            summary.append("：").append(snippet);
        }
        return summary.toString();
    }

    private String summarizeGenericTool(String toolName, JsonNode root) {
        JsonNode representative = representativeNode(root);
        String coordinateSummary = summarizeCoordinates(representative);
        if (coordinateSummary != null) {
            return coordinateSummary;
        }

        JsonNode items = firstArray(root, "return", "results", "items", "list", "data", "hits");
        if (items != null && items.isArray() && !items.isEmpty()) {
            StringBuilder summary = new StringBuilder(toolName)
                    .append(" 返回 ")
                    .append(items.size())
                    .append(" 条结果。");
            for (int i = 0; i < Math.min(items.size(), MAX_LIST_ITEMS); i++) {
                JsonNode item = items.get(i);
                String title = firstNonBlank(
                        text(item, "title"),
                        text(item, "name"),
                        text(item, "id"),
                        "结果" + (i + 1)
                );
                String snippet = abbreviate(firstNonBlank(
                        text(item, "summary"),
                        text(item, "snippet"),
                        text(item, "description"),
                        text(item, "content"),
                        text(item, "text"),
                        scalarSummary(item)
                ));
                summary.append("\n- ").append(title).append("：").append(snippet);
            }
            return summary.toString();
        }

        String primaryText = firstNonBlank(
                text(representative, "result"),
                text(representative, "content"),
                text(representative, "summary"),
                text(representative, "message"),
                text(representative, "text"),
                locationSummary(representative),
                scalarSummary(representative),
                scalarSummary(root)
        );
        return toolName + " 返回：" + abbreviate(primaryText);
    }

    private String summarizeCoordinates(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return null;
        }
        String lat = firstNonBlank(scalar(root, "lat"), scalar(root, "latitude"), scalar(root.path("location"), "lat"), scalar(root.path("location"), "latitude"), null);
        String lon = firstNonBlank(scalar(root, "lon"), scalar(root, "lng"), scalar(root, "longitude"), scalar(root.path("location"), "lon"), scalar(root.path("location"), "lng"), scalar(root.path("location"), "longitude"), null);
        String locationText = text(root, "location");
        if ((lat == null || lon == null) && locationText != null && locationText.contains(",")) {
            String[] parts = locationText.split(",", 2);
            if (parts.length == 2) {
                lon = firstNonBlank(lon, parts[0].trim(), null);
                lat = firstNonBlank(lat, parts[1].trim(), null);
            }
        }
        if (lat == null || lon == null) {
            JsonNode coordinates = root.path("coordinates");
            lat = firstNonBlank(lat, scalar(coordinates, "lat"), scalar(coordinates, "latitude"), null);
            lon = firstNonBlank(lon, scalar(coordinates, "lon"), scalar(coordinates, "lng"), scalar(coordinates, "longitude"), null);
        }
        if (lat == null || lon == null) {
            return null;
        }
        String location = firstNonBlank(
                locationSummary(root),
                text(root, "location"),
                text(root, "address"),
                text(root, "name"),
                text(root.path("location"), "name"),
                text(root.path("result"), "formatted_address"),
                "目标位置"
        );
        return "地图查询结果：" + location + "，坐标 (" + lon + ", " + lat + ")。";
    }

    private JsonNode representativeNode(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return root;
        }
        JsonNode firstReturn = firstArray(root, "return");
        if (firstReturn != null && firstReturn.isArray() && !firstReturn.isEmpty()) {
            JsonNode item = firstReturn.get(0);
            if (item != null && !item.isNull()) {
                return item;
            }
        }
        JsonNode result = root.path("result");
        if (result.isObject()) {
            return result;
        }
        JsonNode data = root.path("data");
        if (data.isObject()) {
            return data;
        }
        return root;
    }

    private String locationSummary(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String district = text(node, "district");
        String city = text(node, "city");
        String province = text(node, "province");
        String country = text(node, "country");
        StringBuilder builder = new StringBuilder();
        appendLocationPart(builder, country);
        appendLocationPart(builder, province);
        appendLocationPart(builder, city);
        appendLocationPart(builder, district);
        if (builder.isEmpty()) {
            return null;
        }
        return builder.toString();
    }

    private void appendLocationPart(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!builder.isEmpty() && builder.toString().endsWith(value)) {
            return;
        }
        builder.append(value.trim());
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

    private String scalar(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.isValueNode()) {
            String text = value.asText(null);
            return text == null || text.isBlank() ? null : text.trim();
        }
        return null;
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
