package com.cs.rag.service;

/**
 * 提示词管理服务接口
 * 
 * <p>统一管理应用程序中使用的所有提示词模板，
 * 从资源文件中加载并处理提示词内容。</p>
 * 
 * <p>提示词文件存放位置: resources/prompts/</p>
 * 
 * @author caoshuai
 * @since 1.0
 */
public interface PromptService {

    /**
     * 获取默认对话提示词
     * 
     * <p>用于RAG对话时设置系统提示词，
     * 指导LLM的回复风格和行为规范。</p>
     * 
     * @return 处理后的提示词内容
     */
    String getChatDefaultPrompt();
}

