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
import com.cs.rag.service.AliOssFileService;
import com.cs.rag.utils.AliOssUtil;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
* @author  caoshuai
* @description 针对表【ali_oss_file】的数据库操作Service实现
* @date 2025/11/05 18:22
*/
@Service
public class AliOssFileServiceImpl extends ServiceImpl<AliOssFileMapper, AliOssFile>
    implements AliOssFileService {

    @Autowired
    private AliOssFileMapper aliOssFileMapper;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private AliOssUtil aliOssUtil;


    /**
     * 查询文件
     * @param request
     * @return
     */
    @Override
    public BaseResponse queryPage(QueryFileDTO request) {
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
            aliOssUtil.deleteOss(aliOssFile.getUrl());
        }

        return ResultUtils.success("成功删除"+ count + "个文件");
    }

    @Override
    public BaseResponse downloadFiles(List<Long> ids) {
        List<AliOssFile> aliOssFiles = aliOssFileMapper.selectByIds(ids);
        if (ids.isEmpty()) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请选择文件");
        }
        for (AliOssFile aliOssFile : aliOssFiles){
            String url = aliOssFile.getUrl();
            String fileName = extractFileName(url);
            aliOssUtil.download(fileName);
        }
        return ResultUtils.success("下载成功");
    }
    public static String extractFileName(String url) {
        // 找到最后一个斜杠的位置
        int lastSlashIndex = url.lastIndexOf('/');
        if (lastSlashIndex == -1) {
            return url; // 如果没有找到斜杠，返回整个URL
        }
        // 从最后一个斜杠之后的部分截取
        return url.substring(lastSlashIndex + 1);
    }

}




