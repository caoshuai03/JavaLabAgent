package com.cs.rag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * @author  caoshuai
 * @date 2025/11/05 18:19
 */

@Configuration
public class ApplicationConfig {

    /**
     * ETL中的DocumentTransformer的实现，将文本数据源转换为多个分割段落
     * 
     * ========== RAG精度优化：文档分割配置 ==========
     * 
     * TokenTextSplitter参数说明：
     * 1. chunkSize: 每个文档片段的最大token数
     *    - 太小（<256）：信息碎片化，上下文不足
     *    - 太大（>2048）：检索噪音增加，包含无关信息
     *    - 推荐：512-1024（平衡精度和上下文）
     * 
     * 2. chunkOverlap: 相邻片段之间的重叠token数
     *    - 目的：避免重要信息在分割边界丢失
     *    - 太小：可能丢失边界信息
     *    - 太大：增加存储开销
     *    - 推荐：chunkSize的10-20%
     * 
     * 3. 根据文档类型调整：
     *    - 技术文档：chunkSize=512-768, overlap=50-100
     *    - 问答对话：chunkSize=256-512, overlap=25-50
     *    - 长文本：chunkSize=1024-2048, overlap=100-200
     * 
     * 注意：当前使用默认配置，如需自定义，请查看Spring AI文档了解
     * TokenTextSplitter的具体构造函数参数
     * 
     * @return TokenTextSplitter文档分割器
     */
    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        // 当前配置：使用默认值
        // 默认值通常是：chunkSize=1000, overlap=200
        return new TokenTextSplitter();
    }

    @Bean
    ChatClient chatclient(ChatClient.Builder builder){
        return builder.defaultSystem("你是一个乐于助人解决问题的AI机器人")
                .build();
    }

    @Bean
    ChatMemory chatMemory(){
        return new ChatMemory() {
            @Override
            public void add(String conversationId, List<Message> messages) {

            }

            @Override
            public List<Message> get(String conversationId, int lastN) {
                return null;
            }

            @Override
            public void clear(String conversationId) {

            }
        };
    }


}
