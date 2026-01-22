package com.cs.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import org.apache.ibatis.type.JdbcType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户反馈实体类
 * 
 * <p>对应数据库表 tb_user_feedback，用于存储用户提交的反馈信息</p>
 * 
 * @author caoshuai
 * @since 1.0
 */
@TableName(value = "tb_user_feedback")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFeedback {

    /**
     * 主键ID（自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 反馈提交人用户ID（必须登录）
     */
    private Long userId;

    /**
     * 反馈类型：0-其它 1-BUG 2-建议 3-投诉
     */
    private Integer type;

    /**
     * 反馈标题（可选）
     */
    private String title;

    /**
     * 反馈内容
     */
    private String content;

    /**
     * 状态：0-新建 1-处理中 2-已解决 3-已关闭
     */
    private Integer status;

    /**
     * 优先级：0-低 1-中 2-高
     */
    private Integer priority;


    /**
     * 处理人用户ID（管理员）
     */
    private Long handlerUserId;

    /**
     * 处理时间
     */
    private LocalDateTime handledAt;

    /**
     * 处理结论/回复
     */
    private String handleResult;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
