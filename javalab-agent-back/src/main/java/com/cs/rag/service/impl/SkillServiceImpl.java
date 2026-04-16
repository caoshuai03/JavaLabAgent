package com.cs.rag.service.impl;

import com.cs.rag.pojo.entity.SkillInfo;
import com.cs.rag.pojo.entity.SkillMetadata;
import com.cs.rag.service.SkillService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Skill 服务实现。
 * 将原来的管理、扫描、匹配逻辑收敛到一个实现类中，减少不必要的中间跳转。
 */
@Slf4j
@Service
public class SkillServiceImpl implements SkillService {

    /** 单次请求最多加载的 Skill 数量，避免提示词过长。 */
    private static final int MAX_SKILLS_PER_REQUEST = 3;

    /** Skill 定义文件名。 */
    private static final String SKILL_FILE_NAME = "SKILL.md";

    /** Frontmatter 分隔符。 */
    private static final String FRONTMATTER_DELIMITER = "---";

    /** Skill 附属资源目录。 */
    private static final String[] RESOURCE_DIRECTORIES = {"scripts", "references", "assets"};

    @Value("${skills.directory:src/main/resources/skills}")
    private String skillsDirectory;

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    /** Skill 缓存，key 为 Skill 名称。 */
    private final Map<String, SkillInfo> skillCache = new ConcurrentHashMap<>();

    /** 最近一次扫描时间。 */
    private volatile long lastScanTime;

    @PostConstruct
    public void initialize() {
        refreshSkills();
    }

    @Override
    public int refreshSkills() {
        skillCache.clear();

        List<SkillInfo> skills = scanSkillsFromClasspath(skillsDirectory);
        if (skills.isEmpty()) {
            skills = scanSkillsFromFileSystem(skillsDirectory);
        }

        lastScanTime = System.currentTimeMillis();
        log.info("Skills 扫描完成，共加载 {} 个 Skill", skills.size());
        return skills.size();
    }

    @Override
    public List<SkillInfo> getAllSkills() {
        return new ArrayList<>(skillCache.values());
    }

    @Override
    public SkillInfo getSkill(String name) {
        return skillCache.get(name);
    }

    @Override
    public long getLastScanTime() {
        return lastScanTime;
    }

    @Override
    public List<SkillInfo> matchSkills(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return List.of();
        }

        List<SkillInfo> allSkills = getAllSkills();
        if (allSkills.isEmpty()) {
            return List.of();
        }

        String messageLower = userMessage.toLowerCase(Locale.ROOT);
        List<SkillMatchResult> matches = new ArrayList<>();
        for (SkillInfo skill : allSkills) {
            SkillMetadata metadata = skill.getMetadata();
            if (metadata == null || metadata.getTriggerKeywords() == null || metadata.getTriggerKeywords().isEmpty()) {
                continue;
            }

            int score = 0;
            List<String> matchedKeywords = new ArrayList<>();
            for (String keyword : metadata.getTriggerKeywords()) {
                if (keyword == null || keyword.isBlank()) {
                    continue;
                }
                if (messageLower.contains(keyword.toLowerCase(Locale.ROOT))) {
                    score++;
                    matchedKeywords.add(keyword);
                }
            }

            if (score > 0) {
                matches.add(new SkillMatchResult(skill, score, matchedKeywords));
            }
        }

        List<SkillInfo> result = matches.stream()
                .sorted((left, right) -> Integer.compare(right.score(), left.score()))
                .limit(MAX_SKILLS_PER_REQUEST)
                .map(match -> {
                    log.info("Skill 命中: {} (score={}, keywords={})",
                            match.skill().getMetadata().getName(), match.score(), match.matchedKeywords());
                    return match.skill();
                })
                .toList();

        log.info("本次消息共匹配到 {} 个 Skill", result.size());
        return result;
    }

    @Override
    public Map<String, Object> listSkillSummaries() {
        List<SkillInfo> allSkills = getAllSkills();
        List<Map<String, Object>> skillList = new ArrayList<>();
        for (SkillInfo skill : allSkills) {
            if (skill.getMetadata() == null) {
                continue;
            }
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
        result.put("lastScanTime", getLastScanTime());
        return result;
    }

    @Override
    public Map<String, Object> getSkillDetail(String name) {
        SkillInfo skill = getSkill(name);
        if (skill == null || skill.getMetadata() == null) {
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

    @Override
    public Map<String, Object> refreshSkillCatalog() {
        try {
            int count = refreshSkills();
            log.info("Skills refreshed successfully. Total: {}", count);
            return Map.of(
                    "success", true,
                    "message", "刷新成功，发现 " + count + " 个 Skills",
                    "total", count,
                    "lastScanTime", getLastScanTime()
            );
        } catch (Exception e) {
            log.error("Failed to refresh skills", e);
            return Map.of(
                    "success", false,
                    "message", "刷新失败: " + e.getMessage()
            );
        }
    }

    /**
     * 从 classpath 扫描 Skill，兼容 jar 部署。
     */
    private List<SkillInfo> scanSkillsFromClasspath(String directory) {
        List<SkillInfo> skills = new ArrayList<>();
        try {
            String pattern = "classpath*:" + directory + "/**/" + SKILL_FILE_NAME;
            Resource[] resources = resourceResolver.getResources(pattern);
            for (Resource resource : resources) {
                try {
                    SkillInfo skillInfo = parseSkillFromResource(resource);
                    cacheSkill(skills, skillInfo);
                } catch (Exception e) {
                    log.error("解析 classpath Skill 失败: {}", resource.getDescription(), e);
                }
            }
        } catch (IOException e) {
            log.warn("从 classpath 扫描 Skill 失败: {}", directory, e);
        }
        return skills;
    }

    /**
     * 从文件系统扫描 Skill，兼容本地开发环境。
     */
    private List<SkillInfo> scanSkillsFromFileSystem(String directory) {
        Path skillsPath = Paths.get(directory);
        if (!Files.exists(skillsPath) || !Files.isDirectory(skillsPath)) {
            log.warn("文件系统中未找到 Skill 目录: {}", directory);
            return Collections.emptyList();
        }

        List<SkillInfo> skills = new ArrayList<>();
        try (Stream<Path> paths = Files.list(skillsPath)) {
            paths.filter(Files::isDirectory).forEach(skillFolder -> {
                try {
                    SkillInfo skillInfo = parseSkillFromFolder(skillFolder);
                    cacheSkill(skills, skillInfo);
                } catch (Exception e) {
                    log.error("解析文件系统 Skill 失败: {}", skillFolder, e);
                }
            });
        } catch (IOException e) {
            log.error("扫描文件系统 Skill 目录失败: {}", directory, e);
        }
        return skills;
    }

    private void cacheSkill(List<SkillInfo> skills, SkillInfo skillInfo) {
        if (skillInfo == null || skillInfo.getMetadata() == null || skillInfo.getMetadata().getName() == null) {
            return;
        }
        skillCache.put(skillInfo.getMetadata().getName(), skillInfo);
        skills.add(skillInfo);
    }

    private SkillInfo parseSkillFromResource(Resource resource) throws IOException {
        String content;
        try (InputStream inputStream = resource.getInputStream()) {
            content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        return buildSkillInfo(resource.getDescription(), List.of(), content);
    }

    private SkillInfo parseSkillFromFolder(Path skillFolder) throws IOException {
        Path skillFile = skillFolder.resolve(SKILL_FILE_NAME);
        if (!Files.exists(skillFile)) {
            log.warn("目录中缺少 SKILL.md: {}", skillFolder);
            return null;
        }
        String content = Files.readString(skillFile, StandardCharsets.UTF_8);
        return buildSkillInfo(skillFolder.toString(), scanResources(skillFolder), content);
    }

    /**
     * 构建 SkillInfo，统一处理 frontmatter 和正文。
     */
    private SkillInfo buildSkillInfo(String folderPath, List<String> resources, String rawContent) {
        String[] parts = splitFrontmatterAndContent(rawContent);
        SkillMetadata metadata = parseFrontmatter(parts[0]);
        if (metadata == null || metadata.getName() == null || metadata.getDescription() == null) {
            log.warn("Skill 元数据不完整，跳过加载: {}", folderPath);
            return null;
        }

        return SkillInfo.builder()
                .metadata(metadata)
                .content(parts[1].trim())
                .folderPath(folderPath)
                .resources(resources)
                .build();
    }

    /**
     * 分离 YAML frontmatter 与 Markdown 正文。
     */
    private String[] splitFrontmatterAndContent(String content) {
        String[] lines = content.split("\n", -1);
        if (lines.length < 3 || !FRONTMATTER_DELIMITER.equals(lines[0].trim())) {
            return new String[]{"", content};
        }

        int endIndex = -1;
        for (int i = 1; i < lines.length; i++) {
            if (FRONTMATTER_DELIMITER.equals(lines[i].trim())) {
                endIndex = i;
                break;
            }
        }
        if (endIndex < 0) {
            return new String[]{"", content};
        }

        StringBuilder frontmatter = new StringBuilder();
        for (int i = 1; i < endIndex; i++) {
            frontmatter.append(lines[i]).append('\n');
        }

        StringBuilder markdown = new StringBuilder();
        for (int i = endIndex + 1; i < lines.length; i++) {
            markdown.append(lines[i]);
            if (i < lines.length - 1) {
                markdown.append('\n');
            }
        }
        return new String[]{frontmatter.toString(), markdown.toString()};
    }

    /**
     * 解析 YAML 元数据。
     */
    private SkillMetadata parseFrontmatter(String yaml) {
        if (yaml == null || yaml.isBlank()) {
            return null;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = yamlMapper.readValue(yaml, Map.class);
            SkillMetadata metadata = new SkillMetadata();
            metadata.setName((String) map.get("name"));
            metadata.setDescription((String) map.get("description"));
            metadata.setVersion((String) map.get("version"));
            metadata.setAuthor((String) map.get("author"));
            metadata.setLicense((String) map.get("license"));

            Object triggerKeywords = map.get("trigger_keywords");
            if (triggerKeywords instanceof List<?> list) {
                List<String> keywords = list.stream().map(String::valueOf).toList();
                metadata.setTriggerKeywords(keywords);
            }
            return metadata;
        } catch (Exception e) {
            log.error("解析 Skill frontmatter 失败: {}", yaml, e);
            return null;
        }
    }

    /**
     * 扫描 Skill 目录下的补充资源。
     */
    private List<String> scanResources(Path skillFolder) {
        List<String> resources = new ArrayList<>();
        for (String directoryName : RESOURCE_DIRECTORIES) {
            Path resourceDirectory = skillFolder.resolve(directoryName);
            if (!Files.exists(resourceDirectory) || !Files.isDirectory(resourceDirectory)) {
                continue;
            }

            try (Stream<Path> files = Files.walk(resourceDirectory)) {
                files.filter(Files::isRegularFile)
                        .map(Path::toString)
                        .forEach(resources::add);
            } catch (IOException e) {
                log.error("扫描 Skill 资源目录失败: {}", resourceDirectory, e);
            }
        }
        return resources;
    }

    /**
     * Skill 匹配结果。
     * 仅用于匹配排序，不再单独拆分类文件。
     */
    private record SkillMatchResult(SkillInfo skill, int score, List<String> matchedKeywords) {
    }
}
