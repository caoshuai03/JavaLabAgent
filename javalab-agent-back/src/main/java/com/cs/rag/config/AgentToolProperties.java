package com.cs.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 工具运行时配置。
 * 用于控制工作区根目录、只读工具的结果截断策略以及网页访问范围。
 */
@Component
@ConfigurationProperties(prefix = "cs.agent.tools")
public class AgentToolProperties {

    /**
     * 工作区根目录。
     * 未显式配置时，会在策略层根据当前启动目录自动推断。
     */
    private String workspaceRoot;

    /**
     * 允许访问的网页 Host 白名单。
     * 为空时表示允许访问任意 http/https Host。
     */
    private List<String> allowedWebHosts = new ArrayList<>();

    /** 文件读取最大返回行数。 */
    private int maxReadLines = 200;

    /** 文件读取最大返回字符数。 */
    private int maxReadCharacters = 12000;

    /** 搜索类工具的最大返回条数。 */
    private int maxSearchResults = 50;

    /** 目录遍历默认最大深度。 */
    private int maxDirectoryDepth = 6;

    /** 文件搜索默认最大深度。 */
    private int maxFileSearchDepth = 8;

    /** grep 搜索最多扫描的文件数量。 */
    private int maxGrepFiles = 200;

    /** 网页读取超时时间（秒）。 */
    private int webReadTimeoutSeconds = 10;

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public List<String> getAllowedWebHosts() {
        return allowedWebHosts;
    }

    public void setAllowedWebHosts(List<String> allowedWebHosts) {
        this.allowedWebHosts = allowedWebHosts;
    }

    public int getMaxReadLines() {
        return maxReadLines;
    }

    public void setMaxReadLines(int maxReadLines) {
        this.maxReadLines = maxReadLines;
    }

    public int getMaxReadCharacters() {
        return maxReadCharacters;
    }

    public void setMaxReadCharacters(int maxReadCharacters) {
        this.maxReadCharacters = maxReadCharacters;
    }

    public int getMaxSearchResults() {
        return maxSearchResults;
    }

    public void setMaxSearchResults(int maxSearchResults) {
        this.maxSearchResults = maxSearchResults;
    }

    public int getMaxDirectoryDepth() {
        return maxDirectoryDepth;
    }

    public void setMaxDirectoryDepth(int maxDirectoryDepth) {
        this.maxDirectoryDepth = maxDirectoryDepth;
    }

    public int getMaxFileSearchDepth() {
        return maxFileSearchDepth;
    }

    public void setMaxFileSearchDepth(int maxFileSearchDepth) {
        this.maxFileSearchDepth = maxFileSearchDepth;
    }

    public int getMaxGrepFiles() {
        return maxGrepFiles;
    }

    public void setMaxGrepFiles(int maxGrepFiles) {
        this.maxGrepFiles = maxGrepFiles;
    }

    public int getWebReadTimeoutSeconds() {
        return webReadTimeoutSeconds;
    }

    public void setWebReadTimeoutSeconds(int webReadTimeoutSeconds) {
        this.webReadTimeoutSeconds = webReadTimeoutSeconds;
    }
}
