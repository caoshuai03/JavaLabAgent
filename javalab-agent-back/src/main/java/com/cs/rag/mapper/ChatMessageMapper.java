package com.cs.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.rag.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 消息表Mapper接口
 * 针对表【chat_message】的数据库操作
 * 
 * <p>SQL语句定义在 resources/mapper/ChatMessageMapper.xml</p>
 * 
 * @author caoshuai
 * @since 1.0
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
    
    /**
     * 滑动窗口查询：获取指定会话的最近N条消息
     * 
     * <p>滑动窗口策略说明:</p>
     * <ol>
     *   <li>查询时按创建时间倒序(DESC)获取最近N条</li>
     *   <li>返回结果在Service层需反转为时间正序，以符合LLM对话顺序要求</li>
     *   <li>增加用户ID校验，确保用户只能访问自己的消息</li>
     * </ol>
     * 
     * @param sessionId 会话ID
     * @param userId 用户ID（用于权限校验）
     * @param limit 获取消息条数 (滑动窗口大小)
     * @return 最近N条消息列表 (按时间倒序)
     */
    List<ChatMessage> selectRecentMessages(@Param("sessionId") String sessionId,
                                           @Param("userId") Long userId,
                                           @Param("limit") int limit);
    
    /**
     * 查询指定会话的所有消息，按时间正序
     * 增加用户ID校验，确保用户只能访问自己的消息
     * 
     * @param sessionId 会话ID
     * @param userId 用户ID（用于权限校验）
     * @return 消息列表 (按时间正序)
     */
    List<ChatMessage> selectBySessionId(@Param("sessionId") String sessionId,
                                        @Param("userId") Long userId);
}
