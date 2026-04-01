package com.cs.rag.service.impl;

import com.cs.rag.service.PromptService;
import com.cs.rag.skill.SkillInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
public class PromptServiceImpl implements PromptService {

    @Value("classpath:/prompts/chat-default.md")
    private Resource chatDefaultPrompt;

    @Value("classpath:/prompts/chat-summary.md")
    private Resource chatSummaryPrompt;

    @Value("classpath:/prompts/react-system-prompt.md")
    private Resource reactAgentPrompt;

    @Value("classpath:/prompts/react-agent-final-prompt.md")
    private Resource reactAgentFinalPrompt;

    @Value("classpath:/prompts/skills-injection-template.md")
    private Resource skillsInjectionTemplate;

    @Value("classpath:/prompts/react-user-prompt-template.md")
    private Resource reactUserPromptTemplate;

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

    @Override
    public String buildReactAgentPromptWithSkills(List<SkillInfo> matchedSkills) {
        String basePrompt = getReactAgentPrompt();
        
        if (matchedSkills == null || matchedSkills.isEmpty()) {
            return basePrompt;
        }

        String template = readFile(skillsInjectionTemplate);
        
        StringBuilder skillsContent = new StringBuilder();
        for (SkillInfo skill : matchedSkills) {
            skillsContent.append("\n### ").append(skill.getMetadata().getName()).append("\n");
            if (skill.getMetadata().getDescription() != null) {
                skillsContent.append("**说明**: ").append(skill.getMetadata().getDescription()).append("\n\n");
            }
            skillsContent.append(skill.getContent()).append("\n\n");
            skillsContent.append("---\n");
        }

        String skillsSection = template.replace("{SKILLS_CONTENT}", skillsContent.toString());
        
        log.info("Enhanced ReAct prompt with {} skills", matchedSkills.size());
        return basePrompt + "\n\n" + skillsSection;
    }

    @Override
    public String buildReactUserPrompt(String toolList, String userMessage, String observations) {
        String template = readFile(reactUserPromptTemplate);
        return template
                .replace("{TOOL_LIST}", toolList)
                .replace("{USER_MESSAGE}", userMessage)
                .replace("{OBSERVATIONS}", observations);
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
