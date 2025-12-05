package com.cs.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.rag.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 会话表Mapper接口
 * 针对表【chat_session】的数据库操作
 * 
 * <p>SQL语句定义在 resources/mapper/ChatSessionMapper.xml</p>
 * 
 * @author caoshuai
 * @since 1.0
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
    
    /**
     * 根据用户ID查询会话列表，按更新时间倒序
     * 
     * @param userId 用户ID
     * @return 会话列表
     */
    List<ChatSession> selectByUserId(@Param("userId") Long userId);
    
    /**
     * 更新会话的更新时间
     * 每次新增消息时调用此方法
     * 
     * @param sessionId 会话ID
     */
    void updateSessionTime(@Param("sessionId") String sessionId);
    
    /**
     * 逻辑删除会话
     * 将 deleted 标记设为 1
     * 
     * @param sessionId 会话ID
     * @return 影响的行数
     */
    int logicalDelete(@Param("sessionId") String sessionId);
}
