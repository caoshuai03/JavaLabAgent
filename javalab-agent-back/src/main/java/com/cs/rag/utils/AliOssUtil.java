package com.cs.rag.utils;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.OSSObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * 阿里云 OSS 存储工具类
 * 实现 StorageUtil 接口，提供文件上传、下载、删除等功能
 */
@Data
@AllArgsConstructor
@Slf4j
public class AliOssUtil implements StorageUtil {

    private static final String RAG_FOLDER_PREFIX = "java-lab-agent-rag/";

    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;

    /**
     * 文件上传
     *
     * @param bytes
     * @param objectName
     * @return
     */
    public String upload(byte[] bytes, String objectName) {
        // 添加文件夹前缀
        String fullObjectName = RAG_FOLDER_PREFIX + objectName;

        // 创建OSSClient实例。
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

        try {
            // 创建PutObject请求。
            ossClient.putObject(bucketName, fullObjectName, new ByteArrayInputStream(bytes));
        } catch (OSSException oe) {
            System.out.println("Caught an OSSException, which means your request made it to OSS, "
                    + "but was rejected with an error response for some reason.");
            System.out.println("Error Message:" + oe.getErrorMessage());
            System.out.println("Error Code:" + oe.getErrorCode());
            System.out.println("Request ID:" + oe.getRequestId());
            System.out.println("Host ID:" + oe.getHostId());
        } catch (ClientException ce) {
            System.out.println("Caught an ClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with OSS, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message:" + ce.getMessage());
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }

        //文件访问路径规则 https://BucketName.Endpoint/ObjectName
        StringBuilder stringBuilder = new StringBuilder("https://");
        stringBuilder
                .append(bucketName)
                .append(".")
                .append(endpoint)
                .append("/")
                .append(fullObjectName);

        log.info("文件上传到:{}", stringBuilder.toString());

        return stringBuilder.toString();
    }



    /**
     * 删除文件
     * @param objectName 对象名称或完整 URL
     * @return 是否删除成功
     */
    @Override
    public boolean delete(String objectName) {
        // 创建OSSClient实例。
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            URL url = new URL(objectName);
            String fileName = url.getPath().replaceFirst("/", "");
            // 删除文件
            ossClient.deleteObject(bucketName, fileName);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }

        }
        return true;
    }

    /**
     * 下载文件到本地
     * @param objectName 对象名称
     */
    @Override
    public void download(String objectName) {
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        // 填写Object下载到本地的完整路径。
        // 如果objectName不包含文件夹前缀，则添加前缀
        String fullObjectName = objectName.startsWith(RAG_FOLDER_PREFIX) 
                ? objectName 
                : RAG_FOLDER_PREFIX + objectName;
        
        String dirPath = "D:\\fileOSS";
        String filePath = dirPath + File.separator + fullObjectName.replace("/", "_");


        try {
            File dir = new File(dirPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 使用正确的文件路径（包含文件夹前缀）
            ossClient.getObject(new GetObjectRequest(bucketName, fullObjectName), new File(filePath));

        } catch (OSSException oe) {
            System.out.println("Caught an OSSException, which means your request made it to OSS, "
                    + "but was rejected with an error response for some reason.");
            System.out.println("Error Message:" + oe.getErrorMessage());
            System.out.println("Error Code:" + oe.getErrorCode());
            System.out.println("Request ID:" + oe.getRequestId());
            System.out.println("Host ID:" + oe.getHostId());
        } catch (ClientException ce) {
            System.out.println("Caught an ClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with OSS, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message:" + ce.getMessage());
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }

    /**
     * 获取文件输入流
     * @param objectName 对象名称
     * @return 文件输入流
     */
    @Override
    public InputStream getObject(String objectName) {
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            String fullObjectName = objectName.startsWith(RAG_FOLDER_PREFIX)
                    ? objectName
                    : RAG_FOLDER_PREFIX + objectName;
            OSSObject ossObject = ossClient.getObject(bucketName, fullObjectName);
            return ossObject.getObjectContent();
        } catch (Exception e) {
            log.error("OSS 获取文件流失败: {}", e.getMessage());
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
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            String fullObjectName = objectName.startsWith(RAG_FOLDER_PREFIX)
                    ? objectName
                    : RAG_FOLDER_PREFIX + objectName;
            // 设置过期时间
            java.util.Date expiration = new java.util.Date(System.currentTimeMillis() + expireMinutes * 60 * 1000L);
            // 生成预签名 URL
            java.net.URL url = ossClient.generatePresignedUrl(bucketName, fullObjectName, expiration);
            return url.toString();
        } catch (Exception e) {
            log.error("OSS 生成预签名 URL 失败: {}", e.getMessage());
            throw new RuntimeException("生成预签名 URL 失败", e);
        } finally {
            ossClient.shutdown();
        }
    }

}
