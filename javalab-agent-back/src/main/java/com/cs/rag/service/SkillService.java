package com.cs.rag.service;

import com.cs.rag.pojo.entity.SkillInfo;

import java.util.List;
import java.util.Map;

/**
 * Skill 服务。
 * 统一负责 Skill 的扫描、查询和匹配，避免职责分散在多个薄类中。
 */
public interface SkillService {

    /**
     * 刷新 Skills（重新扫描目录）。
     *
     * @return 扫描到的 Skill 数量
     */
    int refreshSkills();

    /**
     * 获取所有 Skill。
     */
    List<SkillInfo> getAllSkills();

    /**
     * 根据名称获取单个 Skill。
     */
    SkillInfo getSkill(String name);

    /**
     * 获取最后一次扫描时间。
     */
    long getLastScanTime();

    /**
     * 根据用户消息匹配相关 Skill。
     */
    List<SkillInfo> matchSkills(String userMessage);

    /**
     * 获取供前端展示的 Skill 列表数据。
     */
    Map<String, Object> listSkillSummaries();

    /**
     * 获取单个 Skill 的详情数据。
     */
    Map<String, Object> getSkillDetail(String name);

    /**
     * 刷新 Skill 后返回标准结果。
     */
    Map<String, Object> refreshSkillCatalog();
}
