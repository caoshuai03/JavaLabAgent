package com.cs.rag.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;

@Slf4j
@Component
public class McpProtocolSupport {

    private final ObjectMapper objectMapper;

    public McpProtocolSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // 从 SSE 握手流里提取真正的 JSON-RPC 地址。
    public String readEndpointFromSseStream(BufferedReader reader, String baseUrl) throws IOException {
        boolean isEndpointEvent = false;
        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            log.debug("Read SSE handshake line: {}", trimmed);

            if (trimmed.equals("event: endpoint") || trimmed.equals("event:endpoint")) {
                isEndpointEvent = true;
                continue;
            }

            if (isEndpointEvent && trimmed.startsWith("data:")) {
                String data = trimmed.substring(5).trim();
                if (data.startsWith("http://") || data.startsWith("https://")) {
                    return data;
                }
                try {
                    URI base = URI.create(baseUrl);
                    String authority = base.getScheme() + "://" + base.getAuthority();
                    return authority + (data.startsWith("/") ? data : "/" + data);
                } catch (Exception e) {
                    log.warn("Build SSE endpoint url failed: base={}, data={}", baseUrl, data);
                    return data;
                }
            }

            if (isEndpointEvent && (trimmed.isEmpty() || trimmed.startsWith("event:"))) {
                isEndpointEvent = false;
            }
        }
        return null;
    }

    // 解析标准 JSON-RPC 响应体。
    public JsonNode parseJsonRpcResponse(String serverName, String jsonContent) {
        try {
            JsonNode responseNode = objectMapper.readTree(jsonContent);
            if (responseNode.has("error")) {
                JsonNode error = responseNode.get("error");
                log.warn("MCP[{}] response error: code={}, message={}",
                        serverName, error.path("code").asInt(), error.path("message").asText());
                return null;
            }
            return responseNode.path("result");
        } catch (Exception e) {
            log.warn("Parse JSON-RPC response failed for [{}]: {}", serverName, e.getMessage());
            return null;
        }
    }

    // 解析包在 SSE message 事件里的结果。
    public JsonNode parseSseMessageResponse(String serverName, String sseBody, int requestId) {
        String[] lines = sseBody.split("\n");
        boolean nextIsMessage = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals("event: message") || trimmed.equals("event:message")) {
                nextIsMessage = true;
                continue;
            }
            if (nextIsMessage && trimmed.startsWith("data:")) {
                String data = trimmed.substring(5).trim();
                try {
                    JsonNode node = objectMapper.readTree(data);
                    if (!node.has("id") || node.path("id").asInt() == requestId) {
                        if (node.has("error")) {
                            JsonNode error = node.get("error");
                            log.warn("MCP SSE[{}] response error: code={}, message={}",
                                    serverName, error.path("code").asInt(), error.path("message").asText());
                            return null;
                        }
                        return node.path("result");
                    }
                } catch (Exception e) {
                    log.debug("Parse SSE message failed for [{}]: {}", serverName, e.getMessage());
                }
                nextIsMessage = false;
            }
            if (nextIsMessage && (trimmed.isEmpty() || trimmed.startsWith("event:"))) {
                nextIsMessage = false;
            }
        }
        log.warn("MCP SSE[{}] no matched message response, requestId={}", serverName, requestId);
        return null;
    }
}
