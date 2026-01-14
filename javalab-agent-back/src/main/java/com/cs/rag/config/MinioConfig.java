package com.cs.rag.config;

import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 配置类
 * 当 storage.type=minio 时生效，创建 MinioClient Bean
 */
@Configuration
@ConditionalOnProperty(name = "storage.type", havingValue = "minio")
@Slf4j
public class MinioConfig {

    /**
     * 创建 MinIO 客户端 Bean
     * @param minioProperties MinIO 配置属性
     * @return MinioClient 实例
     */
    @Bean
    public MinioClient minioClient(MinioProperties minioProperties) {
        log.info("开始创建 MinIO 客户端，endpoint: {}", minioProperties.getEndpoint());
        return MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();
    }

}
