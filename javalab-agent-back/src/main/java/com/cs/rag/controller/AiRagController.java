package com.cs.rag.controller;

import com.cs.rag.annotation.Loggable;
import com.cs.rag.common.ApplicationConstant;
import com.cs.rag.common.ErrorCode;
import com.cs.rag.entity.SensitiveWord;
import com.cs.rag.exception.BusinessException;
import com.cs.rag.service.PromptService;
import com.cs.rag.service.SensitiveWordService;
import com.cs.rag.utils.SearchUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

@Tag(name = "AiRagController", description = "Rag接口")
@Slf4j
@RestController
@RequestMapping(ApplicationConstant.API_VERSION + "/ai")
public class AiRagController {
    @Autowired
    private PromptService promptService;

    @Autowired
    private SearchUtils searchUtils;

    private final VectorStore vectorStore;
    private final ChatModel chatModel;

    @Autowired
    private SensitiveWordService sensitiveWordService;

    private ChatMemory chatMemory = new InMemoryChatMemory();

    public AiRagController(VectorStore vectorStore, ChatModel chatModel) {
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
    }

    @Operation(summary = "rag", description = "Rag对话接口")
    @GetMapping(value = "/rag")
    @Loggable
    public Flux<String> generate(@RequestParam(value = "message", defaultValue = "你好") String message,
                                  @RequestParam(value = "conversationId", required = false) String conversationId,
                                  @RequestParam(value = "enableWebSearch", required = false, defaultValue = "false") Boolean enableWebSearch) throws IOException {
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

        // RAG检索：从向量数据库检索相关知识库内容
        SearchRequest ragSearchRequest = SearchRequest.builder()
                .query(message)
                .topK(8)
                .similarityThreshold(0.7)
                .build();
        
        List<Document> ragDocuments = vectorStore.similaritySearch(ragSearchRequest);
        if (!ragDocuments.isEmpty()) {
            StringBuilder knowledgeBaseContent = new StringBuilder("\n\n知识库来源:\n");
            for (Document doc : ragDocuments) {
                knowledgeBaseContent.append(doc.getText()).append("\n\n");
            }
            message = message + knowledgeBaseContent.toString();
        }

        ChatClient chatClient = ChatClient.builder(chatModel).build();

        // 设置对话ID，默认使用"0"
        String finalConversationId = (conversationId != null && !conversationId.trim().isEmpty()) 
                ? conversationId 
                : "0";

        // 流式返回对话结果
        return chatClient.prompt()
                .system(promptService.getChatDefaultPrompt())
                .advisors(new MessageChatMemoryAdvisor(chatMemory))
                .advisors(a -> a
                        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, finalConversationId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 20))
                .user(message)
                .stream()
                .content();
    }
}