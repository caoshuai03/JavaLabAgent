package com.cs.rag.service.impl;

import com.cs.rag.service.PromptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class PromptServiceImpl implements PromptService {

    @Value("classpath:/prompts/chat-default.md")
    private Resource chatDefaultPrompt;

    @Value("classpath:/prompts/chat-summary.md")
    private Resource chatSummaryPrompt;

    @Value("classpath:/prompts/react-system-prompt.md")
    private Resource reactAgentPrompt;

    @Value("classpath:/prompts/react-final-system-prompt.md")
    private Resource reactAgentFinalPrompt;

    @Override
    public String getChatDefaultPrompt() {
        return readFile(chatDefaultPrompt);
    }

    @Override
    public String getChatSummaryPrompt() {
        return readFile(chatSummaryPrompt);
    }

    @Override
    public String getReactAgentPrompt() {
        return readFile(reactAgentPrompt);
    }

    @Override
    public String getReactAgentFinalPrompt() {
        return readFile(reactAgentFinalPrompt);
    }

    // 允许 prompt 文件保留 markdown 标题，运行时自动跳过文件头说明。
    private String readFile(Resource resource) {
        try {
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
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
        } catch (IOException e) {
            log.error("Read prompt file failed: {}", resource.getFilename(), e);
            return "";
        }
    }
}
