package com.cs.rag.common;

/**
 * 应用程序全局常量定义
 * 
 * <p>包含应用程序中使用的全局配置常量，
 * 如API版本、应用名称、默认配置等。</p>
 * 
 * @author caoshuai
 * @since 1.0
 */
public class ApplicationConstant {
    
    /**
     * API版本前缀
     * 所有RESTful接口的统一版本前缀
     */
    public final static String API_VERSION = "/api/v1";
    
    /**
     * 应用程序名称
     */
    public final static String APPLICATION_NAME = "know-hub-ai";
    
    /**
     * OpenAI API默认基础URL
     */
    public final static String DEFAULT_BASE_URL = "https://api.openai.com";
    
    /**
     * 默认描述信息
     */
    public final static String DEFAULT_DESCRIBE = "分享自:[PG Thinker's Blog](https://pgthinker.me/2023/10/03/196/)";
}
