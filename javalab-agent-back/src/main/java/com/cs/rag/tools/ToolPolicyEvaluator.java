package com.cs.rag.tools;

import com.cs.rag.config.AgentToolProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 工具安全策略评估器。
 * 第一阶段只负责只读工具的工作区路径校验和网页访问校验。
 */
@Component
public class ToolPolicyEvaluator {

    private static final String DEFAULT_WEB_SEARCH_HOST = "html.duckduckgo.com";

    private final AgentToolProperties properties;

    public ToolPolicyEvaluator(AgentToolProperties properties) {
        this.properties = properties;
    }

    /**
     * 对一次工具调用进行策略判断，并返回归一化后的入参。
     */
    public ToolPolicyDecision evaluate(ToolDescriptor descriptor, Map<String, Object> input) {
        if (descriptor == null) {
            return ToolPolicyDecision.deny("Unsupported tool");
        }
        Map<String, Object> normalizedInput = new LinkedHashMap<>();
        if (input != null) {
            normalizedInput.putAll(input);
        }
        return switch (descriptor.name()) {
            case ToolRegistry.TOOL_FILE_READ -> evaluateFileRead(normalizedInput);
            case ToolRegistry.TOOL_DIR_LIST -> evaluateDirList(normalizedInput);
            case ToolRegistry.TOOL_FILE_SEARCH -> evaluateFileSearch(normalizedInput);
            case ToolRegistry.TOOL_GREP_SEARCH -> evaluateGrepSearch(normalizedInput);
            case ToolRegistry.TOOL_WEB_SEARCH -> evaluateWebSearch(normalizedInput);
            case ToolRegistry.TOOL_WEB_READ -> evaluateWebRead(normalizedInput);
            default -> ToolPolicyDecision.allow(normalizedInput);
        };
    }

    /**
     * 获取当前生效的工作区根目录。
     */
    public Path getWorkspaceRoot() {
        String configuredRoot = properties.getWorkspaceRoot();
        if (configuredRoot != null && !configuredRoot.isBlank()) {
            return Paths.get(configuredRoot).toAbsolutePath().normalize();
        }
        Path current = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        if (current.getFileName() != null
                && "javalab-agent-back".equalsIgnoreCase(current.getFileName().toString())
                && current.getParent() != null) {
            return current.getParent().normalize();
        }
        return current;
    }

    private ToolPolicyDecision evaluateFileRead(Map<String, Object> input) {
        String rawPath = asString(input.get("path"));
        if (rawPath == null || rawPath.isBlank()) {
            return ToolPolicyDecision.deny("file_read requires path");
        }
        Path filePath = resolveWorkspacePath(rawPath, true);
        if (filePath == null || !Files.isRegularFile(filePath)) {
            return ToolPolicyDecision.deny("file_read path is not a readable file inside workspace");
        }
        input.put("path", filePath.toString());
        int startLine = Math.max(1, asInt(input.get("startLine"), 1));
        int maxEndLine = startLine + Math.max(1, properties.getMaxReadLines()) - 1;
        int endLine = Math.max(startLine, asInt(input.get("endLine"), maxEndLine));
        input.put("startLine", startLine);
        input.put("endLine", Math.min(endLine, maxEndLine));
        return ToolPolicyDecision.allow(input);
    }

    private ToolPolicyDecision evaluateDirList(Map<String, Object> input) {
        String rawPath = defaultIfBlank(asString(input.get("path")), getWorkspaceRoot().toString());
        Path directory = resolveWorkspacePath(rawPath, true);
        if (directory == null || !Files.isDirectory(directory)) {
            return ToolPolicyDecision.deny("dir_list path is not a directory inside workspace");
        }
        input.put("path", directory.toString());
        input.put("recursive", asBoolean(input.get("recursive"), false));
        input.put("maxDepth", clamp(asInt(input.get("maxDepth"), properties.getMaxDirectoryDepth()), 1, properties.getMaxDirectoryDepth()));
        return ToolPolicyDecision.allow(input);
    }

    private ToolPolicyDecision evaluateFileSearch(Map<String, Object> input) {
        String pattern = asString(input.get("pattern"));
        if (pattern == null || pattern.isBlank()) {
            return ToolPolicyDecision.deny("file_search requires pattern");
        }
        String rawBaseDir = defaultIfBlank(asString(input.get("baseDir")), getWorkspaceRoot().toString());
        Path baseDir = resolveWorkspacePath(rawBaseDir, true);
        if (baseDir == null || !Files.isDirectory(baseDir)) {
            return ToolPolicyDecision.deny("file_search baseDir is invalid");
        }
        input.put("pattern", pattern.trim());
        input.put("baseDir", baseDir.toString());
        input.put("maxDepth", clamp(asInt(input.get("maxDepth"), properties.getMaxFileSearchDepth()), 1, properties.getMaxFileSearchDepth()));
        return ToolPolicyDecision.allow(input);
    }

    private ToolPolicyDecision evaluateGrepSearch(Map<String, Object> input) {
        String query = asString(input.get("query"));
        if (query == null || query.isBlank()) {
            return ToolPolicyDecision.deny("grep_search requires query");
        }
        String rawBaseDir = defaultIfBlank(asString(input.get("baseDir")), getWorkspaceRoot().toString());
        Path baseDir = resolveWorkspacePath(rawBaseDir, true);
        if (baseDir == null || !Files.isDirectory(baseDir)) {
            return ToolPolicyDecision.deny("grep_search baseDir is invalid");
        }
        input.put("query", query);
        input.put("baseDir", baseDir.toString());
        input.put("caseSensitive", asBoolean(input.get("caseSensitive"), false));
        input.put("includes", toStringList(input.get("includes")));
        return ToolPolicyDecision.allow(input);
    }

    private ToolPolicyDecision evaluateWebSearch(Map<String, Object> input) {
        String query = asString(input.get("query"));
        if (query == null || query.isBlank()) {
            return ToolPolicyDecision.deny("web_search requires query");
        }
        if (!isAllowedHost(DEFAULT_WEB_SEARCH_HOST)) {
            return ToolPolicyDecision.deny("web_search host is not allowed by policy");
        }
        input.put("query", query.trim());
        input.put("maxResults", clamp(asInt(input.get("maxResults"), 5), 1, Math.max(1, properties.getMaxSearchResults())));
        return ToolPolicyDecision.allow(input);
    }

    private ToolPolicyDecision evaluateWebRead(Map<String, Object> input) {
        String rawUrl = asString(input.get("url"));
        if (rawUrl == null || rawUrl.isBlank()) {
            return ToolPolicyDecision.deny("web_read requires url");
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                return ToolPolicyDecision.deny("web_read only supports http or https URLs");
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return ToolPolicyDecision.deny("web_read requires a valid host");
            }
            if (!isAllowedHost(host)) {
                return ToolPolicyDecision.deny("web_read host is not allowed by policy");
            }
            input.put("url", uri.toString());
            return ToolPolicyDecision.allow(input);
        } catch (Exception e) {
            return ToolPolicyDecision.deny("web_read url is invalid: " + e.getMessage());
        }
    }

    private boolean isAllowedHost(String host) {
        List<String> allowedWebHosts = properties.getAllowedWebHosts();
        if (allowedWebHosts == null || allowedWebHosts.isEmpty()) {
            return true;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        for (String allowedHost : allowedWebHosts) {
            if (allowedHost == null || allowedHost.isBlank()) {
                continue;
            }
            String normalizedAllowedHost = allowedHost.toLowerCase(Locale.ROOT).trim();
            if (normalizedHost.equals(normalizedAllowedHost) || normalizedHost.endsWith("." + normalizedAllowedHost)) {
                return true;
            }
        }
        return false;
    }

    private Path resolveWorkspacePath(String rawPath, boolean mustExist) {
        try {
            Path workspaceRoot = getWorkspaceRoot();
            Path candidate = Paths.get(rawPath);
            if (!candidate.isAbsolute()) {
                candidate = workspaceRoot.resolve(candidate);
            }
            candidate = candidate.toAbsolutePath().normalize();
            if (!candidate.startsWith(workspaceRoot)) {
                return null;
            }
            if (mustExist && !Files.exists(candidate)) {
                return null;
            }
            return candidate;
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> toStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> values = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    String text = String.valueOf(item).trim();
                    if (!text.isBlank()) {
                        values.add(text);
                    }
                }
            }
            return values;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? List.of() : List.of(text);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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

    private boolean asBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
