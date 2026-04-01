package com.cs.rag.service;

import com.cs.rag.skill.SkillInfo;

import java.util.List;

public interface PromptService {
    
    String getChatDefaultPrompt();
    
    String getChatSummaryPrompt();
    
    String getReactAgentPrompt();
    
    String getReactAgentFinalPrompt();
    
    /**
     * 构建带 Skills 增强的 ReAct Agent 提示词
     * 
     * @param matchedSkills 匹配到的 Skills 列表
     * @return 增强后的提示词
     */
    String buildReactAgentPromptWithSkills(List<SkillInfo> matchedSkills);

    String buildReactUserPrompt(String toolList, String userMessage, String observations);
}
