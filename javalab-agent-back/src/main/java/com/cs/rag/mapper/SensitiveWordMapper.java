package com.cs.rag.mapper;

import com.cs.rag.entity.SensitiveWord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
* @author  caoshuai
* @description 针对表【sensitive_word】的数据库操作Mapper
* @date 2025/11/05 18:22
* @Entity com.cs.rag.entity.SensitiveWord
*/

@Mapper
public interface SensitiveWordMapper extends BaseMapper<SensitiveWord> {

}




