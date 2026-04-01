package com.cs.rag.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 完整的 Skill 信息
 * 包含元数据和完整的 Markdown 内容
 *
 * @author caoshuai
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillInfo {
    
    /**
     * Skill 元数据
     */
    private SkillMetadata metadata;
    
    /**
     * Skill 的完整 Markdown 内容（不含 frontmatter）
     */
    private String content;
    
    /**
     * Skill 文件夹路径
     */
    private String folderPath;
    
    /**
     * 额外资源文件列表（scripts, references, assets 等）
     */
    private List<String> resources;
}
