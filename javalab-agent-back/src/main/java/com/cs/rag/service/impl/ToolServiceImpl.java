package com.cs.rag.service.impl;

import com.cs.rag.config.AgentToolProperties;
import com.cs.rag.pojo.entity.McpToolInfo;
import com.cs.rag.service.McpService;
import com.cs.rag.service.ToolService;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * ReAct 工具服务实现。
 * 统一承接工具注册、参数校验、内置工具执行与 MCP 工具转发。
 */
@Service
public class ToolServiceImpl implements ToolService {

    /** 文本文件读取大小上限。 */
    private static final long MAX_TEXT_FILE_SIZE_BYTES = 1024 * 1024;

    /** 默认网页搜索主机。 */
    private static final String WEB_SEARCH_HOST = "html.duckduckgo.com";

    /** 页面标题提取规则。 */
    private static final Pattern TITLE_PATTERN = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");

    /** DuckDuckGo 搜索结果链接提取规则。 */
    private static final Pattern SEARCH_RESULT_LINK_PATTERN = Pattern.compile("(?is)<a[^>]*class=\"[^\"]*result__a[^\"]*\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>");

    /** DuckDuckGo 搜索结果摘要提取规则。 */
    private static final Pattern SEARCH_RESULT_SNIPPET_PATTERN = Pattern.compile("(?is)<(?:a|div)[^>]*class=\"[^\"]*result__snippet[^\"]*\"[^>]*>(.*?)</(?:a|div)>");

    /** 工具策略评估相关常量。 */
    private static final Set<String> SHELL_EXECUTABLES = Set.of("cmd", "powershell", "pwsh", "bash", "sh", "zsh");
    private static final Set<String> BLOCKED_WRITE_EXTENSIONS = Set.of(
            ".exe", ".dll", ".so", ".dylib", ".class", ".jar", ".war",
            ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".ico", ".pdf",
            ".zip", ".tar", ".gz", ".7z", ".ps1", ".bat", ".cmd", ".sh"
    );

    private final AgentToolProperties properties;
    private final McpService mcpService;
    private final HttpClient httpClient;

    public ToolServiceImpl(AgentToolProperties properties,
                           McpService mcpService) {
        this.properties = properties;
        this.mcpService = mcpService;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(properties.getWebReadTimeoutSeconds()))
                .build();
    }

    @Override
    public ToolExecutionResult execute(String toolName, Map<String, Object> input, String sessionId, Long userId) {
        Map<String, Object> safeInput = input == null ? Map.of() : new LinkedHashMap<>(input);
        ToolDescriptor descriptor = findTool(toolName);
        if (descriptor == null) {
            return ToolExecutionResult.error(toolName, "Unsupported tool", "registry", 0L, Map.of());
        }

        ToolPolicyDecision policyDecision = evaluateToolPolicy(descriptor, safeInput);
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
            return executeBuiltinTool(toolName, policyDecision.normalizedInput());
        }
        return executeMcpTool(toolName, policyDecision.normalizedInput());
    }

    @Override
    public String getToolDescription(String toolName) {
        ToolDescriptor descriptor = findTool(toolName);
        return descriptor == null ? null : descriptor.description();
    }

    @Override
    public List<Map<String, Object>> toolSchemas() {
        List<Map<String, Object>> schemas = new ArrayList<>();
        for (ToolDescriptor descriptor : getAllTools()) {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("name", descriptor.name());
            schema.put("description", descriptor.description());
            schema.put("source", descriptor.source());
            schema.put("readOnly", descriptor.readOnly());

            Map<String, Object> inputSchema = descriptor.inputSchema();
            Object propertiesMap = inputSchema.get("properties");
            if (propertiesMap instanceof Map<?, ?> propsMap) {
                schema.put("params", new ArrayList<>(propsMap.keySet()));
                schema.put("paramDesc", buildParamDesc(propsMap));
            } else {
                schema.put("params", List.of());
                schema.put("paramDesc", "No params");
            }
            schemas.add(schema);
        }
        return schemas;
    }

    /**
     * 获取全部工具定义，统一合并内置工具和 MCP 工具。
     */
    private List<ToolDescriptor> getAllTools() {
        List<ToolDescriptor> descriptors = new ArrayList<>();
        descriptors.addAll(builtinTools());
        descriptors.addAll(mcpTools());
        return deduplicateTools(descriptors);
    }

    private ToolDescriptor findTool(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return null;
        }
        for (ToolDescriptor descriptor : getAllTools()) {
            if (descriptor.name().equals(toolName)) {
                return descriptor;
            }
        }
        return null;
    }

    /**
     * 内置工具定义。
     */
    private List<ToolDescriptor> builtinTools() {
        return List.of(
                new ToolDescriptor(TOOL_WEB_SEARCH, "Search the web for relevant pages", "builtin",
                        schemaOf(mapOf(
                                "query", property("string", "Search query"),
                                "maxResults", property("integer", "Optional max result count")
                        )), true),
                new ToolDescriptor(TOOL_FILE_READ, "Read a text file inside workspace", "builtin",
                        schemaOf(mapOf(
                                "path", property("string", "Workspace-relative or absolute file path"),
                                "startLine", property("integer", "Optional start line, default 1"),
                                "endLine", property("integer", "Optional end line")
                        )), true),
                new ToolDescriptor(TOOL_FILE_WRITE, "Create, overwrite, or append a text file inside workspace", "builtin",
                        schemaOf(mapOf(
                                "path", property("string", "Workspace-relative or absolute file path"),
                                "content", property("string", "Full text content to write"),
                                "append", property("boolean", "Append to an existing file instead of replacing it"),
                                "overwrite", property("boolean", "Explicitly allow replacing an existing file"),
                                "createDirectories", property("boolean", "Whether to create missing parent directories")
                        )), false),
                new ToolDescriptor(TOOL_FILE_SEARCH, "Search files by pattern inside workspace", "builtin",
                        schemaOf(mapOf(
                                "pattern", property("string", "Filename pattern, glob or keyword"),
                                "baseDir", property("string", "Optional base directory"),
                                "maxDepth", property("integer", "Maximum traversal depth")
                        )), true),
                new ToolDescriptor(TOOL_GREP_SEARCH, "Search text in workspace files", "builtin",
                        schemaOf(mapOf(
                                "query", property("string", "Text query to search"),
                                "baseDir", property("string", "Optional base directory"),
                                "includes", property("array", "Optional file glob filters"),
                                "caseSensitive", property("boolean", "Whether the search is case-sensitive")
                        )), true),
                new ToolDescriptor(TOOL_TERMINAL_EXEC, "Execute a workspace command unless blocked by server policy", "builtin",
                        schemaOf(mapOf(
                                "command", property("array", "Executable plus args as a JSON array, for example [\"mvn\",\"-q\",\"-DskipTests\",\"compile\"]"),
                                "workdir", property("string", "Optional workspace directory for the command"),
                                "timeoutSeconds", property("integer", "Optional timeout, capped by server policy")
                        )), false),
                new ToolDescriptor(TOOL_WEB_READ, "Read webpage content from an http or https URL", "builtin",
                        schemaOf(mapOf(
                                "url", property("string", "Target webpage URL")
                        )), true)
        );
    }

    private List<ToolDescriptor> mcpTools() {
        List<ToolDescriptor> descriptors = new ArrayList<>();
        for (McpToolInfo mcpTool : mcpService.getAllTools()) {
            descriptors.add(new ToolDescriptor(
                    mcpTool.getName(),
                    mcpTool.getDescription(),
                    "mcp:" + mcpTool.getServerName(),
                    mcpTool.getInputSchema(),
                    true
            ));
        }
        return descriptors;
    }

    private List<ToolDescriptor> deduplicateTools(List<ToolDescriptor> descriptors) {
        List<ToolDescriptor> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ToolDescriptor descriptor : descriptors) {
            if (seen.add(descriptor.name())) {
                result.add(descriptor);
            }
        }
        return result;
    }

    private String buildParamDesc(Map<?, ?> propsMap) {
        StringBuilder builder = new StringBuilder();
        propsMap.forEach((key, value) -> {
            if (value instanceof Map<?, ?> propDef) {
                Object description = propDef.get("description");
                Object type = propDef.get("type");
                if (!builder.isEmpty()) {
                    builder.append("; ");
                }
                builder.append(key).append(": ").append(description != null ? description : type);
            }
        });
        return builder.isEmpty() ? "No params" : builder.toString();
    }

    private Map<String, Object> schemaOf(Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return schema;
    }

    private Map<String, Object> property(String type, String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", description);
        return property;
    }

    @SafeVarargs
    private Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }

    /**
     * 执行内置工具。
     */
    private ToolExecutionResult executeBuiltinTool(String toolName, Map<String, Object> input) {
        return switch (toolName) {
            case TOOL_WEB_SEARCH -> executeWebSearch(input);
            case TOOL_FILE_READ -> executeFileRead(input);
            case TOOL_FILE_WRITE -> executeFileWrite(input);
            case TOOL_FILE_SEARCH -> executeFileSearch(input);
            case TOOL_GREP_SEARCH -> executeGrepSearch(input);
            case TOOL_TERMINAL_EXEC -> executeTerminalExec(input);
            case TOOL_WEB_READ -> executeWebRead(input);
            default -> ToolExecutionResult.error(toolName, "Unsupported builtin tool", "builtin", 0L, Map.of());
        };
    }

    private ToolExecutionResult executeMcpTool(String toolName, Map<String, Object> input) {
        long start = System.currentTimeMillis();
        try {
            McpService.McpToolResult mcpResult = mcpService.callTool(toolName, input);
            if (mcpResult.isSuccess()) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("source", "mcp");
                data.put("result", mcpResult.getContent());
                return ToolExecutionResult.success(
                        toolName,
                        data,
                        null,
                        "mcp",
                        System.currentTimeMillis() - start,
                        Map.of()
                );
            }
            return ToolExecutionResult.error(
                    toolName,
                    "MCP tool error: " + mcpResult.getErrorMessage(),
                    "mcp",
                    System.currentTimeMillis() - start,
                    Map.of()
            );
        } catch (Exception e) {
            return ToolExecutionResult.error(
                    toolName,
                    "MCP tool invocation failed: " + e.getMessage(),
                    "mcp",
                    System.currentTimeMillis() - start,
                    Map.of()
            );
        }
    }

    private ToolExecutionResult executeWebSearch(Map<String, Object> input) {
        long start = System.currentTimeMillis();
        String query = asString(input.get("query"));
        int maxResults = asInt(input.get("maxResults"), 5);
        if (query == null || query.isBlank()) {
            return ToolExecutionResult.error(TOOL_WEB_SEARCH, "Missing query", "builtin", System.currentTimeMillis() - start, Map.of());
        }

        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://" + WEB_SEARCH_HOST + "/html/?q=" + encodedQuery))
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
                return ToolExecutionResult.error(
                        TOOL_WEB_SEARCH,
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
            String summary = results.isEmpty() ? "网页搜索未找到合适结果。" : "网页搜索找到 " + results.size() + " 条结果。";
            return ToolExecutionResult.success(
                    TOOL_WEB_SEARCH,
                    data,
                    summary,
                    "builtin",
                    System.currentTimeMillis() - start,
                    Map.of("query", query, "statusCode", response.statusCode())
            );
        } catch (Exception e) {
            String message = e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage();
            return ToolExecutionResult.error(TOOL_WEB_SEARCH, "Failed to search web: " + message, "builtin",
                    System.currentTimeMillis() - start, Map.of("query", query));
        }
    }

    private ToolExecutionResult executeFileRead(Map<String, Object> input) {
        long start = System.currentTimeMillis();
        Path path = Paths.get(asString(input.get("path")));
        if (!isProbablyTextFile(path)) {
            return ToolExecutionResult.error(TOOL_FILE_READ, "Only text files smaller than 1MB are supported",
                    "builtin", System.currentTimeMillis() - start, Map.of("path", path.toString()));
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
            return ToolExecutionResult.error(TOOL_FILE_READ, "Failed to read file: " + e.getMessage(),
                    "builtin", System.currentTimeMillis() - start, Map.of("path", path.toString()));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("path", relativize(path));
        data.put("startLine", startLine);
        data.put("endLine", Math.max(startLine, startLine + lines.size() - 1));
        data.put("count", lines.size());
        data.put("truncated", truncated);
        data.put("content", String.join("\n", lines));
        String summary = "文件 " + relativize(path) + " 读取成功，共 " + lines.size() + " 行。";
        return ToolExecutionResult.success(
                TOOL_FILE_READ,
                data,
                summary,
                "builtin",
                System.currentTimeMillis() - start,
                Map.of("path", path.toString(), "totalLinesRead", totalLines)
        );
    }

    private ToolExecutionResult executeFileWrite(Map<String, Object> input) {
        long start = System.currentTimeMillis();
        Path path = Paths.get(asString(input.get("path")));
        String content = asString(input.get("content"));
        boolean append = asBoolean(input.get("append"), false);
        boolean createDirectories = asBoolean(input.get("createDirectories"), true);
        boolean existedBefore = Files.exists(path);
        try {
            Path parent = path.getParent();
            if (createDirectories && parent != null) {
                Files.createDirectories(parent);
            }

            if (append) {
                Files.writeString(path, content, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
            } else if (existedBefore) {
                Files.writeString(path, content, StandardCharsets.UTF_8,
                        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            } else {
                Files.writeString(path, content, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            }

            String operation = append ? (existedBefore ? "append" : "create") : (existedBefore ? "overwrite" : "create");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", relativize(path));
            data.put("operation", operation);
            data.put("created", !existedBefore);
            data.put("appended", append);
            data.put("size", Files.size(path));
            data.put("writtenCharacters", content == null ? 0 : content.length());

            String summary = "文件 " + relativize(path) + " " + operation + " 成功。";
            return ToolExecutionResult.success(
                    TOOL_FILE_WRITE,
                    data,
                    summary,
                    "builtin",
                    System.currentTimeMillis() - start,
                    Map.of("path", path.toString(), "operation", operation)
            );
        } catch (Exception e) {
            return ToolExecutionResult.error(
                    TOOL_FILE_WRITE,
                    "Failed to write file: " + e.getMessage(),
                    "builtin",
                    System.currentTimeMillis() - start,
                    Map.of("path", path.toString())
            );
        }
    }

    private ToolExecutionResult executeTerminalExec(Map<String, Object> input) {
        long start = System.currentTimeMillis();
        List<String> command = toStringList(input.get("command"));
        if (command.isEmpty()) {
            return ToolExecutionResult.error(TOOL_TERMINAL_EXEC, "Missing command", "builtin",
                    System.currentTimeMillis() - start, Map.of());
        }

        Path workdir = Paths.get(asString(input.get("workdir")));
        int timeoutSeconds = asInt(input.get("timeoutSeconds"), properties.getTerminalTimeoutSeconds());
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workdir.toFile());
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            FutureTask<OutputCapture> outputTask = new FutureTask<>(() -> captureProcessOutput(process.getInputStream()));
            Thread outputThread = new Thread(outputTask, "builtin-terminal-output");
            outputThread.setDaemon(true);
            outputThread.start();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }

            OutputCapture outputCapture = awaitOutputCapture(outputTask);
            int exitCode = finished ? process.exitValue() : -1;
            boolean ok = finished && exitCode == 0;

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("command", command);
            data.put("commandLine", formatCommand(command));
            data.put("workdir", relativize(workdir));
            data.put("exitCode", exitCode);
            data.put("timedOut", !finished);
            data.put("ok", ok);
            data.put("truncated", outputCapture.truncated());
            data.put("output", outputCapture.text());

            String preview = outputCapture.text().isBlank() ? "no output" : abbreviate(outputCapture.text(), 260);
            String summary;
            if (!finished) {
                summary = "Terminal command timed out after " + timeoutSeconds + "s. Output: " + preview;
            } else if (ok) {
                summary = "Terminal command succeeded. Output: " + preview;
            } else {
                summary = "Terminal command exited with code " + exitCode + ". Output: " + preview;
            }

            return ToolExecutionResult.success(
                    TOOL_TERMINAL_EXEC,
                    data,
                    summary,
                    "builtin",
                    System.currentTimeMillis() - start,
                    Map.of("command", formatCommand(command), "exitCode", exitCode, "timedOut", !finished)
            );
        } catch (Exception e) {
            return ToolExecutionResult.error(
                    TOOL_TERMINAL_EXEC,
                    "Failed to execute terminal command: " + e.getMessage(),
                    "builtin",
                    System.currentTimeMillis() - start,
                    Map.of("command", formatCommand(command), "workdir", workdir.toString())
            );
        }
    }

    private ToolExecutionResult executeFileSearch(Map<String, Object> input) {
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
            return ToolExecutionResult.error(TOOL_FILE_SEARCH, "Failed to search files: " + e.getMessage(),
                    "builtin", System.currentTimeMillis() - start, Map.of("baseDir", baseDir.toString()));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pattern", pattern);
        data.put("baseDir", relativize(baseDir));
        data.put("count", files.size());
        data.put("files", files);
        String summary = "文件搜索在 " + relativize(baseDir) + " 下找到 " + files.size() + " 个匹配项。";
        return ToolExecutionResult.success(
                TOOL_FILE_SEARCH,
                data,
                summary,
                "builtin",
                System.currentTimeMillis() - start,
                Map.of("baseDir", baseDir.toString())
        );
    }

    private ToolExecutionResult executeGrepSearch(Map<String, Object> input) {
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
            return ToolExecutionResult.error(TOOL_GREP_SEARCH, "Failed to search text: " + e.getMessage(),
                    "builtin", System.currentTimeMillis() - start, Map.of("baseDir", baseDir.toString()));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("query", query);
        data.put("baseDir", relativize(baseDir));
        data.put("count", matches.size());
        data.put("matches", matches);
        data.put("scannedFiles", scannedFiles);
        String summary = "文本搜索在 " + relativize(baseDir) + " 下找到 " + matches.size() + " 个匹配项。";
        return ToolExecutionResult.success(
                TOOL_GREP_SEARCH,
                data,
                summary,
                "builtin",
                System.currentTimeMillis() - start,
                Map.of("baseDir", baseDir.toString(), "scannedFiles", scannedFiles)
        );
    }

    private ToolExecutionResult executeWebRead(Map<String, Object> input) {
        long start = System.currentTimeMillis();
        String url = asString(input.get("url"));
        if (url == null || url.isBlank()) {
            return ToolExecutionResult.error(TOOL_WEB_READ, "Missing url", "builtin",
                    System.currentTimeMillis() - start, Map.of());
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
                return ToolExecutionResult.error(
                        TOOL_WEB_READ,
                        "HTTP status " + response.statusCode() + " while reading webpage",
                        "builtin",
                        System.currentTimeMillis() - start,
                        Map.of("url", url, "statusCode", response.statusCode(), "title", title == null ? "" : title)
                );
            }
            if (isLikelyErrorPage(title, content)) {
                return ToolExecutionResult.error(
                        TOOL_WEB_READ,
                        "Webpage looks like an error page: " + firstNonBlank(title, "unknown page"),
                        "builtin",
                        System.currentTimeMillis() - start,
                        Map.of("url", url, "statusCode", response.statusCode(), "title", title == null ? "" : title)
                );
            }
            if (content.isBlank() || content.length() < 80) {
                return ToolExecutionResult.error(
                        TOOL_WEB_READ,
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
            String summary = (title == null || title.isBlank() ? "网页读取成功：" + url : "网页读取成功：" + title) + "；摘要：" + excerpt;
            return ToolExecutionResult.success(
                    TOOL_WEB_READ,
                    data,
                    summary,
                    "builtin",
                    System.currentTimeMillis() - start,
                    Map.of("url", url, "finalUrl", response.uri() == null ? url : response.uri().toString(), "statusCode", response.statusCode())
            );
        } catch (Exception e) {
            String message = e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage();
            return ToolExecutionResult.error(TOOL_WEB_READ, "Failed to read webpage: " + message, "builtin",
                    System.currentTimeMillis() - start, Map.of("url", url));
        }
    }

    private void collectMatches(Path file, String query, boolean caseSensitive, List<Map<String, Object>> matches) {
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
            // 只读搜索场景中，单文件失败直接跳过，避免影响整体结果。
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
                if (relativeText.contains(normalizedInclude)
                        || path.getFileName().toString().toLowerCase(Locale.ROOT).contains(normalizedInclude)) {
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
        return html.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
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
        String[] sentences = normalized.split("(?<=[。!?])\\s+");
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
                || combined.contains("错误页面")
                || combined.contains("找不到页面");
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

    private OutputCapture captureProcessOutput(InputStream inputStream) throws Exception {
        StringBuilder builder = new StringBuilder();
        boolean truncated = false;
        int maxCharacters = Math.max(1, properties.getMaxTerminalOutputCharacters());
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String formatted = lineNumber + ": " + line;
                if (builder.length() < maxCharacters) {
                    if (!builder.isEmpty()) {
                        if (builder.length() + 1 <= maxCharacters) {
                            builder.append('\n');
                        } else {
                            truncated = true;
                            continue;
                        }
                    }
                    int remaining = maxCharacters - builder.length();
                    if (formatted.length() <= remaining) {
                        builder.append(formatted);
                    } else {
                        builder.append(formatted, 0, Math.max(0, remaining));
                        truncated = true;
                    }
                } else {
                    truncated = true;
                }
            }
        }
        return new OutputCapture(builder.toString(), truncated);
    }

    private OutputCapture awaitOutputCapture(FutureTask<OutputCapture> outputTask) {
        if (outputTask == null) {
            return new OutputCapture("", false);
        }
        try {
            return outputTask.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            return new OutputCapture("", false);
        }
    }

    private String formatCommand(List<String> command) {
        if (command == null || command.isEmpty()) {
            return "";
        }
        List<String> rendered = new ArrayList<>();
        for (String token : command) {
            if (token == null) {
                continue;
            }
            rendered.add(token.contains(" ") ? "\"" + token + "\"" : token);
        }
        return String.join(" ", rendered);
    }

    private String relativize(Path path) {
        try {
            return getWorkspaceRoot().relativize(path).toString().replace('\\', '/');
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

    /**
     * 终端输出捕获结果。
     */
    private record OutputCapture(String text, boolean truncated) {
    }

    // ==================== 工具策略评估方法（内联自 ToolPolicyEvaluator）====================

    /**
     * 评估工具策略，确保参数安全和合规。
     */
    private ToolPolicyDecision evaluateToolPolicy(ToolDescriptor descriptor, Map<String, Object> input) {
        if (descriptor == null) {
            return ToolPolicyDecision.deny("Unsupported tool");
        }
        Map<String, Object> normalizedInput = new LinkedHashMap<>();
        if (input != null) {
            normalizedInput.putAll(input);
        }
        return switch (descriptor.name()) {
            case TOOL_FILE_READ -> evaluateFileRead(normalizedInput);
            case TOOL_FILE_WRITE -> evaluateFileWrite(normalizedInput);
            case TOOL_FILE_SEARCH -> evaluateFileSearch(normalizedInput);
            case TOOL_GREP_SEARCH -> evaluateGrepSearch(normalizedInput);
            case TOOL_TERMINAL_EXEC -> evaluateTerminalExec(normalizedInput);
            case TOOL_WEB_SEARCH -> evaluateWebSearch(normalizedInput);
            case TOOL_WEB_READ -> evaluateWebRead(normalizedInput);
            default -> ToolPolicyDecision.allow(normalizedInput);
        };
    }

    /**
     * 获取工作空间根路径。
     */
    private Path getWorkspaceRoot() {
        String configuredRoot = properties.getWorkspaceRoot();
        if (configuredRoot != null && !configuredRoot.isBlank()) {
            return Paths.get(configuredRoot).toAbsolutePath().normalize();
        }
        Path current = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path workspaceRoot = findWorkspaceRootInAncestors(current);
        if (workspaceRoot != null) {
            return workspaceRoot;
        }
        Path nestedWorkspaceRoot = findUniqueNestedWorkspaceRoot(current);
        return nestedWorkspaceRoot == null ? current : nestedWorkspaceRoot;
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

    private ToolPolicyDecision evaluateFileWrite(Map<String, Object> input) {
        String rawPath = asString(input.get("path"));
        if (rawPath == null || rawPath.isBlank()) {
            return ToolPolicyDecision.deny("file_write requires path");
        }
        if (!input.containsKey("content")) {
            return ToolPolicyDecision.deny("file_write requires content");
        }
        String content = asString(input.get("content"));
        if (content == null) {
            return ToolPolicyDecision.deny("file_write content cannot be null");
        }
        if (content.indexOf('\0') >= 0) {
            return ToolPolicyDecision.deny("file_write content contains invalid null characters");
        }
        if (content.length() > properties.getMaxWriteCharacters()) {
            return ToolPolicyDecision.deny("file_write content exceeds maxWriteCharacters");
        }

        Path targetPath = resolveWorkspacePath(rawPath, false);
        if (targetPath == null) {
            return ToolPolicyDecision.deny("file_write path must stay inside workspace");
        }
        if (hasBlockedWriteExtension(targetPath)) {
            return ToolPolicyDecision.deny("file_write only supports text-like files");
        }
        if (Files.exists(targetPath) && Files.isDirectory(targetPath)) {
            return ToolPolicyDecision.deny("file_write path points to a directory");
        }

        boolean append = asBoolean(input.get("append"), false);
        boolean overwrite = asBoolean(input.get("overwrite"), false);
        boolean createDirectories = asBoolean(input.get("createDirectories"), true);
        if (append && overwrite) {
            return ToolPolicyDecision.deny("file_write cannot append and overwrite at the same time");
        }
        if (Files.exists(targetPath) && !append && !overwrite) {
            return ToolPolicyDecision.deny("file_write refuses to replace existing file unless overwrite=true or append=true");
        }

        Path parent = targetPath.getParent();
        if (parent != null && Files.exists(parent) && !Files.isDirectory(parent)) {
            return ToolPolicyDecision.deny("file_write parent path is not a directory");
        }
        if (parent != null && !Files.exists(parent) && !createDirectories) {
            return ToolPolicyDecision.deny("file_write parent directory does not exist");
        }

        input.put("path", targetPath.toString());
        input.put("content", content);
        input.put("append", append);
        input.put("overwrite", overwrite);
        input.put("createDirectories", createDirectories);
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

    private ToolPolicyDecision evaluateTerminalExec(Map<String, Object> input) {
        List<String> command = toCommandList(input.get("command"));
        if (command.isEmpty()) {
            return ToolPolicyDecision.deny("terminal_exec requires command");
        }
        String executableToken = command.get(0);
        if (containsPathExpression(executableToken)) {
            return ToolPolicyDecision.deny("terminal_exec only accepts executable names, not shell paths");
        }
        if (containsCommandOperators(command)) {
            return ToolPolicyDecision.deny("terminal_exec does not support shell operators or multiline commands");
        }

        String executableName = normalizeExecutableName(executableToken);
        if (SHELL_EXECUTABLES.contains(executableName)) {
            return ToolPolicyDecision.deny("terminal_exec does not allow shell executables");
        }
        if (isBlockedTerminalCommand(executableName)) {
            return ToolPolicyDecision.deny("terminal_exec command is blocked by policy: " + executableName);
        }

        String rawWorkdir = defaultIfBlank(asString(input.get("workdir")), getWorkspaceRoot().toString());
        Path workdir = resolveWorkspacePath(rawWorkdir, true);
        if (workdir == null || !Files.isDirectory(workdir)) {
            return ToolPolicyDecision.deny("terminal_exec workdir must be a directory inside workspace");
        }

        int timeoutSeconds = clamp(
                asInt(input.get("timeoutSeconds"), properties.getTerminalTimeoutSeconds()),
                1,
                Math.max(1, properties.getTerminalTimeoutSeconds())
        );

        input.put("command", List.copyOf(command));
        input.put("workdir", workdir.toString());
        input.put("timeoutSeconds", timeoutSeconds);
        return ToolPolicyDecision.allow(input);
    }

    private ToolPolicyDecision evaluateWebSearch(Map<String, Object> input) {
        String query = asString(input.get("query"));
        if (query == null || query.isBlank()) {
            return ToolPolicyDecision.deny("web_search requires query");
        }
        if (!isAllowedHost(WEB_SEARCH_HOST)) {
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

    private boolean isBlockedTerminalCommand(String executableName) {
        List<String> blockedTerminalCommands = properties.getBlockedTerminalCommands();
        if (blockedTerminalCommands == null || blockedTerminalCommands.isEmpty()) {
            return false;
        }
        for (String blockedCommand : blockedTerminalCommands) {
            if (executableName.equals(normalizeExecutableName(blockedCommand))) {
                return true;
            }
        }
        return false;
    }

    private Path findWorkspaceRootInAncestors(Path start) {
        for (Path candidate = start; candidate != null; candidate = candidate.getParent()) {
            if (looksLikeWorkspaceRoot(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        return null;
    }

    private Path findUniqueNestedWorkspaceRoot(Path start) {
        try (var childStream = Files.list(start)) {
            List<Path> candidates = childStream
                    .filter(Files::isDirectory)
                    .filter(this::looksLikeWorkspaceRoot)
                    .limit(2)
                    .toList();
            return candidates.size() == 1 ? candidates.get(0).toAbsolutePath().normalize() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean looksLikeWorkspaceRoot(Path candidate) {
        return Files.isDirectory(candidate.resolve(".git"))
                || (Files.isDirectory(candidate.resolve("javalab-agent-back"))
                && Files.isDirectory(candidate.resolve("javalab-agent-front")));
    }

    private boolean containsCommandOperators(List<String> command) {
        for (String token : command) {
            if (token == null) {
                continue;
            }
            String trimmed = token.trim();
            if (trimmed.contains("\n")
                    || trimmed.contains("\r")
                    || trimmed.contains("&&")
                    || trimmed.contains("||")
                    || trimmed.contains("|")
                    || trimmed.contains(">")
                    || trimmed.contains("<")
                    || ";".equals(trimmed)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsPathExpression(String executableToken) {
        return executableToken.contains("/")
                || executableToken.contains("\\")
                || executableToken.contains(":");
    }

    private boolean hasBlockedWriteExtension(Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String blockedExtension : BLOCKED_WRITE_EXTENSIONS) {
            if (fileName.endsWith(blockedExtension)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeExecutableName(String executableToken) {
        if (executableToken == null) {
            return "";
        }
        String normalized = executableToken.trim().toLowerCase(Locale.ROOT);
        for (String suffix : List.of(".exe", ".cmd", ".bat", ".com")) {
            if (normalized.endsWith(suffix)) {
                return normalized.substring(0, normalized.length() - suffix.length());
            }
        }
        return normalized;
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

    private List<String> toCommandList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> tokens = new ArrayList<>();
            for (Object item : list) {
                if (item == null) {
                    continue;
                }
                String text = String.valueOf(item).trim();
                if (!text.isBlank()) {
                    tokens.add(text);
                }
            }
            return tokens;
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return List.of();
        }
        return tokenizeCommandLine(text);
    }

    private List<String> tokenizeCommandLine(String commandLine) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char activeQuote = 0;
        for (int i = 0; i < commandLine.length(); i++) {
            char currentChar = commandLine.charAt(i);
            if (activeQuote != 0) {
                if (currentChar == activeQuote) {
                    activeQuote = 0;
                } else {
                    current.append(currentChar);
                }
                continue;
            }
            if (currentChar == '"' || currentChar == '\'') {
                activeQuote = currentChar;
                continue;
            }
            if (Character.isWhitespace(currentChar)) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(currentChar);
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
