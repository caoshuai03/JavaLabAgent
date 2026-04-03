package com.cs.rag.service;

import com.cs.rag.skill.SkillInfo;

import java.util.List;

public interface PromptService {
    
    String getChatDefaultPrompt();
    
    String getChatSummaryPrompt();
    
    String getReactAgentPrompt();
    
    String getReactAgentFinalPrompt();
    
    /**
     * 构建规划阶段使用的用户提示词。
     * 将工具列表、结构化观察结果和技能参考放到 UserMessage，避免污染 SystemMessage。
     */
    String buildReactUserPrompt(String toolList,
                                String userMessage,
                                String observations,
                                List<SkillInfo> matchedSkills,
                                int round);
}
