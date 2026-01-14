package com.cs.rag.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cs.rag.common.BaseResponse;
import com.cs.rag.common.ErrorCode;
import com.cs.rag.common.ResultUtils;
import com.cs.rag.entity.AliOssFile;
import com.cs.rag.mapper.AliOssFileMapper;
import com.cs.rag.pojo.dto.QueryFileDTO;
import com.cs.rag.pojo.dto.FileDownloadInfo;
import com.cs.rag.service.AliOssFileService;
import com.cs.rag.utils.StorageUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;

/**
* @author  caoshuai
* @description 针对表【ali_oss_file】的数据库操作Service实现
* @date 2025/11/05 18:22
*/
@Slf4j
@Service
public class AliOssFileServiceImpl extends ServiceImpl<AliOssFileMapper, AliOssFile>
    implements AliOssFileService {

    @Autowired
    private AliOssFileMapper aliOssFileMapper;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private StorageUtil storageUtil;


    /**
     * 查询文件
     * @param request 查询请求参数
     * @return 分页文件列表
     */
    @Override
    public BaseResponse queryPage(QueryFileDTO request) {
        // 参数校验
        if (request.getPage() == null || request.getPageSize() == null) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "分页参数不能为空");
        }
        Page<AliOssFile> page = new Page<>(request.getPage(), request.getPageSize());
        IPage<AliOssFile> fileList = aliOssFileMapper.findByFileNameContaining(page, request.getFileName());
        return ResultUtils.success(fileList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResponse deleteFiles(List<Long> ids) {
        List<AliOssFile> aliOssFiles = aliOssFileMapper.selectByIds(ids);
        if (ids.isEmpty()) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请选择文件");
        }
        int count = aliOssFileMapper.deleteBatchIds(ids);
        if (count == 0) {
            return ResultUtils.error(ErrorCode.OPERATION_ERROR, "删除失败");
        }
        for (AliOssFile aliOssFile : aliOssFiles) {
            List<String> vectorIds = JSON.parseArray(aliOssFile.getVectorId(), String.class);
            vectorStore.delete(vectorIds);
            storageUtil.delete(aliOssFile.getUrl());
        }

        return ResultUtils.success("成功删除"+ count + "个文件");
    }

    @Override
    public BaseResponse downloadFiles(List<Long> ids) {
        // 修复参数检查错误：应该先检查ids是否为空
        if (ids == null || ids.isEmpty()) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请选择文件");
        }
        List<AliOssFile> aliOssFiles = aliOssFileMapper.selectByIds(ids);
        for (AliOssFile aliOssFile : aliOssFiles){
            String url = aliOssFile.getUrl();
            String fileName = extractFileName(url);
            storageUtil.download(fileName);
        }
        return ResultUtils.success("下载成功");
    }

    @Override
    public FileDownloadInfo getFileForDownload(Long id) {
        // 参数校验
        if (id == null) {
            throw new RuntimeException("文件ID不能为空");
        }
        
        // 查询文件信息
        AliOssFile aliOssFile = aliOssFileMapper.selectById(id);
        if (aliOssFile == null) {
            throw new RuntimeException("文件不存在");
        }
        
        try {
            // 从URL中提取文件名
            String fileName = extractFileName(aliOssFile.getUrl());
            
            // 添加调试日志
            log.info("文件下载调试 - 文件ID: {}, 原始URL: {}, 提取的文件名: {}", 
                id, aliOssFile.getUrl(), fileName);
            
            // 获取文件输入流
            InputStream inputStream = storageUtil.getObject(fileName);
            
            // 返回文件下载信息
            return new FileDownloadInfo(
                aliOssFile.getFileName(), 
                inputStream
            );
        } catch (Exception e) {
            throw new RuntimeException("获取文件失败: " + e.getMessage());
        }
    }

    public static String extractFileName(String url) {
        // 从完整URL中提取MinIO对象路径（包含文件夹前缀）
        // URL格式：endpoint/bucket/java-lab-agent-rag/filename.ext
        try {
            java.net.URI uri = java.net.URI.create(url);
            String path = uri.getPath();
            // 移除开头的斜杠
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            
            // 查找 java-lab-agent-rag 的位置，直接提取从该前缀开始的部分
            int folderIndex = path.indexOf("java-lab-agent-rag/");
            if (folderIndex != -1) {
                return path.substring(folderIndex);
            }
            
            // 如果没有找到文件夹前缀，可能是bucket/java-lab-agent-rag/filename的格式
            // 尝试移除第一个路径段（bucket名称）
            int firstSlashIndex = path.indexOf('/');
            if (firstSlashIndex != -1) {
                return path.substring(firstSlashIndex + 1);
            }
            
            return path;
        } catch (Exception e) {
            // 如果URL格式不正确，尝试直接提取路径
            int folderIndex = url.indexOf("java-lab-agent-rag/");
            if (folderIndex != -1) {
                return url.substring(folderIndex);
            }
            
            // 最后的备选方案：返回最后一个斜杠后的部分
            int lastSlashIndex = url.lastIndexOf('/');
            return lastSlashIndex != -1 ? url.substring(lastSlashIndex + 1) : url;
        }
    }

}




