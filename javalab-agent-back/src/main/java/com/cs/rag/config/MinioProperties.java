package com.cs.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinIO 配置属性类
 * 从 application.yml 中读取 minio.* 配置
 */
@Component
@ConfigurationProperties(prefix = "minio")
@Data
public class MinioProperties {

    /**
     * MinIO 服务端点地址，例如：http://localhost:9000
     */
    private String endpoint;

    /**
     * 访问密钥（用户名）
     */
    private String accessKey;

    /**
     * 密钥（密码）
     */
    private String secretKey;

    /**
     * 存储桶名称
     */
    private String bucketName;

}
