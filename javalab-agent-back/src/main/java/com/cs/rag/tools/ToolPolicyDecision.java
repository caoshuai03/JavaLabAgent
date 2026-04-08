package com.cs.rag.tools;

import java.util.Collections;
import java.util.Map;

/**
 * 工具策略判断结果。
 * 用于在执行前返回是否放行，以及归一化后的参数。
 */
public record ToolPolicyDecision(boolean allowed,
                                 String message,
                                 Map<String, Object> normalizedInput) {

    public ToolPolicyDecision {
        normalizedInput = normalizedInput == null ? Collections.emptyMap() : Collections.unmodifiableMap(normalizedInput);
    }

    public static ToolPolicyDecision allow(Map<String, Object> normalizedInput) {
        return new ToolPolicyDecision(true, null, normalizedInput);
    }

    public static ToolPolicyDecision deny(String message) {
        return new ToolPolicyDecision(false, message, Collections.emptyMap());
    }
}
