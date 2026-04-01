package com.cs.rag.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * Skill 管理器
 * 负责初始化和管理 Skills 的生命周期
 *
 * @author caoshuai
 */
@Slf4j
@Component
public class SkillManager {

    @Value("${skills.directory:src/main/resources/skills}")
    private String skillsDirectory;

    private final SkillScanner skillScanner;

    public SkillManager(SkillScanner skillScanner) {
        this.skillScanner = skillScanner;
    }

    /**
     * 应用启动时自动扫描 Skills
     */
    @PostConstruct
    public void initialize() {
        log.info("Initializing Skills from directory: {}", skillsDirectory);
        refreshSkills();
    }

    /**
     * 刷新 Skills（重新扫描目录）
     * 
     * @return 扫描到的 Skills 数量
     */
    public int refreshSkills() {
        skillScanner.clearCache();
        List<SkillInfo> skills = skillScanner.scanSkills(skillsDirectory);
        log.info("Skills refresh completed. Total {} skills loaded.", skills.size());
        return skills.size();
    }

    /**
     * 获取所有 Skills
     */
    public List<SkillInfo> getAllSkills() {
        return skillScanner.getAllSkills();
    }

    /**
     * 根据名称获取单个 Skill
     */
    public SkillInfo getSkill(String name) {
        return skillScanner.getSkill(name);
    }

    /**
     * 获取最后扫描时间
     */
    public long getLastScanTime() {
        return skillScanner.getLastScanTime();
    }
}
