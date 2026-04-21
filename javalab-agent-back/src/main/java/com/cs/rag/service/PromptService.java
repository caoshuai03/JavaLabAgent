package com.cs.rag.service;

import com.cs.rag.pojo.entity.SkillInfo;

import java.util.List;

public interface PromptService {

    String getRagAnswerSystemPrompt();

    String buildRagUserMessage(String userMessage, String knowledgeBlock);

    String getSummarySystemPrompt();

    String getReactPlanSystemPrompt();

    String getReactAnswerSystemPrompt();

    /**
     * 构建历史会话摘要的 SystemMessage 内容。
     * 将摘要填入模板，避免在代码中硬编码提示词。
     */
    String buildContextSummaryMessage(String summary);

    /**
     * 构建规划阶段使用的用户提示词。
     * 将工具列表、结构化观察结果和技能参考放到 UserMessage，避免污染 SystemMessage。
     */
    String buildReactPlanUserPrompt(String toolList,
                                    String userMessage,
                                    String observations,
                                    List<SkillInfo> matchedSkills,
                                    int round);
}
