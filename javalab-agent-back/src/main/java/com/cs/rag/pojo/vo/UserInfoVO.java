package com.cs.rag.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息视图对象
 * 用于返回给前端的用户数据，隐藏敏感字段（密码、身份证号等）
 * 
 * @author caoshuai
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoVO {
    
    /**
     * 用户ID
     */
    private Integer id;
    
    /**
     * 姓名
     */
    private String name;
    
    /**
     * 用户名
     */
    private String userName;

    /**
     * 状态（0：禁用 1：启用）
     */
    private Integer status;
}
