package com.cs.rag.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Skill 匹配服务
 * 根据用户消息和触发关键词匹配相关的 Skills
 *
 * @author caoshuai
 */
@Slf4j
@Service
public class SkillMatchService {

    private static final int MAX_SKILLS_PER_REQUEST = 3;

    private final SkillScanner skillScanner;

    public SkillMatchService(SkillScanner skillScanner) {
        this.skillScanner = skillScanner;
    }

    /**
     * 根据用户消息匹配相关 Skills
     * 
     * @param userMessage 用户消息
     * @return 匹配的 Skills 列表（最多 MAX_SKILLS_PER_REQUEST 个）
     */
    public List<SkillInfo> matchSkills(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return List.of();
        }

        String messageLower = userMessage.toLowerCase();
        List<SkillInfo> allSkills = skillScanner.getAllSkills();
        
        if (allSkills.isEmpty()) {
            return List.of();
        }

        // 匹配逻辑：检查用户消息是否包含任何触发关键词
        List<SkillMatch> matches = new ArrayList<>();
        
        for (SkillInfo skill : allSkills) {
            List<String> triggerKeywords = skill.getMetadata().getTriggerKeywords();
            
            if (triggerKeywords == null || triggerKeywords.isEmpty()) {
                continue;
            }

            // 计算匹配分数：匹配的关键词数量
            int matchScore = 0;
            List<String> matchedKeywords = new ArrayList<>();
            
            for (String keyword : triggerKeywords) {
                if (keyword != null && !keyword.isBlank()) {
                    String keywordLower = keyword.toLowerCase();
                    if (messageLower.contains(keywordLower)) {
                        matchScore++;
                        matchedKeywords.add(keyword);
                    }
                }
            }

            if (matchScore > 0) {
                matches.add(new SkillMatch(skill, matchScore, matchedKeywords));
            }
        }

        // 按匹配分数排序，取前 N 个
        List<SkillInfo> result = matches.stream()
                .sorted((m1, m2) -> Integer.compare(m2.score, m1.score))
                .limit(MAX_SKILLS_PER_REQUEST)
                .map(m -> {
                    log.info("Skill matched: {} (score={}, keywords={})", 
                            m.skill.getMetadata().getName(), m.score, m.matchedKeywords);
                    return m.skill;
                })
                .collect(Collectors.toList());

        log.info("Total {} skills matched for message: '{}'", result.size(), 
                userMessage.length() > 50 ? userMessage.substring(0, 50) + "..." : userMessage);
        
        return result;
    }

    /**
     * 内部类：Skill 匹配结果
     */
    private static class SkillMatch {
        final SkillInfo skill;
        final int score;
        final List<String> matchedKeywords;

        SkillMatch(SkillInfo skill, int score, List<String> matchedKeywords) {
            this.skill = skill;
            this.score = score;
            this.matchedKeywords = matchedKeywords;
        }
    }
}
