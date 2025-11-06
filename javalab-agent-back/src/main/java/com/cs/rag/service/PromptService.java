package com.cs.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author  caoshuai
 * @date 2025/11/06 18:29
 * @description: Prompt 管理服务，统一管理所有提示词
 */
@Slf4j
@Service
public class PromptService {

    @Value("classpath:/prompts/chat-default.md")
    private Resource chatDefaultPrompt;

    /**
     * 获取默认对话提示词
     */
    public String getChatDefaultPrompt() {
        return readFile(chatDefaultPrompt);
    }

    /**
     * 读取文件内容，去除 Markdown 标题和描述行
     */
    private String readFile(Resource resource) {
        try {
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            String[] lines = content.split("\n");
            StringBuilder result = new StringBuilder();
            boolean foundContent = false;
            
            for (String line : lines) {
                String trimmedLine = line.trim();
                if (trimmedLine.startsWith("#") || trimmedLine.startsWith("用于")) {
                    continue;
                }
                if (!foundContent && trimmedLine.isEmpty()) {
                    continue;
                }
                foundContent = true;
                result.append(line).append("\n");
            }
            
            return result.toString().trim();
        } catch (IOException e) {
            log.error("读取 prompt 文件失败: {}", resource.getFilename(), e);
            return "";
        }
    }
}

