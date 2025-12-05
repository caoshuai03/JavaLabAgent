package com.cs.rag.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cs.rag.entity.ChatSession;

import java.util.List;

/**
 * 会话Service接口
 * 针对表【chat_session】的业务操作
 * 
 * @author caoshuai
 */
public interface ChatSessionService extends IService<ChatSession> {
    
    /**
     * 创建新会话
     * 
     * @param userId 用户ID
     * @param title 会话标题 (通常取自第一条用户消息)
     * @return 新创建的会话对象
     */
    ChatSession createSession(Long userId, String title);
    
    /**
     * 获取用户的会话列表
     * 
     * @param userId 用户ID
     * @return 会话列表，按更新时间倒序
     */
    List<ChatSession> getSessionsByUserId(Long userId);
    
    /**
     * 更新会话的更新时间
     * 每次新增消息时调用此方法
     * 
     * @param sessionId 会话ID
     */
    void updateSessionTime(String sessionId);
    
    /**
     * 根据ID获取会话，如果不存在则创建新会话
     * 
     * @param sessionId 会话ID (可为空)
     * @param userId 用户ID
     * @param title 会话标题 (新建时使用)
     * @return 会话对象
     */
    ChatSession getOrCreateSession(String sessionId, Long userId, String title);
    
    /**
     * 逻辑删除会话
     * 将 deleted 标记设为 1，不实际删除数据
     * 
     * @param sessionId 会话ID
     * @return 是否删除成功
     */
    boolean deleteSession(String sessionId);
}
