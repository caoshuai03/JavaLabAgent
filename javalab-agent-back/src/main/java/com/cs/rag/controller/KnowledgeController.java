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
import com.cs.rag.utils.QaDocumentSplitter;
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
import java.util.*;
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
     * @param files 上传的文件列表
     * @return 上传结果
     */
    @Operation(summary = "upload", description = "上传附件接口")
    @PostMapping(value = "file/upload", headers = "content-type=multipart/form-data")
    public BaseResponse upload(@RequestParam("file") List<MultipartFile> files) {
        if (files.isEmpty()) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请上传文件");
        }

        for (MultipartFile file : files) {
            String originalFilename = file.getOriginalFilename();
            log.info("开始处理文件上传: {}", originalFilename);

            try {
                // 1. 读取文档内容
                Resource resource = file.getResource();
                log.debug("文件资源获取成功: {}", originalFilename);

                TikaDocumentReader tkReader = new TikaDocumentReader(resource);
                List<Document> documents = tkReader.get();
                log.info("文档解析成功，原始文档数: {}", documents.size());

                // 2. 文档分割：根据文档内容智能选择分割器
                List<Document> splitDocuments;

                // 检查文档是否为QA格式（包含 "---" 分隔符和 "## Q:" 标识）
                boolean isQaFormat = false;
                if (!documents.isEmpty()) {
                    String content = documents.get(0).getText();
                    // 判断是否为QA格式：同时包含分隔符和问题标识
                    isQaFormat = content.contains("---") && content.contains("## Q:");
                }

                if (isQaFormat) {
                    // 使用自定义QA分割器：按 "---" 分隔符拆分QA对
                    log.info("检测到QA格式文档，使用QaDocumentSplitter进行分割");
                    splitDocuments = QaDocumentSplitter.split(documents);
                    log.info("QA文档分割完成，生成 {} 个QA对", splitDocuments.size());
                } else {
                    // 使用默认的Token分割器：按token数量智能切分
                    log.info("普通文档格式，使用TokenTextSplitter进行分割");
                    splitDocuments = tokenTextSplitter.apply(documents);
                    log.info("文档分割完成，分割后文档数: {}", splitDocuments.size());
                }

                // 3. 向量化存储（添加异常处理和日志）
                List<String> vectorIds = new ArrayList<>();
                try {
                    log.info("开始向量化存储，文档数量: {}", splitDocuments.size());
                    vectorStore.add(splitDocuments);

                    // 获取向量ID
                    vectorIds = splitDocuments.stream()
                            .map(Document::getId)
                            .collect(Collectors.toList());

                    log.info("向量化存储成功，文件: {}, 向量ID数量: {}, 向量ID列表: {}",
                            originalFilename, vectorIds.size(), vectorIds);
                } catch (Exception e) {
                    log.error("向量化存储失败，文件: {}, 错误信息: {}", originalFilename, e.getMessage(), e);
                    // 即使向量化失败，也继续执行OSS上传和数据库保存
                    // 但vectorId将为空，后续可以通过重新上传来修复
                }

                // 4. OSS上传
                int lastDotIndex = originalFilename.lastIndexOf(".");
                String nameWithoutExt = lastDotIndex > 0
                        ? originalFilename.substring(0, lastDotIndex)
                        : originalFilename;
                String extension = lastDotIndex > 0
                        ? originalFilename.substring(lastDotIndex)
                        : "";

                // 清理文件名中的非法字符
                nameWithoutExt = nameWithoutExt.replaceAll("[\\\\/:*?\"<>|]", "_");

                // 生成有意义的文件名：原始文件名_时间戳.扩展名
                String timestamp = String.valueOf(System.currentTimeMillis());
                String objectName = nameWithoutExt + "_" + timestamp + extension;

                log.info("开始上传文件到OSS: {}", objectName);
                String url = aliOssUtil.upload(file.getBytes(), objectName);
                log.info("OSS上传成功，URL: {}", url);

                // 5. 保存数据库记录
                long currMillis = System.currentTimeMillis();
                aliOssFileService.save(AliOssFile.builder()
                        .fileName(originalFilename)
                        .vectorId(JSON.toJSONString(vectorIds))
                        .url(url)
                        .createTime(new Date(currMillis))
                        .updateTime(new Date(currMillis))
                        .build());
                log.info("数据库记录保存成功，文件: {}", originalFilename);

            } catch (IOException e) {
                log.error("文件处理IO异常，文件: {}, 错误信息: {}", originalFilename, e.getMessage(), e);
                e.printStackTrace();
            } catch (Exception e) {
                log.error("文件上传处理异常，文件: {}, 错误信息: {}", originalFilename, e.getMessage(), e);
                e.printStackTrace();
            }
        }

        return ResultUtils.success("文件上传成功");
    }

    @Operation(summary = "contents", description = "文件查询")
    @GetMapping("/contents")
    public BaseResponse queryFiles(QueryFileDTO request) {
        if (request.getPage() == null || request.getPageSize() == null) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "page 或 pageSize为空");
        }
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
