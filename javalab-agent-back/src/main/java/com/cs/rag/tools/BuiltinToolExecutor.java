package com.cs.rag.tools;

import com.cs.rag.config.AgentToolProperties;
import com.cs.rag.service.ReactAgentToolService;
import com.cs.rag.service.impl.RagConversationSupport;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 内置工具执行器。
 * 第一阶段仅承载知识检索和只读工具能力。
 */
@Component
public class BuiltinToolExecutor {

    private static final long MAX_TEXT_FILE_SIZE_BYTES = 1024 * 1024;
    private static final Pattern TITLE_PATTERN = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern SEARCH_RESULT_LINK_PATTERN = Pattern.compile("(?is)<a[^>]*class=\"[^\"]*result__a[^\"]*\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>");
    private static final Pattern SEARCH_RESULT_SNIPPET_PATTERN = Pattern.compile("(?is)<(?:a|div)[^>]*class=\"[^\"]*result__snippet[^\"]*\"[^>]*>(.*?)</(?:a|div)>");

    private final RagConversationSupport ragConversationSupport;
    private final ToolPolicyEvaluator toolPolicyEvaluator;
    private final AgentToolProperties properties;
    private final HttpClient httpClient;

    public BuiltinToolExecutor(RagConversationSupport ragConversationSupport,
                               ToolPolicyEvaluator toolPolicyEvaluator,
                               AgentToolProperties properties) {
        this.ragConversationSupport = ragConversationSupport;
        this.toolPolicyEvaluator = toolPolicyEvaluator;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(properties.getWebReadTimeoutSeconds()))
                .build();
    }

    /**
     * 执行指定内置工具。
     */
    public ReactAgentToolService.ToolExecutionResult execute(String toolName, Map<String, Object> input) {
        return switch (toolName) {
            case ToolRegistry.TOOL_KNOWLEDGE_SEARCH -> executeKnowledgeSearch(input);
            case ToolRegistry.TOOL_WEB_SEARCH -> executeWebSearch(input);
            case ToolRegistry.TOOL_FILE_READ -> executeFileRead(input);
            case ToolRegistry.TOOL_DIR_LIST -> executeDirList(input);
            case ToolRegistry.TOOL_FILE_SEARCH -> executeFileSearch(input);
            case ToolRegistry.TOOL_GREP_SEARCH -> executeGrepSearch(input);
            case ToolRegistry.TOOL_WEB_READ -> executeWebRead(input);
            default -> ReactAgentToolService.ToolExecutionResult.error(toolName, "Unsupported builtin tool", "builtin", 0L, Map.of());
        };
    }

    private ReactAgentToolService.ToolExecutionResult executeKnowledgeSearch(Map<String, Object> input) {
        long start = System.currentTimeMillis();
        String query = asString(input.get("query"));
        if (query == null || query.isBlank()) {
            return ReactAgentToolService.ToolExecutionResult.error(ToolRegistry.TOOL_KNOWLEDGE_SEARCH, "Missing query", "builtin", 0L, Map.of());
        }
        List<Document> documents = ragConversationSupport.performSearch(query);
        List<Map<String, Object>> hits = new ArrayList<>();
        if (documents != null) {
            for (Document doc : documents) {
                Map<String, Object> hit = new LinkedHashMap<>();
                hit.put("score", doc.getScore());
                String text = doc.getText();
                hit.put("content", abbreviate(text, 400));
                hits.add(hit);
                if (hits.size() >= 5) {
                    break;
                }
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("query", query);
        data.put("count", hits.size());
        data.put("hits", hits);
        String summary = hits.isEmpty()
                ? "知识检索未找到相关结果。"
                : "知识检索找到 " + hits.size() + " 条结果。";
        return ReactAgentToolService.ToolExecutionResult.success(
                ToolRegistry.TOOL_KNOWLEDGE_SEARCH,
                data,
                summary,
                "builtin",
                System.currentTimeMillis() - start,
                Map.of()
        );
    }

    private ReactAgentToolService.ToolExecutionResult executeWebSearch(Map<String, Object> input) {
        long start = System.currentTimeMillis();
        String query = asString(input.get("query"));
        int maxResults = asInt(input.get("maxResults"), 5);
        if (query == null || query.isBlank()) {
            return ReactAgentToolService.ToolExecutionResult.error(ToolRegistry.TOOL_WEB_SEARCH, "Missing query", "builtin", System.currentTimeMillis() - start, Map.of());
        }
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://html.duckduckgo.com/html/?q=" + encodedQuery))
                    .timeout(Duration.ofSeconds(properties.getWebReadTimeoutSeconds()))
                    .header("User-Agent", "JavaLabAgent/1.0")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String html = response.body() == null ? "" : response.body();
            List<Map<String, Object>> results = new ArrayList<>();
            Matcher matcher = SEARCH_RESULT_LINK_PATTERN.matcher(html);
            while (matcher.find() && results.size() < maxResults) {
                String link = normalizeSearchResultUrl(matcher.group(1));
                String title = normalizeText(stripHtml(matcher.group(2)));
                String snippet = extractSearchSnippet(html, matcher.end());
                if (link == null || link.isBlank() || title.isBlank()) {
                    continue;
                }
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("url", link);
                result.put("title", title);
                result.put("snippet", snippet);
                results.add(result);
            }
            if (response.statusCode() >= 400) {
                return ReactAgentToolService.ToolExecutionResult.error(
                        ToolRegistry.TOOL_WEB_SEARCH,
                        "HTTP status " + response.statusCode() + " while searching web",
                        "builtin",
                        System.currentTimeMillis() - start,
                        Map.of("query", query, "statusCode", response.statusCode())
                );
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("query", query);
            data.put("count", results.size());
            data.put("results", results);
            String summary = results.isEmpty()
                    ? "网页搜索未找到合适结果。"
                    : "网页搜索找到 " + results.size() + " 条结果。";
            return ReactAgentToolService.ToolExecutionResult.success(
                    ToolRegistry.TOOL_WEB_SEARCH,
                    data,
                    summary,
                    "builtin",
                    System.currentTimeMillis() - start,
                    Map.of("query", query, "statusCode", response.statusCode())
            );
        } catch (Exception e) {
            String message = e.getMessage() == null || e.getMessage().isBlank()
                    ? e.getClass().getSimpleName()
                    : e.getMessage();
            return ReactAgentToolService.ToolExecutionResult.error(ToolRegistry.TOOL_WEB_SEARCH, "Failed to search web: " + message, "builtin", System.currentTimeMillis() - start, Map.of("query", query));
        }
    }

    private ReactAgentToolService.ToolExecutionResult executeFileRead(Map<String, Object> input) {
        long start = System.currentTimeMillis();
        Path path = Paths.get(asString(input.get("path")));
        if (!isProbablyTextFile(path)) {
            return ReactAgentToolService.ToolExecutionResult.error(ToolRegistry.TOOL_FILE_READ, "Only text files smaller than 1MB are supported", "builtin", System.currentTimeMillis() - start, Map.of("path", path.toString()));
        }
        int startLine = asInt(input.get("startLine"), 1);
        int endLine = asInt(input.get("endLine"), startLine + properties.getMaxReadLines() - 1);
        List<String> lines = new ArrayList<>();
        boolean truncated = false;
        int characterCount = 0;
        int totalLines = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                totalLines++;
                if (totalLines < startLine) {
                    continue;
                }
                if (totalLines > endLine) {
                    truncated = true;
                    break;
                }
                String formatted = totalLines + ": " + abbreviate(line, 500);
                characterCount += formatted.length();
                if (characterCount > properties.getMaxReadCharacters()) {
                    truncated = true;
                    break;
                }
                lines.add(formatted);
            }
            if (!truncated && reader.readLine() != null) {
                truncated = true;
            }
        } catch (Exception e) {
            return ReactAgentToolService.ToolExecutionResult.error(ToolRegistry.TOOL_FILE_READ, "Failed to read file: " + e.getMessage(), "builtin", System.currentTimeMillis() - start, Map.of("path", path.toString()));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("path", relativize(path));
        data.put("startLine", startLine);
        data.put("endLine", Math.max(startLine, startLine + lines.size() - 1));
        data.put("count", lines.size());
        data.put("truncated", truncated);
        data.put("content", String.join("\n", lines));
        String summary = "读取文件 " + relativize(path) + "，返回 " + lines.size() + " 行。";
        return ReactAgentToolService.ToolExecutionResult.success(
                ToolRegistry.TOOL_FILE_READ,
                data,
                summary,
                "builtin",
                System.currentTimeMillis() - start,
                Map.of("path", path.toString(), "totalLinesRead", totalLines)
        );
    }

    private ReactAgentToolService.ToolExecutionResult executeDirList(Map<String, Object> input) {
        long start = System.currentTimeMillis();
        Path directory = Paths.get(asString(input.get("path")));
        boolean recursive = asBoolean(input.get("recursive"), false);
        int maxDepth = asInt(input.get("maxDepth"), properties.getMaxDirectoryDepth());
        int effectiveDepth = recursive ? maxDepth : 1;
        List<Map<String, Object>> entries = new ArrayList<>();
        try (Stream<Path> pathStream = Files.walk(directory, effectiveDepth)) {
            pathStream
                    .filter(path -> !path.equals(directory))
                    .limit(properties.getMaxSearchResults())
                    .forEach(path -> entries.add(toPathEntry(path)));
        } catch (Exception e) {
            return ReactAgentToolService.ToolExecutionResult.error(ToolRegistry.TOOL_DIR_LIST, "Failed to list directory: " + e.getMessage(), "builtin", System.currentTimeMillis() - start, Map.of("path", directory.toString()));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("path", relativize(directory));
        data.put("count", entries.size());
        data.put("entries", entries);
        String summary = "目录 " + relativize(directory) + " 共列出 " + entries.size() + " 个条目。";
        return ReactAgentToolService.ToolExecutionResult.success(
                ToolRegistry.TOOL_DIR_LIST,
                data,
                summary,
                "builtin",
                System.currentTimeMillis() - start,
                Map.of("path", directory.toString(), "recursive", recursive)
        );
    }

    private ReactAgentToolService.ToolExecutionResult executeFileSearch(Map<String, Object> input) {
        long start = System.currentTimeMillis();
        String pattern = asString(input.get("pattern"));
        Path baseDir = Paths.get(asString(input.get("baseDir")));
        int maxDepth = asInt(input.get("maxDepth"), properties.getMaxFileSearchDepth());
        List<Map<String, Object>> files = new ArrayList<>();
        try (Stream<Path> pathStream = Files.walk(baseDir, maxDepth)) {
            List<Path> matchedPaths = pathStream
                    .filter(Files::isRegularFile)
                    .filter(path -> matchesPattern(baseDir, path, pattern))
                    .limit(properties.getMaxSearchResults())
                    .toList();
            for (Path matchedPath : matchedPaths) {
                files.add(toPathEntry(matchedPath));
            }
        } catch (Exception e) {
            return ReactAgentToolService.ToolExecutionResult.error(ToolRegistry.TOOL_FILE_SEARCH, "Failed to search files: " + e.getMessage(), "builtin", System.currentTimeMillis() - start, Map.of("baseDir", baseDir.toString()));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pattern", pattern);
        data.put("baseDir", relativize(baseDir));
        data.put("count", files.size());
        data.put("files", files);
        String summary = "文件搜索在 " + relativize(baseDir) + " 下找到 " + files.size() + " 个匹配项。";
        return ReactAgentToolService.ToolExecutionResult.success(
                ToolRegistry.TOOL_FILE_SEARCH,
                data,
                summary,
                "builtin",
                System.currentTimeMillis() - start,
                Map.of("baseDir", baseDir.toString())
        );
    }

    private ReactAgentToolService.ToolExecutionResult executeGrepSearch(Map<String, Object> input) {
        long start = System.currentTimeMillis();
        String query = asString(input.get("query"));
        Path baseDir = Paths.get(asString(input.get("baseDir")));
        boolean caseSensitive = asBoolean(input.get("caseSensitive"), false);
        List<String> includes = toStringList(input.get("includes"));
        List<Map<String, Object>> matches = new ArrayList<>();
        int scannedFiles = 0;
        try (Stream<Path> pathStream = Files.walk(baseDir, properties.getMaxFileSearchDepth())) {
            List<Path> candidateFiles = pathStream
                    .filter(Files::isRegularFile)
                    .filter(this::isProbablyTextFile)
                    .filter(path -> matchesIncludes(baseDir, path, includes))
                    .limit(properties.getMaxGrepFiles())
                    .toList();
            scannedFiles = candidateFiles.size();
            for (Path candidateFile : candidateFiles) {
                if (matches.size() >= properties.getMaxSearchResults()) {
                    break;
                }
                collectMatches(candidateFile, query, caseSensitive, matches);
            }
        } catch (Exception e) {
            return ReactAgentToolService.ToolExecutionResult.error(ToolRegistry.TOOL_GREP_SEARCH, "Failed to search text: " + e.getMessage(), "builtin", System.currentTimeMillis() - start, Map.of("baseDir", baseDir.toString()));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("query", query);
        data.put("baseDir", relativize(baseDir));
        data.put("count", matches.size());
        data.put("matches", matches);
        data.put("scannedFiles", scannedFiles);
        String summary = "文本搜索在 " + relativize(baseDir) + " 中找到 " + matches.size() + " 处匹配。";
        return ReactAgentToolService.ToolExecutionResult.success(
                ToolRegistry.TOOL_GREP_SEARCH,
                data,
                summary,
                "builtin",
                System.currentTimeMillis() - start,
                Map.of("baseDir", baseDir.toString(), "scannedFiles", scannedFiles)
        );
    }

    private ReactAgentToolService.ToolExecutionResult executeWebRead(Map<String, Object> input) {
        long start = System.currentTimeMillis();
        String url = asString(input.get("url"));
        if (url == null || url.isBlank()) {
            return ReactAgentToolService.ToolExecutionResult.error(
                    ToolRegistry.TOOL_WEB_READ,
                    "Missing url",
                    "builtin",
                    System.currentTimeMillis() - start,
                    Map.of()
            );
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(properties.getWebReadTimeoutSeconds()))
                    .header("User-Agent", "JavaLabAgent/1.0")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String html = response.body() == null ? "" : response.body();
            String title = extractTitle(html);
            String text = normalizeText(stripHtml(html));
            String content = abbreviate(text, properties.getMaxReadCharacters());
            if (response.statusCode() >= 400) {
                return ReactAgentToolService.ToolExecutionResult.error(
                        ToolRegistry.TOOL_WEB_READ,
                        "HTTP status " + response.statusCode() + " while reading webpage",
                        "builtin",
                        System.currentTimeMillis() - start,
                        Map.of("url", url, "statusCode", response.statusCode(), "title", title == null ? "" : title)
                );
            }
            if (isLikelyErrorPage(title, content)) {
                return ReactAgentToolService.ToolExecutionResult.error(
                        ToolRegistry.TOOL_WEB_READ,
                        "Webpage looks like an error page: " + firstNonBlank(title, "unknown page"),
                        "builtin",
                        System.currentTimeMillis() - start,
                        Map.of("url", url, "statusCode", response.statusCode(), "title", title == null ? "" : title)
                );
            }
            if (content.isBlank() || content.length() < 80) {
                return ReactAgentToolService.ToolExecutionResult.error(
                        ToolRegistry.TOOL_WEB_READ,
                        "Webpage content is empty or too short to be useful",
                        "builtin",
                        System.currentTimeMillis() - start,
                        Map.of("url", url, "statusCode", response.statusCode(), "title", title == null ? "" : title)
                );
            }
            String excerpt = abbreviate(extractUsefulExcerpt(content), 260);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("url", url);
            data.put("finalUrl", response.uri() == null ? url : response.uri().toString());
            data.put("statusCode", response.statusCode());
            data.put("title", title);
            data.put("excerpt", excerpt);
            data.put("content", content);
            String summary = (title == null || title.isBlank()
                    ? "网页读取成功：" + url
                    : "网页读取成功：" + title) + "；摘要：" + excerpt;
            return ReactAgentToolService.ToolExecutionResult.success(
                    ToolRegistry.TOOL_WEB_READ,
                    data,
                    summary,
                    "builtin",
                    System.currentTimeMillis() - start,
                    Map.of("url", url, "finalUrl", response.uri() == null ? url : response.uri().toString(), "statusCode", response.statusCode())
            );
        } catch (Exception e) {
            String message = e.getMessage() == null || e.getMessage().isBlank()
                    ? e.getClass().getSimpleName()
                    : e.getMessage();
            return ReactAgentToolService.ToolExecutionResult.error(ToolRegistry.TOOL_WEB_READ, "Failed to read webpage: " + message, "builtin", System.currentTimeMillis() - start, Map.of("url", url));
        }
    }

    private void collectMatches(Path file,
                                String query,
                                boolean caseSensitive,
                                List<Map<String, Object>> matches) {
        String normalizedQuery = caseSensitive ? query : query.toLowerCase(Locale.ROOT);
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String comparableLine = caseSensitive ? line : line.toLowerCase(Locale.ROOT);
                if (!comparableLine.contains(normalizedQuery)) {
                    continue;
                }
                Map<String, Object> match = new HashMap<>();
                match.put("path", relativize(file));
                match.put("line", lineNumber);
                match.put("content", abbreviate(line, 300));
                matches.add(match);
                if (matches.size() >= properties.getMaxSearchResults()) {
                    return;
                }
            }
        } catch (Exception ignored) {
            // 只读搜索场景下，单文件读取失败直接跳过即可，避免影响整体搜索结果。
        }
    }

    private Map<String, Object> toPathEntry(Path path) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("path", relativize(path));
        entry.put("type", Files.isDirectory(path) ? "directory" : "file");
        try {
            entry.put("size", Files.isDirectory(path) ? null : Files.size(path));
        } catch (Exception e) {
            entry.put("size", null);
        }
        return entry;
    }

    private boolean matchesPattern(Path baseDir, Path path, String pattern) {
        String trimmedPattern = pattern == null ? "" : pattern.trim();
        if (trimmedPattern.isBlank()) {
            return false;
        }
        Path relativePath = baseDir.relativize(path);
        if (containsGlob(trimmedPattern)) {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + trimmedPattern);
            return matcher.matches(relativePath) || matcher.matches(path.getFileName());
        }
        String normalizedPattern = trimmedPattern.toLowerCase(Locale.ROOT);
        return relativePath.toString().toLowerCase(Locale.ROOT).contains(normalizedPattern)
                || path.getFileName().toString().toLowerCase(Locale.ROOT).contains(normalizedPattern);
    }

    private boolean matchesIncludes(Path baseDir, Path path, List<String> includes) {
        if (includes == null || includes.isEmpty()) {
            return true;
        }
        Path relativePath = baseDir.relativize(path);
        for (String include : includes) {
            if (include == null || include.isBlank()) {
                continue;
            }
            if (containsGlob(include)) {
                PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + include);
                if (matcher.matches(relativePath) || matcher.matches(path.getFileName())) {
                    return true;
                }
            } else {
                String normalizedInclude = include.toLowerCase(Locale.ROOT);
                String relativeText = relativePath.toString().toLowerCase(Locale.ROOT);
                if (relativeText.contains(normalizedInclude) || path.getFileName().toString().toLowerCase(Locale.ROOT).contains(normalizedInclude)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsGlob(String pattern) {
        return pattern.contains("*") || pattern.contains("?") || pattern.contains("[") || pattern.contains("{");
    }

    private boolean isProbablyTextFile(Path path) {
        try {
            if (!Files.isRegularFile(path) || Files.size(path) > MAX_TEXT_FILE_SIZE_BYTES) {
                return false;
            }
            try (InputStream inputStream = Files.newInputStream(path)) {
                byte[] sample = inputStream.readNBytes(1024);
                for (byte currentByte : sample) {
                    if (currentByte == 0) {
                        return false;
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String extractTitle(String html) {
        Matcher matcher = TITLE_PATTERN.matcher(html == null ? "" : html);
        if (!matcher.find()) {
            return null;
        }
        return normalizeText(stripHtml(matcher.group(1)));
    }

    private String stripHtml(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return html
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"");
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private String abbreviate(String text, int maxLength) {
        String normalized = normalizeText(text);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private String extractUsefulExcerpt(String content) {
        String normalized = normalizeText(content);
        if (normalized.isBlank()) {
            return "";
        }
        String[] sentences = normalized.split("(?<=[。！？.!?])\\s+");
        StringBuilder builder = new StringBuilder();
        for (String sentence : sentences) {
            String trimmed = normalizeText(sentence);
            if (trimmed.isBlank() || isLikelyNoiseSentence(trimmed)) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(trimmed);
            if (builder.length() >= 220) {
                break;
            }
        }
        return builder.isEmpty() ? abbreviate(normalized, 220) : builder.toString();
    }

    private boolean isLikelyErrorPage(String title, String content) {
        String combined = (firstNonBlank(title, "") + " " + firstNonBlank(content, "")).toLowerCase(Locale.ROOT);
        return combined.contains("404")
                || combined.contains("page not found")
                || combined.contains("not found")
                || combined.contains("access denied")
                || combined.contains("forbidden")
                || combined.contains("error page")
                || combined.contains("抱歉")
                || combined.contains("页面不存在");
    }

    private boolean isLikelyNoiseSentence(String sentence) {
        String normalized = sentence.toLowerCase(Locale.ROOT);
        return normalized.contains("cookie")
                || normalized.contains("privacy policy")
                || normalized.contains("terms of use")
                || normalized.contains("copyright")
                || normalized.contains("all rights reserved")
                || normalized.contains("subscribe")
                || normalized.contains("sign in")
                || normalized.contains("menu")
                || normalized.contains("navigation");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String normalizeSearchResultUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        String trimmed = rawUrl.trim();
        try {
            URI uri = URI.create(trimmed);
            String query = uri.getQuery();
            if (query != null && query.contains("uddg=")) {
                for (String pair : query.split("&")) {
                    if (pair.startsWith("uddg=")) {
                        return URLDecoder.decode(pair.substring(5), StandardCharsets.UTF_8);
                    }
                }
            }
            return trimmed;
        } catch (Exception e) {
            return trimmed;
        }
    }

    private String extractSearchSnippet(String html, int startIndex) {
        if (html == null || html.isBlank() || startIndex < 0 || startIndex >= html.length()) {
            return "";
        }
        String tail = html.substring(startIndex, Math.min(html.length(), startIndex + 2000));
        Matcher matcher = SEARCH_RESULT_SNIPPET_PATTERN.matcher(tail);
        if (!matcher.find()) {
            return "";
        }
        return abbreviate(normalizeText(stripHtml(matcher.group(1))), 220);
    }

    private String relativize(Path path) {
        try {
            return toolPolicyEvaluator.getWorkspaceRoot().relativize(path).toString().replace('\\', '/');
        } catch (Exception e) {
            return path.toString().replace('\\', '/');
        }
    }

    private List<String> toStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        return List.of();
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
