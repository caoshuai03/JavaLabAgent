/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.cs.rag.controller;

import com.alibaba.cloud.ai.advisor.RetrievalRerankAdvisor;
import com.alibaba.cloud.ai.model.RerankModel;
import com.cs.rag.annotation.Loggable;
import com.cs.rag.common.ApplicationConstant;
import com.cs.rag.common.ErrorCode;
import com.cs.rag.entity.SensitiveWord;
import com.cs.rag.exception.BusinessException;
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
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

@Tag(name = "AiRagController", description = "Rag接口")
@Slf4j
@RestController
@RequestMapping(ApplicationConstant.API_VERSION + "/ai")
public class AiRagController {
    @Value("classpath:/prompts/system-qa.st")
    private Resource systemResource;

    @Autowired
    private SearchUtils searchUtils;

    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final RerankModel rerankModel;

    @Autowired
    private SensitiveWordService sensitiveWordService;

    //初始化基于内存的对话记忆
    private ChatMemory chatMemory = new InMemoryChatMemory();


    public AiRagController(VectorStore vectorStore, ChatModel chatModel, RerankModel rerankModel) {
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
        this.rerankModel = rerankModel;
    }


    @Operation(summary = "rag", description = "Rag对话接口")
    @GetMapping(value = "/rag")
    @Loggable
    public Flux<String> generate(@RequestParam(value = "message", defaultValue = "你好") String message) throws IOException {
        List<SensitiveWord> list = sensitiveWordService.list();

        for(SensitiveWord sensitiveWord: list){
            if (message.contains(sensitiveWord.getWord())){
                throw new BusinessException(ErrorCode.PARAMS_ERROR,"包含敏感词:" + sensitiveWord.getWord());
            }
        }
        List<Map<String, String>> maps = searchUtils.tavilySearch(message);
        if (!maps.isEmpty()){
            for (Map<String, String> map : maps) {
                message = message + "网络来源:"+ "\n" + map.get("title") + "\n" + map.get("content");
            }
        }

        // ========== RAG精度优化：检索参数配置 ==========
        // SearchRequest配置影响检索精度：
        // 1. 检索数量（TopK）：返回最相关的K个文档片段
        //    - 太小（3-5）：可能遗漏重要信息
        //    - 太大（>20）：引入噪音，增加处理成本
        //    - 推荐：5-10个（平衡精度和性能）
        // 
        // 2. 相似度阈值：过滤低相关性文档
        //    - 太低（<0.5）：包含无关信息
        //    - 太高（>0.8）：可能过滤掉相关信息
        //    - 推荐：0.6-0.75（根据实际效果调整）
        //
        // 注意：具体API可能因Spring AI版本而异，根据实际情况调整
        SearchRequest searchRequest = SearchRequest.builder()
                // 可以在这里配置检索参数（根据实际API支持的方法）
                // 例如：withTopK(5), withSimilarityThreshold(0.7) 等
                .build();
        
        // ========== RAG精度优化：提示词模板 ==========
        // 提示词模板决定如何将检索结果传递给模型
        // 文件位置：src/main/resources/prompts/system-qa.st
        // 优化建议：
        // 1. 明确指示使用检索到的上下文
        // 2. 要求模型引用来源
        // 3. 如果信息不在上下文中，明确告知用户
        String promptTemplate = systemResource.getContentAsString(StandardCharsets.UTF_8);
        
        // ========== RAG精度优化：RetrievalRerankAdvisor参数 ==========
        // RetrievalRerankAdvisor使用重排序模型提高检索精度
        // 参数说明：
        // 1. vectorStore - 向量存储（自动注入）
        // 2. rerankModel - 重排序模型（自动注入，对初始检索结果重新排序）
        // 3. searchRequest - 检索请求配置
        // 4. promptTemplate - 提示词模板
        // 5. 最后一个参数（相似度阈值）- 范围：0.0-1.0
        //    - 0.1-0.3：非常严格，只返回高度相关的内容（适合精确匹配）
        //    - 0.3-0.6：中等严格（推荐，平衡精度和召回率）
        //    - 0.6-0.9：宽松，返回更多相关内容（适合探索性查询）
        //    ⭐ 推荐值：0.3-0.6（根据实际效果调整）
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new RetrievalRerankAdvisor(
                        vectorStore, 
                        rerankModel, 
                        searchRequest, 
                        promptTemplate, 
                        0.4  // ⭐ 相似度阈值优化：0.4（从0.1提升到0.4，提高召回率）
                ))
                .build();

        return chatClient.prompt()
                // RAG配置：自定义系统提示词
                .system("如果检测到向量数据库包含用户提问的信息，那么在回答时不使用网络来源信息进行生成")
                .advisors(new MessageChatMemoryAdvisor(chatMemory))
                .advisors(a -> a
                        // RAG配置：对话记忆参数
                        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, 0)  // 对话ID，可以为每个用户设置不同ID
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 100))  // 检索的历史对话条数，可调整
                .user(message)
                .stream()
                .content();
    }
}