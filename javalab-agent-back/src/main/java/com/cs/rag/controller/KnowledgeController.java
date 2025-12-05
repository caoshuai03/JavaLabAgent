package com.cs.rag.controller;

/**
 * @Title: KnowledgeController
 * @author  caoshuai
 * @Package com.cs.rag.controller
 * @date 2025/11/05 18:19
 * @description: 知识库控制器，负责接收请求和返回响应
 */

import com.cs.rag.common.ApplicationConstant;
import com.cs.rag.common.BaseResponse;
import com.cs.rag.pojo.dto.QueryFileDTO;
import com.cs.rag.service.AliOssFileService;
import com.cs.rag.service.KnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "KnowledgeController", description = "知识库管理接口")
@Slf4j
@RestController
@RequestMapping(ApplicationConstant.API_VERSION + "/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private AliOssFileService aliOssFileService;

    /**
     * 上传附件接口
     *
     * @param files 上传的文件列表
     * @return 上传结果
     */
    @Operation(summary = "upload", description = "上传附件接口")
    @PostMapping(value = "file/upload", headers = "content-type=multipart/form-data")
    public BaseResponse upload(@RequestParam("file") List<MultipartFile> files) {
        // 调用Service层处理文件上传业务逻辑
        return knowledgeService.uploadFiles(files);
    }

    @Operation(summary = "contents", description = "文件查询")
    @GetMapping("/contents")
    public BaseResponse queryFiles(QueryFileDTO request) {
        // 调用Service层处理文件查询业务逻辑
        return aliOssFileService.queryPage(request);
    }

    @Operation(summary = "delete", description = "文件删除")
    @DeleteMapping("/delete")
    public BaseResponse deleteFiles(@RequestParam List<Long> ids) {
        return aliOssFileService.deleteFiles(ids);
    }

    @Operation(summary = "download", description = "文件下载")
    @GetMapping("/download")
    public BaseResponse downloadFiles(@RequestParam List<Long> ids) {
        return aliOssFileService.downloadFiles(ids);
    }

}
