package com.cs.rag.service.impl;

import com.cs.rag.service.PromptService;
import com.cs.rag.skill.SkillInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class PromptServiceImpl implements PromptService {

    private final PromptRegistry promptRegistry;

    public PromptServiceImpl(PromptRegistry promptRegistry) {
        this.promptRegistry = promptRegistry;
    }

    @Override
    public String getRagAnswerSystemPrompt() {
        return promptRegistry.get(PromptRegistry.RAG_ANSWER_SYSTEM);
    }

    @Override
    public String buildRagUserMessage(String userMessage, String knowledgeBlock) {
        if (knowledgeBlock == null || knowledgeBlock.isBlank()) {
            return userMessage;
        }
        String template = promptRegistry.get(PromptRegistry.RAG_USER_MESSAGE);
        return renderTemplate(template, Map.of(
                "USER_MESSAGE", userMessage,
                "KNOWLEDGE_BLOCK", knowledgeBlock.trim()
        ));
    }

    @Override
    public String getSummarySystemPrompt() {
        return promptRegistry.get(PromptRegistry.SUMMARY_SYSTEM);
    }

    @Override
    public String getReactPlanSystemPrompt() {
        return promptRegistry.get(PromptRegistry.REACT_PLAN_SYSTEM);
    }

    @Override
    public String getReactAnswerSystemPrompt() {
        return promptRegistry.get(PromptRegistry.REACT_ANSWER_SYSTEM);
    }

    @Override
    public String buildReactPlanUserPrompt(String toolList,
                                           String userMessage,
                                           String observations,
                                           List<SkillInfo> matchedSkills,
                                           int round) {
        String template = promptRegistry.get(PromptRegistry.REACT_PLAN_USER);
        String skillsReference = buildSkillsReference(matchedSkills);
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("ROUND", String.valueOf(round));
        variables.put("TOOL_LIST", toolList);
        variables.put("USER_MESSAGE", userMessage);
        variables.put("OBSERVATIONS", observations);
        variables.put("SKILLS_REFERENCE", skillsReference);
        return renderTemplate(template, variables);
    }

    private String buildSkillsReference(List<SkillInfo> matchedSkills) {
        if (matchedSkills == null || matchedSkills.isEmpty()) {
            return "无";
        }

        String template = promptRegistry.get(PromptRegistry.REACT_SKILLS_FRAGMENT);
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
        return renderTemplate(template, Map.of("SKILLS_CONTENT", skillsContent.toString().trim()));
    }

    private String renderTemplate(String template, Map<String, String> variables) {
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
