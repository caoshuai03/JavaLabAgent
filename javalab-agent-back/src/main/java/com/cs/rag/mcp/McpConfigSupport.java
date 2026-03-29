package com.cs.rag.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;

@Slf4j
@Component
public class McpConfigSupport {

    private final ObjectMapper objectMapper;

    public McpConfigSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // 配置文件读取单独下沉，避免管理类同时处理文件系统细节。
    public McpToolsConfig loadConfig(Path externalPath, String classpathConfigFile) {
        try {
            String jsonContent = null;

            if (Files.exists(externalPath)) {
                jsonContent = Files.readString(externalPath, StandardCharsets.UTF_8);
                log.info("Load MCP config from external file: {}", externalPath.toAbsolutePath());
            }

            if (jsonContent == null) {
                ClassPathResource resource = new ClassPathResource(classpathConfigFile);
                if (resource.exists()) {
                    try (InputStream inputStream = resource.getInputStream()) {
                        jsonContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                        log.info("Load MCP config from classpath: {}", classpathConfigFile);
                    }
                }
            }

            if (jsonContent == null || jsonContent.isBlank()) {
                return emptyConfig();
            }

            McpToolsConfig config = objectMapper.readValue(jsonContent, McpToolsConfig.class);
            normalizeConfig(config);
            return config;
        } catch (Exception e) {
            log.error("Load MCP config failed: {}", e.getMessage(), e);
            return emptyConfig();
        }
    }

    // 保存逻辑集中后，后续切换存储方式时改动面更小。
    public void saveConfig(Path externalPath, McpToolsConfig config) {
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config);
            Files.writeString(externalPath, json, StandardCharsets.UTF_8);
            log.info("Save MCP config to: {}", externalPath.toAbsolutePath());
        } catch (Exception e) {
            log.error("Save MCP config failed: {}", e.getMessage(), e);
            throw new RuntimeException("Save MCP config failed: " + e.getMessage(), e);
        }
    }

    // 兼容旧配置，自动补齐缺省 type 字段。
    public void normalizeConfig(McpToolsConfig config) {
        if (config == null) {
            return;
        }
        if (config.getMcpServers() == null) {
            config.setMcpServers(new LinkedHashMap<>());
            return;
        }
        config.getMcpServers().forEach((name, serverConfig) -> {
            if (serverConfig == null) {
                return;
            }
            if ((serverConfig.getType() == null || serverConfig.getType().isBlank())
                    && serverConfig.getUrl() != null && !serverConfig.getUrl().isBlank()) {
                serverConfig.setType("http");
                log.info("MCP server [{}] auto detect type=http", name);
            }
            if (serverConfig.getUrl() != null
                    && serverConfig.getUrl().toLowerCase(Locale.ROOT).contains("/sse")) {
                serverConfig.setType("sse");
                log.info("MCP server [{}] auto detect type=sse from url={}", name, serverConfig.getUrl());
            }
        });
    }

    private McpToolsConfig emptyConfig() {
        McpToolsConfig config = new McpToolsConfig();
        config.setMcpServers(new LinkedHashMap<>());
        return config;
    }
}
