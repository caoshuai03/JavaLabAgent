package com.cs.rag.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cs.rag.entity.ChatSession;
import com.cs.rag.mapper.ChatSessionMapper;
import com.cs.rag.service.ChatSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 会话Service实现类
 * 针对表【chat_session】的业务操作实现
 * 
 * @author caoshuai
 */
@Slf4j
@Service
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession>
        implements ChatSessionService {
    
    @Autowired
    private ChatSessionMapper chatSessionMapper;
    
    /**
     * 创建新会话
     * 生成UUID作为会话ID，设置创建时间和更新时间
     * 
     * @param userId 用户ID
     * @param title 会话标题
     * @return 新创建的会话对象
     */
    @Override
    public ChatSession createSession(Long userId, String title) {
        // 生成UUID作为会话ID
        String sessionId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        
        // 构建会话对象
        ChatSession session = ChatSession.builder()
                .id(sessionId)
                .userId(userId)
                .title(title != null ? title : "新对话")
                .createdAt(now)
                .updatedAt(now)
                .build();
        
        // 保存到数据库
        chatSessionMapper.insert(session);
        log.info("创建新会话: sessionId={}, userId={}, title={}", sessionId, userId, title);
        
        return session;
    }
    
    /**
     * 获取用户的会话列表
     * 
     * @param userId 用户ID
     * @return 会话列表，按更新时间倒序
     */
    @Override
    public List<ChatSession> getSessionsByUserId(Long userId) {
        return chatSessionMapper.selectByUserId(userId);
    }
    
    /**
     * 更新会话的更新时间
     * 每次新增消息时调用此方法，保持会话列表按最新活动排序
     * 
     * @param sessionId 会话ID
     */
    @Override
    public void updateSessionTime(String sessionId) {
        chatSessionMapper.updateSessionTime(sessionId);
    }
    
    /**
     * 根据ID获取会话，如果不存在或ID为空则创建新会话
     * 
     * 业务逻辑：
     * 1. 如果sessionId为空或空字符串，创建新会话
     * 2. 如果sessionId不为空，尝试从数据库获取
     * 3. 如果数据库中不存在，创建新会话
     * 
     * @param sessionId 会话ID (可为空)
     * @param userId 用户ID
     * @param title 会话标题 (新建时使用)
     * @return 会话对象
     */
    @Override
    public ChatSession getOrCreateSession(String sessionId, Long userId, String title) {
        // 如果sessionId为空，创建新会话
        if (sessionId == null || sessionId.trim().isEmpty()) {
            log.info("sessionId为空，创建新会话");
            return createSession(userId, title);
        }
        
        // 尝试从数据库获取会话
        ChatSession session = chatSessionMapper.selectById(sessionId);
        
        // 如果会话不存在，创建新会话
        if (session == null) {
            log.info("会话不存在，创建新会话: sessionId={}", sessionId);
            return createSession(userId, title);
        }
        
        return session;
    }
}
