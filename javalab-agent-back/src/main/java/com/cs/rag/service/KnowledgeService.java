package com.cs.rag.service;

import com.cs.rag.common.BaseResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @Title: KnowledgeService
 * @author caoshuai
 * @Package com.cs.rag.service
 * @date 2025/11/05 18:19
 * @description: 知识库服务接口
 */
public interface KnowledgeService {

    /**
     * 上传文件到知识库
     * 包含文档解析、分割、向量化存储、OSS上传和数据库保存
     *
     * @param files 上传的文件列表
     * @return 上传结果
     */
    BaseResponse uploadFiles(List<MultipartFile> files);
}
