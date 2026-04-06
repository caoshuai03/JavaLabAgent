package com.cs.rag.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class PromptRegistry {

    public static final String RAG_ANSWER_SYSTEM = "prompts/rag/rag-answer-system.md";
    public static final String RAG_USER_MESSAGE = "prompts/rag/rag-user-message.md";
    public static final String SUMMARY_SYSTEM = "prompts/summary/summary-system.md";
    public static final String REACT_PLAN_SYSTEM = "prompts/react/react-plan-system.md";
    public static final String REACT_ANSWER_SYSTEM = "prompts/react/react-answer-system.md";
    public static final String REACT_PLAN_USER = "prompts/react/react-plan-user.md";
    public static final String REACT_SKILLS_FRAGMENT = "prompts/react/react-skills-fragment.md";

    private final Map<String, String> promptCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void preload() {
        preloadPrompt(RAG_ANSWER_SYSTEM);
        preloadPrompt(RAG_USER_MESSAGE);
        preloadPrompt(SUMMARY_SYSTEM);
        preloadPrompt(REACT_PLAN_SYSTEM);
        preloadPrompt(REACT_ANSWER_SYSTEM);
        preloadPrompt(REACT_PLAN_USER);
        preloadPrompt(REACT_SKILLS_FRAGMENT);
    }

    public String get(String location) {
        return promptCache.computeIfAbsent(location, this::load);
    }

    private String load(String location) {
        ClassPathResource resource = new ClassPathResource(location);
        if (!resource.exists()) {
            log.error("Read prompt file failed: classpath resource not found: {}", location);
            return "";
        }
        try {
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            return stripPromptHeader(content);
        } catch (IOException e) {
            log.error("Read prompt file failed: {}", location, e);
            return "";
        }
    }

    private void preloadPrompt(String location) {
        String content = load(location);
        if (content == null || content.isBlank()) {
            return;
        }
        promptCache.put(location, content);
    }

    private String stripPromptHeader(String content) {
        String[] lines = content.split("\n");
        StringBuilder result = new StringBuilder();
        boolean foundContent = false;

        for (String line : lines) {
            String trimmedLine = line.trim();
            if (!foundContent) {
                if (trimmedLine.startsWith("#") || trimmedLine.isEmpty()) {
                    continue;
                }
                foundContent = true;
            }
            result.append(line).append("\n");
        }

        return result.toString().trim();
    }
}
