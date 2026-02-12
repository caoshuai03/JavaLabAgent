package com.cs.rag.service;

import com.cs.rag.entity.ChatMessage;
import java.util.List;

/**
 * 摘要生成服务接口
 * 
 * @author caoshuai
 * @since 1.0
 */
public interface SummaryService {

    /**
     * 生成对话历史摘要
     * 
     * @param messages 对话历史消息列表
     * @return 生成的摘要文本
     */
    String summarize(List<ChatMessage> messages);

    /**
     * 基于已有摘要和新消息生成更新后的摘要 (滚动更新)
     * 
     * @param oldSummary 已有的旧摘要
     * @param newMessages 新增的消息列表
     * @return 更新后的摘要文本
     */
    String refreshSummary(String oldSummary, List<ChatMessage> newMessages);
}
