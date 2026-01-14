package com.cs.rag.utils;

import java.io.InputStream;

/**
 * 存储工具接口
 * 抽象文件存储操作，支持 MinIO / 阿里云 OSS 等多种实现
 */
public interface StorageUtil {

    /**
     * 文件上传（字节数组）
     * @param bytes 文件字节数组
     * @param objectName 对象名称（文件路径）
     * @return 文件访问 URL
     */
    String upload(byte[] bytes, String objectName);

    /**
     * 删除文件
     * @param objectName 对象名称或完整 URL
     * @return 是否删除成功
     */
    boolean delete(String objectName);

    /**
     * 下载文件到本地
     * @param objectName 对象名称
     */
    void download(String objectName);

    /**
     * 获取文件输入流
     * @param objectName 对象名称
     * @return 文件输入流
     */
    InputStream getObject(String objectName);

    /**
     * 生成预签名下载 URL（临时访问链接）
     * @param objectName 对象名称
     * @param expireMinutes 过期时间（分钟）
     * @return 预签名 URL
     */
    String getPresignedUrl(String objectName, int expireMinutes);

}
