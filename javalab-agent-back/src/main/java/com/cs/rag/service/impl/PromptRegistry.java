package com.cs.rag.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class PromptRegistry {

    public static final String RAG_ANSWER_SYSTEM = "classpath:/prompts/rag/rag-answer-system.md";
    public static final String RAG_USER_MESSAGE = "classpath:/prompts/rag/rag-user-message.md";
    public static final String SUMMARY_SYSTEM = "classpath:/prompts/summary/summary-system.md";
    public static final String REACT_PLAN_SYSTEM = "classpath:/prompts/react/react-plan-system.md";
    public static final String REACT_ANSWER_SYSTEM = "classpath:/prompts/react/react-answer-system.md";
    public static final String REACT_PLAN_USER = "classpath:/prompts/react/react-plan-user.md";
    public static final String REACT_SKILLS_FRAGMENT = "classpath:/prompts/react/react-skills-fragment.md";

    private final ResourceLoader resourceLoader;
    private final Map<String, String> promptCache = new ConcurrentHashMap<>();

    public PromptRegistry(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void preload() {
        load(RAG_ANSWER_SYSTEM);
        load(RAG_USER_MESSAGE);
        load(SUMMARY_SYSTEM);
        load(REACT_PLAN_SYSTEM);
        load(REACT_ANSWER_SYSTEM);
        load(REACT_PLAN_USER);
        load(REACT_SKILLS_FRAGMENT);
        log.info("Prompt registry initialized with {} prompts", promptCache.size());
    }

    public String get(String location) {
        return promptCache.computeIfAbsent(location, this::load);
    }

    private String load(String location) {
        Resource resource = resourceLoader.getResource(location);
        try {
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            return stripPromptHeader(content);
        } catch (IOException e) {
            log.error("Read prompt file failed: {}", location, e);
            return "";
        }
    }

    private String stripPromptHeader(String content) {
        String[] lines = content.split("\n");
        StringBuilder result = new StringBuilder();
        boolean foundContent = false;

        for (String line : lines) {
            String trimmedLine = line.trim();
            if (!foundContent) {
                if (trimmedLine.startsWith("#") || trimmedLine.startsWith("用于") || trimmedLine.isEmpty()) {
                    continue;
                }
                foundContent = true;
            }
            result.append(line).append("\n");
        }

        return result.toString().trim();
    }
}
