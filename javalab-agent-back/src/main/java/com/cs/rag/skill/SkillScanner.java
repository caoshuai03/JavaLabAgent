package com.cs.rag.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Skill 扫描器
 * 负责扫描文件系统中的 Skills 目录，解析 SKILL.md 文件
 *
 * @author caoshuai
 */
@Slf4j
@Component
public class SkillScanner {

    private static final String SKILL_FILE_NAME = "SKILL.md";
    private static final String FRONTMATTER_DELIMITER = "---";
    
    private final ObjectMapper yamlMapper;
    
    /**
     * Skills 缓存：skillName -> SkillInfo
     */
    private final Map<String, SkillInfo> skillCache = new ConcurrentHashMap<>();
    
    /**
     * 最后扫描时间戳
     */
    private long lastScanTime = 0;

    private final ResourcePatternResolver resourceResolver;
    
    public SkillScanner() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.resourceResolver = new PathMatchingResourcePatternResolver();
    }

    /**
     * 扫描指定目录下的所有 Skills
     * 支持文件系统路径（开发环境）和 classpath 资源（jar 包部署）
     * 
     * @param skillsDirectory Skills 根目录路径
     * @return Skills 列表
     */
    public List<SkillInfo> scanSkills(String skillsDirectory) {
        // 优先尝试从 classpath 加载（适配 jar 包部署）
        List<SkillInfo> skills = scanSkillsFromClasspath(skillsDirectory);
        
        // 如果 classpath 加载失败，尝试文件系统路径（适配开发环境）
        if (skills.isEmpty()) {
            skills = scanSkillsFromFileSystem(skillsDirectory);
        }
        
        lastScanTime = System.currentTimeMillis();
        log.info("Skills scan completed. Total: {} skills loaded.", skills.size());
        return skills;
    }
    
    /**
     * 从 classpath 加载 Skills（jar 包部署环境）
     */
    private List<SkillInfo> scanSkillsFromClasspath(String skillsDirectory) {
        List<SkillInfo> skills = new ArrayList<>();
        
        try {
            // 扫描 classpath 中的 SKILL.md 文件
            String pattern = "classpath*:" + skillsDirectory + "/**/SKILL.md";
            log.info("Scanning skills from classpath: {}", pattern);
            
            Resource[] resources = resourceResolver.getResources(pattern);
            log.info("Found {} SKILL.md files in classpath", resources.length);
            
            for (Resource resource : resources) {
                try {
                    SkillInfo skillInfo = parseSkillFromResource(resource);
                    if (skillInfo != null) {
                        skills.add(skillInfo);
                        skillCache.put(skillInfo.getMetadata().getName(), skillInfo);
                        log.info("Loaded skill from classpath: {}", skillInfo.getMetadata().getName());
                    }
                } catch (Exception e) {
                    log.error("Failed to parse skill from resource: {}", resource.getDescription(), e);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to scan skills from classpath: {}", skillsDirectory, e);
        }
        
        return skills;
    }
    
    /**
     * 从文件系统加载 Skills（开发环境）
     */
    private List<SkillInfo> scanSkillsFromFileSystem(String skillsDirectory) {
        Path skillsPath = Paths.get(skillsDirectory);
        
        if (!Files.exists(skillsPath) || !Files.isDirectory(skillsPath)) {
            log.warn("Skills directory not found in file system: {}", skillsDirectory);
            return Collections.emptyList();
        }

        log.info("Scanning skills from file system: {}", skillsPath.toAbsolutePath());
        List<SkillInfo> skills = new ArrayList<>();
        
        try (Stream<Path> paths = Files.list(skillsPath)) {
            paths.filter(Files::isDirectory)
                .forEach(skillFolder -> {
                    try {
                        SkillInfo skillInfo = parseSkill(skillFolder);
                        if (skillInfo != null) {
                            skills.add(skillInfo);
                            skillCache.put(skillInfo.getMetadata().getName(), skillInfo);
                            log.info("Loaded skill from file system: {}", skillInfo.getMetadata().getName());
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse skill from: {}", skillFolder, e);
                    }
                });
        } catch (IOException e) {
            log.error("Failed to scan skills from file system: {}", skillsDirectory, e);
        }
        
        return skills;
    }
    
    /**
     * 从 Spring Resource 解析 Skill（用于 classpath 加载）
     */
    private SkillInfo parseSkillFromResource(Resource resource) throws IOException {
        String content;
        try (InputStream is = resource.getInputStream()) {
            content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        
        // 解析 frontmatter 和内容
        String[] parts = splitFrontmatterAndContent(content);
        String frontmatterYaml = parts[0];
        String markdownContent = parts[1];

        // 解析元数据
        SkillMetadata metadata = parseFrontmatter(frontmatterYaml);
        if (metadata == null || metadata.getName() == null || metadata.getDescription() == null) {
            log.warn("Invalid skill metadata in resource: {}", resource.getDescription());
            return null;
        }

        return SkillInfo.builder()
                .metadata(metadata)
                .content(markdownContent.trim())
                .folderPath(resource.getDescription())
                .resources(List.of())
                .build();
    }

    /**
     * 解析单个 Skill 文件夹
     * 
     * @param skillFolder Skill 文件夹路径
     * @return SkillInfo 或 null（解析失败）
     */
    private SkillInfo parseSkill(Path skillFolder) throws IOException {
        Path skillFile = skillFolder.resolve(SKILL_FILE_NAME);
        
        if (!Files.exists(skillFile)) {
            log.warn("SKILL.md not found in: {}", skillFolder);
            return null;
        }

        String content = Files.readString(skillFile, StandardCharsets.UTF_8);
        
        // 解析 frontmatter 和内容
        String[] parts = splitFrontmatterAndContent(content);
        String frontmatterYaml = parts[0];
        String markdownContent = parts[1];

        // 解析元数据
        SkillMetadata metadata = parseFrontmatter(frontmatterYaml);
        if (metadata == null || metadata.getName() == null || metadata.getDescription() == null) {
            log.warn("Invalid skill metadata in: {}", skillFolder);
            return null;
        }

        // 扫描额外资源
        List<String> resources = scanResources(skillFolder);

        return SkillInfo.builder()
                .metadata(metadata)
                .content(markdownContent.trim())
                .folderPath(skillFolder.toString())
                .resources(resources)
                .build();
    }

    /**
     * 分离 YAML frontmatter 和 Markdown 内容
     * 
     * @param content 完整文件内容
     * @return [frontmatter, content]
     */
    private String[] splitFrontmatterAndContent(String content) {
        String[] lines = content.split("\n", -1);
        
        if (lines.length < 3 || !lines[0].trim().equals(FRONTMATTER_DELIMITER)) {
            return new String[]{"", content};
        }

        int endIndex = -1;
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].trim().equals(FRONTMATTER_DELIMITER)) {
                endIndex = i;
                break;
            }
        }

        if (endIndex == -1) {
            return new String[]{"", content};
        }

        StringBuilder frontmatter = new StringBuilder();
        for (int i = 1; i < endIndex; i++) {
            frontmatter.append(lines[i]).append("\n");
        }

        StringBuilder markdownContent = new StringBuilder();
        for (int i = endIndex + 1; i < lines.length; i++) {
            markdownContent.append(lines[i]);
            if (i < lines.length - 1) {
                markdownContent.append("\n");
            }
        }

        return new String[]{frontmatter.toString(), markdownContent.toString()};
    }

    /**
     * 解析 YAML frontmatter
     * 
     * @param yaml YAML 内容
     * @return SkillMetadata
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
            
            // 解析 trigger_keywords
            Object triggerKeywordsObj = map.get("trigger_keywords");
            if (triggerKeywordsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> keywords = (List<String>) triggerKeywordsObj;
                metadata.setTriggerKeywords(keywords);
            }
            
            return metadata;
        } catch (Exception e) {
            log.error("Failed to parse YAML frontmatter: {}", yaml, e);
            return null;
        }
    }

    /**
     * 扫描 Skill 文件夹中的额外资源
     * 
     * @param skillFolder Skill 文件夹
     * @return 资源文件路径列表
     */
    private List<String> scanResources(Path skillFolder) {
        List<String> resources = new ArrayList<>();
        String[] resourceDirs = {"scripts", "references", "assets"};
        
        for (String dirName : resourceDirs) {
            Path resourceDir = skillFolder.resolve(dirName);
            if (Files.exists(resourceDir) && Files.isDirectory(resourceDir)) {
                try (Stream<Path> files = Files.walk(resourceDir)) {
                    files.filter(Files::isRegularFile)
                        .forEach(file -> resources.add(file.toString()));
                } catch (IOException e) {
                    log.error("Failed to scan resource directory: {}", resourceDir, e);
                }
            }
        }
        
        return resources;
    }

    /**
     * 获取缓存的 Skill
     * 
     * @param skillName Skill 名称
     * @return SkillInfo 或 null
     */
    public SkillInfo getSkill(String skillName) {
        return skillCache.get(skillName);
    }

    /**
     * 获取所有缓存的 Skills
     * 
     * @return Skills 列表
     */
    public List<SkillInfo> getAllSkills() {
        return new ArrayList<>(skillCache.values());
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        skillCache.clear();
        lastScanTime = 0;
        log.info("Skills cache cleared");
    }

    /**
     * 获取最后扫描时间
     */
    public long getLastScanTime() {
        return lastScanTime;
    }
}
