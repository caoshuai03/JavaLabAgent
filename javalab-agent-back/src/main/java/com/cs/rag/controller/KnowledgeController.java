package com.cs.rag.controller;

/**
 * @Title: KnowledgeController
 * @author  caoshuai
 * @Package com.cs.rag.controller
 * @date 2025/11/05 18:19
 * @description: 知识库
 */

import com.alibaba.fastjson2.JSON;
import com.cs.rag.common.ApplicationConstant;
import com.cs.rag.common.BaseResponse;
import com.cs.rag.common.ErrorCode;
import com.cs.rag.common.ResultUtils;
import com.cs.rag.entity.AliOssFile;
import com.cs.rag.pojo.dto.QueryFileDTO;
import com.cs.rag.service.AliOssFileService;
import com.cs.rag.utils.AliOssUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Tag(name = "KnowledgeController", description = "知识库管理接口")
@Slf4j
@RestController
@RequestMapping(ApplicationConstant.API_VERSION + "/knowledge")
public class KnowledgeController {

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private AliOssUtil aliOssUtil;

    @Autowired
    private TokenTextSplitter tokenTextSplitter;

    @Autowired
    private AliOssFileService aliOssFileService;
    /**
     * 上传附件接口
     *
     * @param
     * @return
     * @throws IOException
     */

    @Operation(summary = "upload", description = "上传附件接口")
    @PostMapping(value = "file/upload", headers = "content-type=multipart/form-data")
    public BaseResponse upload(@RequestParam("file") List<MultipartFile> files) {
        if (files.isEmpty()) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请上传文件");
        }
        for (MultipartFile file : files) {
            Resource resource = file.getResource();
            TikaDocumentReader tkReader = new TikaDocumentReader(resource);
            List<Document> documents = tkReader.get();
            List<Document> splitDocuments = tokenTextSplitter.apply(documents);
            vectorStore.add(splitDocuments);
            try {
                String originalFilename = file.getOriginalFilename();
                String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
                String objectName = UUID.randomUUID() + extension;
                String url = aliOssUtil.upload(file.getBytes(), objectName);
                long currMillis = System.currentTimeMillis();
                aliOssFileService.save(AliOssFile.builder()
                        .fileName(originalFilename)
                        .vectorId(JSON.toJSONString(splitDocuments.stream().map(Document::getId).collect(Collectors.toList())))
                        .url(url)
                        .createTime(new Date(currMillis))
                        .updateTime(new Date(currMillis))
                        .build());
            } catch (IOException e) {
                e.printStackTrace();
                log.info("文件上传OSS失败" + file.getOriginalFilename());
            }
        }

        return ResultUtils.success("文件上传成功");
    }


    @Operation(summary = "contents",description = "文件查询")
    @GetMapping("/contents")
    public BaseResponse queryFiles(QueryFileDTO request){
        if(request.getPage() == null || request.getPageSize() == null){
            return ResultUtils.error(ErrorCode.PARAMS_ERROR,"page 或 pageSize为空");
        }
        return aliOssFileService.queryPage(request);
    }

    @Operation(summary = "delete",description = "文件删除")
    @DeleteMapping("/delete")
    public BaseResponse deleteFiles(@RequestParam List<Long> ids){
        return aliOssFileService.deleteFiles(ids);
    }


    @Operation(summary = "download",description = "文件下载")
    @GetMapping("/download")
    public BaseResponse downloadFiles(@RequestParam List<Long> ids){
        return aliOssFileService.downloadFiles(ids);
    }





}
