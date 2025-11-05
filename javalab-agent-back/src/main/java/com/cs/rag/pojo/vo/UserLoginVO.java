package com.cs.rag.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Title: UserLoginVo
 * @author  caoshuai
 * @Package com.cs.rag.pojo.vo
 * @date 2025/11/05 18:19
 * @description: 用户登录VO
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginVO {

    private Integer id;

    private String userName;

    private String name;

    private String token;
}
