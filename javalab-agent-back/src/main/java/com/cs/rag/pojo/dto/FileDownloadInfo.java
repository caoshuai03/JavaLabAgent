package com.cs.rag.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.InputStream;

/**
 * 文件下载信息封装类
 * 用于传递文件下载所需的信息
 */
@Data
@NoArgsConstructor 
@AllArgsConstructor
public class FileDownloadInfo {
    
    /**
     * 文件名
     */
    private String fileName;
    
    /**
     * 文件大小
     */
    private Long fileSize;
    
    /**
     * 文件MIME类型
     */
    private String contentType;
    
    /**
     * 文件输入流
     */
    private InputStream inputStream;
    
    /**
     * 构造函数 - 只包含必要信息
     */
    public FileDownloadInfo(String fileName, InputStream inputStream) {
        this.fileName = fileName;
        this.inputStream = inputStream;
        this.contentType = "application/octet-stream"; // 默认类型
    }
}
