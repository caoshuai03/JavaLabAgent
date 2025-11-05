package com.cs.rag.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cs.rag.entity.AliOssFile;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
* @author  caoshuai
* @description 针对表【ali_oss_file】的数据库操作Mapper
* @date 2025/11/05 18:22
* @Entity com.cs.rag.entity.AliOssFile
*/

@Mapper
public interface AliOssFileMapper extends BaseMapper<AliOssFile> {

    IPage<AliOssFile> findByFileNameContaining(Page<AliOssFile> page, String fileName);
}




