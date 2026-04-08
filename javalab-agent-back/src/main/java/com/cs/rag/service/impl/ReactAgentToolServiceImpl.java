package com.cs.rag.service.impl;

import com.cs.rag.service.ReactAgentToolService;
import com.cs.rag.tools.BuiltinToolExecutor;
import com.cs.rag.tools.McpToolExecutor;
import com.cs.rag.tools.ToolDescriptor;
import com.cs.rag.tools.ToolPolicyDecision;
import com.cs.rag.tools.ToolPolicyEvaluator;
import com.cs.rag.tools.ToolRegistry;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReactAgentToolServiceImpl implements ReactAgentToolService {

    private final ToolRegistry toolRegistry;
    private final ToolPolicyEvaluator toolPolicyEvaluator;
    private final BuiltinToolExecutor builtinToolExecutor;
    private final McpToolExecutor mcpToolExecutor;

    public ReactAgentToolServiceImpl(ToolRegistry toolRegistry,
                                      ToolPolicyEvaluator toolPolicyEvaluator,
                                      BuiltinToolExecutor builtinToolExecutor,
                                      McpToolExecutor mcpToolExecutor) {
        this.toolRegistry = toolRegistry;
        this.toolPolicyEvaluator = toolPolicyEvaluator;
        this.builtinToolExecutor = builtinToolExecutor;
        this.mcpToolExecutor = mcpToolExecutor;
    }

    @Override
    public ToolExecutionResult execute(String toolName, Map<String, Object> input, String sessionId, Long userId) {
        Map<String, Object> safeInput = input == null ? Map.of() : new LinkedHashMap<>(input);
        ToolDescriptor descriptor = toolRegistry.findTool(toolName);
        if (descriptor == null) {
            return ToolExecutionResult.error(toolName, "Unsupported tool", "registry", 0L, Map.of());
        }

        ToolPolicyDecision policyDecision = toolPolicyEvaluator.evaluate(descriptor, safeInput);
        if (!policyDecision.allowed()) {
            return ToolExecutionResult.error(
                    toolName,
                    "Tool policy blocked: " + policyDecision.message(),
                    "policy",
                    0L,
                    Map.of("toolName", toolName)
            );
        }

        if ("builtin".equals(descriptor.source())) {
            return builtinToolExecutor.execute(toolName, policyDecision.normalizedInput());
        }
        return mcpToolExecutor.execute(toolName, policyDecision.normalizedInput());
    }

    @Override
    public String getToolDescription(String toolName) {
        ToolDescriptor descriptor = toolRegistry.findTool(toolName);
        return descriptor == null ? null : descriptor.description();
    }

    @Override
    public List<Map<String, Object>> toolSchemas() {
        return toolRegistry.toolSchemas();
    }
}
