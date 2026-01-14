package com.cs.rag.utils;

import io.minio.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 存储工具类
 * 实现 StorageUtil 接口，提供文件上传、下载、删除等功能
 */
@Component
@ConditionalOnProperty(name = "storage.type", havingValue = "minio")
@Slf4j
public class MinioUtil implements StorageUtil {

    /**
     * 文件夹前缀，与原 AliOssUtil 保持一致
     */
    private static final String RAG_FOLDER_PREFIX = "java-lab-agent-rag/";

    private final MinioClient minioClient;
    private final String bucketName;
    private final String endpoint;

    /**
     * 构造函数，注入 MinioClient 和配置
     */
    public MinioUtil(MinioClient minioClient,
                     @Value("${minio.bucket-name}") String bucketName,
                     @Value("${minio.endpoint}") String endpoint) {
        this.minioClient = minioClient;
        this.bucketName = bucketName;
        this.endpoint = endpoint;
        // 初始化时确保 bucket 存在
        ensureBucketExists();
    }

    /**
     * 确保存储桶存在，不存在则创建
     */
    private void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build()
                );
                log.info("创建 MinIO 存储桶: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("检查/创建存储桶失败: {}", e.getMessage());
        }
    }

    /**
     * 文件上传
     * @param bytes 文件字节数组
     * @param objectName 对象名称（文件名）
     * @return 文件访问 URL
     */
    @Override
    public String upload(byte[] bytes, String objectName) {
        // 添加文件夹前缀
        String fullObjectName = RAG_FOLDER_PREFIX + objectName;

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
            // 上传文件到 MinIO
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fullObjectName)
                            .stream(inputStream, bytes.length, -1)
                            .build()
            );

            // 构建文件访问 URL：endpoint/bucket/objectName
            String fileUrl = endpoint + "/" + bucketName + "/" + fullObjectName;
            log.info("文件上传到: {}", fileUrl);
            return fileUrl;

        } catch (Exception e) {
            log.error("MinIO 上传文件失败: {}", e.getMessage());
            throw new RuntimeException("文件上传失败", e);
        }
    }

    /**
     * 删除文件
     * @param objectName 对象名称或完整 URL
     * @return 是否删除成功
     */
    @Override
    public boolean delete(String objectName) {
        try {
            // 从 URL 中提取对象路径
            String fileName = extractObjectName(objectName);

            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fileName)
                            .build()
            );
            log.info("MinIO 删除文件成功: {}", fileName);
            return true;

        } catch (Exception e) {
            log.error("MinIO 删除文件失败: {}", e.getMessage());
            throw new RuntimeException("文件删除失败", e);
        }
    }

    /**
     * 下载文件到本地
     * @param objectName 对象名称
     */
    @Override
    public void download(String objectName) {
        // 如果 objectName 不包含文件夹前缀，则添加前缀
        String fullObjectName = objectName.startsWith(RAG_FOLDER_PREFIX)
                ? objectName
                : RAG_FOLDER_PREFIX + objectName;

        // 下载目录（与原 AliOssUtil 保持一致）
        String dirPath = "D:\\fileOSS";
        String filePath = dirPath + File.separator + fullObjectName.replace("/", "_");

        try {
            // 确保目录存在
            File dir = new File(dirPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 从 MinIO 获取文件流并保存到本地
            try (InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fullObjectName)
                            .build()
            )) {
                Files.copy(stream, new File(filePath).toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.info("MinIO 下载文件成功: {}", filePath);
            }

        } catch (Exception e) {
            log.error("MinIO 下载文件失败: {}", e.getMessage());
            throw new RuntimeException("文件下载失败", e);
        }
    }

    /**
     * 获取文件输入流
     * @param objectName 对象名称
     * @return 文件输入流
     */
    @Override
    public InputStream getObject(String objectName) {
        try {
            String fullObjectName = objectName.startsWith(RAG_FOLDER_PREFIX)
                    ? objectName
                    : RAG_FOLDER_PREFIX + objectName;

            // 添加调试日志
            log.info("MinIO getObject调试 - 原始对象名: {}, 最终对象名: {}, 存储桶: {}", 
                objectName, fullObjectName, bucketName);

            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fullObjectName)
                            .build()
            );
        } catch (Exception e) {
            log.error("MinIO 获取文件流失败: {}", e.getMessage());
            throw new RuntimeException("获取文件流失败", e);
        }
    }

    /**
     * 生成预签名下载 URL（临时访问链接）
     * @param objectName 对象名称
     * @param expireMinutes 过期时间（分钟）
     * @return 预签名 URL
     */
    @Override
    public String getPresignedUrl(String objectName, int expireMinutes) {
        try {
            String fullObjectName = objectName.startsWith(RAG_FOLDER_PREFIX)
                    ? objectName
                    : RAG_FOLDER_PREFIX + objectName;

            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(fullObjectName)
                            .expiry(expireMinutes, TimeUnit.MINUTES)
                            .build()
            );
        } catch (Exception e) {
            log.error("MinIO 生成预签名 URL 失败: {}", e.getMessage());
            throw new RuntimeException("生成预签名 URL 失败", e);
        }
    }

    /**
     * 从完整 URL 中提取对象名称
     * @param urlOrObjectName URL 或对象名称
     * @return 对象名称（包含文件夹前缀）
     */
    private String extractObjectName(String urlOrObjectName) {
        try {
            // 尝试解析为 URL
            URL url = new URL(urlOrObjectName);
            String path = url.getPath();
            // 移除开头的斜杠和 bucket 名称
            // 格式：/bucket/java-lab-agent-rag/filename.ext
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            // 移除 bucket 名称前缀
            if (path.startsWith(bucketName + "/")) {
                path = path.substring(bucketName.length() + 1);
            }
            return path;
        } catch (Exception e) {
            // 不是 URL，直接返回
            return urlOrObjectName;
        }
    }

}
