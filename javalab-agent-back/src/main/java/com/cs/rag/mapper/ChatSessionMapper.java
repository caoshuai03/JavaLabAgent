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
     * 根据ID和用户ID查询会话（带用户归属校验）
     * 用于验证会话是否属于指定用户，防止越权访问
     * 
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @return 会话对象，如果不存在或不属于该用户则返回null
     */
    ChatSession selectByIdAndUserId(@Param("sessionId") String sessionId, 
                                     @Param("userId") Long userId);
    
    /**
     * 逻辑删除会话（带用户归属校验）
     * 将 deleted 标记设为 1，仅删除属于指定用户的会话
     * 
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @return 影响的行数
     */
    int logicalDeleteWithUser(@Param("sessionId") String sessionId, 
                               @Param("userId") Long userId);
}
