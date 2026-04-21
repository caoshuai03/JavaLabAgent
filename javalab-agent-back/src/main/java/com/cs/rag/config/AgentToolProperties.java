package com.cs.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 工具运行配置。
 * 统一管理工作区边界、读写限制和终端执行约束。
 */
@Component
@ConfigurationProperties(prefix = "cs.agent.tools")
public class AgentToolProperties {

    /**
     * 工作区根目录。
     * 为空时由策略层根据当前进程目录自动推断。
     */
    private String workspaceRoot;

    /**
     * Web 工具允许访问的域名列表。
     * 为空表示不做域名级限制。
     */
    private List<String> allowedWebHosts = new ArrayList<>();

    /** `file_read` 最多返回的行数。 */
    private int maxReadLines = 200;

    /** 文本读取和网页读取最多返回的字符数。 */
    private int maxReadCharacters = 12000;

    /** 搜索类工具最多返回的条目数。 */
    private int maxSearchResults = 50;

    /** 文件搜索默认最大深度。 */
    private int maxFileSearchDepth = 8;

    /** `grep_search` 最多扫描的文件数。 */
    private int maxGrepFiles = 200;

    /** `file_write` 最多允许写入的字符数。 */
    private int maxWriteCharacters = 24000;

    /** `terminal_exec` 允许的最长执行秒数。 */
    private int terminalTimeoutSeconds = 20;

    /** `terminal_exec` 最多返回的输出字符数。 */
    private int maxTerminalOutputCharacters = 12000;

    /**
     * `terminal_exec` 禁止执行的命令列表。
     * 未出现在列表中的命令默认允许执行，但仍会继续经过其他安全规则校验。
     */
    private List<String> blockedTerminalCommands = new ArrayList<>(List.of(
            "cmd",
            "powershell",
            "pwsh",
            "bash",
            "sh",
            "zsh",
            "rm",
            "rmdir",
            "rd",
            "del",
            "erase",
            "format",
            "diskpart",
            "shutdown",
            "reboot",
            "halt",
            "poweroff",
            "taskkill",
            "kill",
            "pkill",
            "reg",
            "regedit",
            "netsh",
            "sc",
            "bcdedit",
            "takeown",
            "icacls",
            "sudo",
            "runas",
            "curl",
            "wget",
            "ssh",
            "scp",
            "sftp",
            "ftp",
            "telnet",
            "dd",
            "mkfs",
            "mount",
            "umount"
    ));

    /** `web_read` 和 `web_search` 的超时时间，单位秒。 */
    private int webReadTimeoutSeconds = 10;

    /**
     * Agent 全局执行超时（秒）。
     * 涵盖所有轮次的规划 + 最终回答生成，超时后强制进入最终回答阶段。
     * 参考: OpenAI Assistants 10min、LangChain max_execution_time。
     */
    private int agentTotalTimeoutSeconds = 180;

    /**
     * 单次工具执行兜底超时（秒）。
     * 对所有工具类型统一生效，防止单个工具调用长时间挂起。
     * 工具自身若有更短的超时（如 terminal_exec）则以自身为准。
     */
    private int toolExecutionTimeoutSeconds = 60;

    /**
     * LLM 决策推理超时（秒）。
     * 单次 plan 轮中调用 LLM 的最大等待时间，超时后降级为直接回答。
     */
    private int llmDecisionTimeoutSeconds = 60;

    /**
     * MCP stdio 读取超时（秒）。
     * stdio 模式下等待 MCP 服务响应的最大时间，防止 readLine 永久阻塞。
     */
    private int mcpStdioTimeoutSeconds = 30;

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

    public int getMaxWriteCharacters() {
        return maxWriteCharacters;
    }

    public void setMaxWriteCharacters(int maxWriteCharacters) {
        this.maxWriteCharacters = maxWriteCharacters;
    }

    public int getTerminalTimeoutSeconds() {
        return terminalTimeoutSeconds;
    }

    public void setTerminalTimeoutSeconds(int terminalTimeoutSeconds) {
        this.terminalTimeoutSeconds = terminalTimeoutSeconds;
    }

    public int getMaxTerminalOutputCharacters() {
        return maxTerminalOutputCharacters;
    }

    public void setMaxTerminalOutputCharacters(int maxTerminalOutputCharacters) {
        this.maxTerminalOutputCharacters = maxTerminalOutputCharacters;
    }

    public List<String> getBlockedTerminalCommands() {
        return blockedTerminalCommands;
    }

    public void setBlockedTerminalCommands(List<String> blockedTerminalCommands) {
        this.blockedTerminalCommands = blockedTerminalCommands;
    }

    public int getWebReadTimeoutSeconds() {
        return webReadTimeoutSeconds;
    }

    public void setWebReadTimeoutSeconds(int webReadTimeoutSeconds) {
        this.webReadTimeoutSeconds = webReadTimeoutSeconds;
    }

    public int getAgentTotalTimeoutSeconds() {
        return agentTotalTimeoutSeconds;
    }

    public void setAgentTotalTimeoutSeconds(int agentTotalTimeoutSeconds) {
        this.agentTotalTimeoutSeconds = agentTotalTimeoutSeconds;
    }

    public int getToolExecutionTimeoutSeconds() {
        return toolExecutionTimeoutSeconds;
    }

    public void setToolExecutionTimeoutSeconds(int toolExecutionTimeoutSeconds) {
        this.toolExecutionTimeoutSeconds = toolExecutionTimeoutSeconds;
    }

    public int getLlmDecisionTimeoutSeconds() {
        return llmDecisionTimeoutSeconds;
    }

    public void setLlmDecisionTimeoutSeconds(int llmDecisionTimeoutSeconds) {
        this.llmDecisionTimeoutSeconds = llmDecisionTimeoutSeconds;
    }

    public int getMcpStdioTimeoutSeconds() {
        return mcpStdioTimeoutSeconds;
    }

    public void setMcpStdioTimeoutSeconds(int mcpStdioTimeoutSeconds) {
        this.mcpStdioTimeoutSeconds = mcpStdioTimeoutSeconds;
    }
}
