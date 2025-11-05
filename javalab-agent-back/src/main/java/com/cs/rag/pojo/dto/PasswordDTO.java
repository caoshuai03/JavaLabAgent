package com.cs.rag.pojo.dto;

import lombok.Data;

/**
 * @Title: PasswardDTO
 * @author  caoshuai
 * @Package com.cs.rag.pojo.dto
 * @date 2025/11/05 18:19
 * @description: 修改密码DTO
 */

@Data
public class PasswordDTO {
    private Integer id;
    private String oldPassword;
    private String newPassword;
    private String confirmPassword;
}
