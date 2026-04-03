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

    @Value("classpath:/prompts/react-final-system-prompt.md")
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
    public String buildReactUserPrompt(String toolList,
                                       String userMessage,
                                       String observations,
                                       List<SkillInfo> matchedSkills,
                                       int round) {
        String template = readFile(reactUserPromptTemplate);
        String skillsReference = buildSkillsReference(matchedSkills);
        return template
                .replace("{ROUND}", String.valueOf(round))
                .replace("{TOOL_LIST}", toolList)
                .replace("{USER_MESSAGE}", userMessage)
                .replace("{OBSERVATIONS}", observations)
                .replace("{SKILLS_REFERENCE}", skillsReference);
    }

    private String buildSkillsReference(List<SkillInfo> matchedSkills) {
        if (matchedSkills == null || matchedSkills.isEmpty()) {
            return "无";
        }

        String template = readFile(skillsInjectionTemplate);
        StringBuilder skillsContent = new StringBuilder();
        for (SkillInfo skill : matchedSkills) {
            if (skill.getMetadata() == null) {
                continue;
            }
            skillsContent.append("- ").append(skill.getMetadata().getName());
            if (skill.getMetadata().getDescription() != null && !skill.getMetadata().getDescription().isBlank()) {
                skillsContent.append("：").append(skill.getMetadata().getDescription().trim());
            }
            List<String> triggerKeywords = skill.getMetadata().getTriggerKeywords();
            if (triggerKeywords != null && !triggerKeywords.isEmpty()) {
                skillsContent.append("；触发词=").append(String.join(", ", triggerKeywords));
            }
            skillsContent.append("\n");
        }
        if (skillsContent.isEmpty()) {
            return "无";
        }
        log.info("Prepared {} skill references for ReAct user prompt", matchedSkills.size());
        return template.replace("{SKILLS_CONTENT}", skillsContent.toString().trim());
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
