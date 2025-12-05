package com.cs.rag.service.impl;

import com.cs.rag.service.PromptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 提示词管理服务实现类
 * 
 * <p>统一管理应用程序中使用的所有提示词模板，
 * 从资源文件中加载并处理提示词内容。</p>
 * 
 * <p>提示词文件存放位置: resources/prompts/</p>
 * 
 * @author caoshuai
 * @since 1.0
 */
@Slf4j
@Service
public class PromptServiceImpl implements PromptService {

    /** 默认对话提示词资源文件 */
    @Value("classpath:/prompts/chat-default.md")
    private Resource chatDefaultPrompt;

    /**
     * 获取默认对话提示词
     * 
     * @return 处理后的提示词内容
     */
    @Override
    public String getChatDefaultPrompt() {
        return readFile(chatDefaultPrompt);
    }

    /**
     * 读取提示词文件内容
     * 
     * <p>处理逻辑:</p>
     * <ol>
     *   <li>读取Markdown格式的提示词文件</li>
     *   <li>去除标题行（以#开头）</li>
     *   <li>去除描述行（以"用于"开头）</li>
     *   <li>返回纯净的提示词内容</li>
     * </ol>
     * 
     * @param resource 提示词资源文件
     * @return 处理后的文件内容，失败时返回空字符串
     */
    private String readFile(Resource resource) {
        try {
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            String[] lines = content.split("\n");
            StringBuilder result = new StringBuilder();
            boolean foundContent = false;
            
            for (String line : lines) {
                String trimmedLine = line.trim();
                // 跳过Markdown标题和描述行
                if (trimmedLine.startsWith("#") || trimmedLine.startsWith("用于")) {
                    continue;
                }
                // 跳过开头的空行
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
