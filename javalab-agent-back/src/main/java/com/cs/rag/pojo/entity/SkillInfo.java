package com.cs.rag.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 完整的 Skill 信息。
 * 包含元数据、正文内容以及附属资源。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillInfo {

    /**
     * Skill 元数据。
     */
    private SkillMetadata metadata;

    /**
     * Skill 的完整 Markdown 内容（不含 frontmatter）。
     */
    private String content;

    /**
     * Skill 文件夹路径。
     */
    private String folderPath;

    /**
     * Skill 额外资源文件列表（scripts、references、assets 等）。
     */
    private List<String> resources;
}
