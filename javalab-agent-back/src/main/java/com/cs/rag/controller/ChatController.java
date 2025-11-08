package com.cs.rag.controller;

import com.cs.rag.common.ApplicationConstant;
import com.cs.rag.common.ErrorCode;
import com.cs.rag.entity.SensitiveWord;
import com.cs.rag.exception.BusinessException;
import com.cs.rag.service.PromptService;
import com.cs.rag.service.SensitiveWordService;
import com.cs.rag.utils.SearchUtils;
import com.cs.rag.annotation.Loggable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

/**
 * @author  caoshuai
 * @date 2025/11/05 18:19
 * @description: 对话接口
 */
@Tag(name="ChatController",description = "chat对话接口")
@Slf4j
@RestController
@RequestMapping(ApplicationConstant.API_VERSION + "/chat")
public class ChatController {

    @Autowired
    private  ChatClient chatClient;

    @Autowired
    private SearchUtils searchUtils;

    @Autowired
    private SensitiveWordService sensitiveWordService;

    @Autowired
    private PromptService promptService;

    private ChatMemory chatMemory = new InMemoryChatMemory();

    public ChatController(ChatClient.Builder builder, VectorStore vectorStore, ChatMemory chatMemory) {
        this.chatClient = builder
                .defaultAdvisors(
                        new PromptChatMemoryAdvisor(chatMemory),
                        new MessageChatMemoryAdvisor(chatMemory),
                        new QuestionAnswerAdvisor(vectorStore, SearchRequest.builder().build()))
                .build();
    }

    @Operation(summary = "stream",description = "流式对话接口")
    @GetMapping(value = "/stream")
    @Loggable
    public Flux<String> streamRagChat(@RequestParam(value = "message", defaultValue = "你好" ) String message,
                                      @RequestParam(value = "conversationId", required = false) String conversationId,
                                      @RequestParam(value = "enableWebSearch", required = false, defaultValue = "false") Boolean enableWebSearch){
        // 获取默认提示词
        String prompt = promptService.getChatDefaultPrompt();
        
        // 敏感词检查
        List<SensitiveWord> list = sensitiveWordService.list();
        for(SensitiveWord sensitiveWord: list){
            if (message.contains(sensitiveWord.getWord())){
                throw new BusinessException(ErrorCode.PARAMS_ERROR,"包含敏感词:" + sensitiveWord.getWord());
            }
        }

        // 网络搜索（可选）
        if (Boolean.TRUE.equals(enableWebSearch)) {
            List<Map<String, String>> maps = searchUtils.tavilySearch(message);
            if (!maps.isEmpty()){
                for (Map<String, String> map : maps) {
                    message = message + "网络来源:"+ "\n" + map.get("title") + "\n" + map.get("content");
                }
            }
        }
        
        // 设置对话ID，默认使用"0"
        String finalConversationId = (conversationId != null && !conversationId.trim().isEmpty()) 
                ? conversationId 
                : "0";
        
        // 流式返回对话结果
        return chatClient.prompt()
                .system(prompt)
                .advisors(new MessageChatMemoryAdvisor(chatMemory))
                .advisors(a -> a
                        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, finalConversationId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 100))
                .user(message)
                .stream()
                .content();
    }
}
