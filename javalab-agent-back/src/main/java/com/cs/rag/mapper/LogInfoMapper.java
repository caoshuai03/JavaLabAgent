package com.cs.rag.mapper;

import com.cs.rag.entity.LogInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
* @author  caoshuai
* @description 针对表【log_info】的数据库操作Mapper
* @date 2025/11/05 18:22
* @Entity com.cs.rag.entity.LogInfo
*/

@Mapper
public interface LogInfoMapper extends BaseMapper<LogInfo> {

}




