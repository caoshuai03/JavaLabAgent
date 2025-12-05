package com.cs.rag.constant;

/**
 * 用户相关消息常量类
 * 
 * @author caoshuai
 * @since 1.0
 */
public class UserMessageConstant {

    // ==================== 账户相关 ====================
    
    public static final String PASSWORD_ERROR = "密码错误";
    public static final String OLD_PASSWORD_ERROR = "旧密码错误";
    public static final String ACCOUNT_NOT_FOUND = "账号不存在";
    public static final String ACCOUNT_LOCKED = "账号被锁定";
    public static final String USERNAME_ALREADY_EXISTS = "用户名已存在";
    public static final String USER_NOT_LOGIN = "用户未登录";
    public static final String LOGIN_FAILED = "登录失败";
    
    // ==================== 密码相关 ====================
    
    public static final String PASSWORD_NOT_MATCH = "新密码与确认密码不一致";
    public static final String PASSWORD_EDIT_FAILED = "密码修改失败";
    public static final String PASSWORD_EDIT_SUCCESS = "修改密码成功";
    
    // ==================== 操作结果 ====================
    
    public static final String REGISTER_SUCCESS = "注册成功";
    public static final String LOGOUT_SUCCESS = "退出成功";
    public static final String ADD_SUCCESS = "新增成功";
    public static final String EDIT_SUCCESS = "编辑成功";
    public static final String UPDATE_SUCCESS = "更新成功";
    public static final String UPDATE_FAILED = "更新失败";
    public static final String DISABLE_SUCCESS = "禁用成功";
    public static final String USER_UPDATE_FAILED = "更新用户信息失败，未找到对应的用户记录";

}
