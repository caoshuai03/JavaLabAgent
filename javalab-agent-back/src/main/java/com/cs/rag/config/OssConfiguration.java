package com.cs.rag.config;

import com.cs.rag.utils.AliOssUtil;
import com.cs.rag.utils.StorageUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 配置类
 * 当 storage.type=alioss 时生效，创建 AliOssUtil Bean
 */
@Configuration
@ConditionalOnProperty(name = "storage.type", havingValue = "alioss")
@Slf4j
public class OssConfiguration {

    /**
     * 创建阿里云 OSS 存储工具 Bean
     * @param aliOssProperties OSS 配置属性
     * @return StorageUtil 实现（AliOssUtil）
     */
    @Bean
    public StorageUtil storageUtil(AliOssProperties aliOssProperties) {
        log.info("开始创建阿里云文件上传工具类对象：{}", aliOssProperties);
        return new AliOssUtil(aliOssProperties.getEndpoint(),
                aliOssProperties.getAccessKeyId(),
                aliOssProperties.getAccessKeySecret(),
                aliOssProperties.getBucketName());
    }
}
