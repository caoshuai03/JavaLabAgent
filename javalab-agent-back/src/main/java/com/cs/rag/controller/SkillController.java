package com.cs.rag.controller;

import com.cs.rag.common.ApplicationConstant;
import com.cs.rag.skill.SkillInfo;
import com.cs.rag.skill.SkillManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Skills 管理控制器
 * 提供 Skills 的只读查询接口
 *
 * @author caoshuai
 */
@Tag(name = "SkillController", description = "Skills 管理接口")
@Slf4j
@RestController
@RequestMapping(ApplicationConstant.API_VERSION + "/skills")
public class SkillController {

    private final SkillManager skillManager;

    public SkillController(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    /**
     * 获取所有 Skills 列表（仅返回元数据）
     */
    @Operation(summary = "listSkills", description = "获取所有 Skills 列表")
    @GetMapping
    public Map<String, Object> listSkills() {
        List<SkillInfo> allSkills = skillManager.getAllSkills();
        
        List<Map<String, Object>> skillList = new ArrayList<>();
        for (SkillInfo skill : allSkills) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", skill.getMetadata().getName());
            item.put("description", skill.getMetadata().getDescription());
            item.put("triggerKeywords", skill.getMetadata().getTriggerKeywords());
            item.put("version", skill.getMetadata().getVersion());
            item.put("author", skill.getMetadata().getAuthor());
            item.put("license", skill.getMetadata().getLicense());
            item.put("hasResources", skill.getResources() != null && !skill.getResources().isEmpty());
            item.put("resourceCount", skill.getResources() != null ? skill.getResources().size() : 0);
            skillList.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skills", skillList);
        result.put("total", skillList.size());
        result.put("lastScanTime", skillManager.getLastScanTime());
        
        return result;
    }

    /**
     * 获取指定 Skill 的完整信息（包含 Markdown 内容）
     */
    @Operation(summary = "getSkill", description = "获取指定 Skill 的完整信息")
    @GetMapping("/{name}")
    public Map<String, Object> getSkill(@PathVariable String name) {
        SkillInfo skill = skillManager.getSkill(name);
        
        if (skill == null) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "Skill not found: " + name);
            return error;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("name", skill.getMetadata().getName());
        result.put("description", skill.getMetadata().getDescription());
        result.put("triggerKeywords", skill.getMetadata().getTriggerKeywords());
        result.put("version", skill.getMetadata().getVersion());
        result.put("author", skill.getMetadata().getAuthor());
        result.put("license", skill.getMetadata().getLicense());
        result.put("content", skill.getContent());
        result.put("folderPath", skill.getFolderPath());
        result.put("resources", skill.getResources());
        
        return result;
    }

    /**
     * 刷新 Skills（重新扫描目录）
     */
    @Operation(summary = "refreshSkills", description = "刷新 Skills 列表")
    @PostMapping("/refresh")
    public Map<String, Object> refreshSkills() {
        try {
            int count = skillManager.refreshSkills();
            log.info("Skills refreshed successfully. Total: {}", count);
            return Map.of(
                "success", true, 
                "message", "刷新成功，发现 " + count + " 个 Skills",
                "total", count,
                "lastScanTime", skillManager.getLastScanTime()
            );
        } catch (Exception e) {
            log.error("Failed to refresh skills", e);
            return Map.of(
                "success", false, 
                "message", "刷新失败: " + e.getMessage()
            );
        }
    }
}
