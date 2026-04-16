package com.cs.rag.controller;

import com.cs.rag.common.ApplicationConstant;
import com.cs.rag.service.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    /**
     * 获取所有 Skills 列表（仅返回元数据）
     */
    @Operation(summary = "listSkills", description = "获取所有 Skills 列表")
    @GetMapping
    public Map<String, Object> listSkills() {
        return skillService.listSkillSummaries();
    }

    /**
     * 获取指定 Skill 的完整信息（包含 Markdown 内容）
     */
    @Operation(summary = "getSkill", description = "获取指定 Skill 的完整信息")
    @GetMapping("/{name}")
    public Map<String, Object> getSkill(@PathVariable String name) {
        return skillService.getSkillDetail(name);
    }

    /**
     * 刷新 Skills（重新扫描目录）
     */
    @Operation(summary = "refreshSkills", description = "刷新 Skills 列表")
    @PostMapping("/refresh")
    public Map<String, Object> refreshSkills() {
        return skillService.refreshSkillCatalog();
    }
}
