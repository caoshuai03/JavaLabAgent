package com.cs.rag.service;

public interface PromptService {

    String getChatDefaultPrompt();

    String getChatSummaryPrompt();

    String getReactAgentPrompt();

    String getReactAgentFinalPrompt();
}
