package com.cs.rag.service;

import com.cs.rag.common.BaseResponse;
import com.cs.rag.entity.AliOssFile;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cs.rag.pojo.dto.QueryFileDTO;

import java.util.List;

/**
* @author  caoshuai
* @description 针对表【ali_oss_file】的数据库操作Service
* @date 2025/11/05 18:22
*/
public interface AliOssFileService extends IService<AliOssFile> {

    BaseResponse queryPage(QueryFileDTO request);

    BaseResponse deleteFiles(List<Long> ids);

    BaseResponse downloadFiles(List<Long> ids);
}
