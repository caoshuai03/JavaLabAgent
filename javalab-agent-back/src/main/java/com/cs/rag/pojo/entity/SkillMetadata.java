package com.cs.rag.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Skill 元数据。
 * 对应 SKILL.md 文件中的 YAML frontmatter。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillMetadata {

    /**
     * 技能名称。
     */
    private String name;

    /**
     * 技能描述。
     */
    private String description;

    /**
     * 触发关键词列表。
     */
    private List<String> triggerKeywords;

    /**
     * 版本号。
     */
    private String version;

    /**
     * 作者。
     */
    private String author;

    /**
     * 许可证。
     */
    private String license;
}
