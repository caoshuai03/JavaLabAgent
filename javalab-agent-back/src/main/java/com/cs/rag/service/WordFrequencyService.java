package com.cs.rag.service;

import com.cs.rag.common.PageResult;
import com.cs.rag.entity.WordFrequency;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cs.rag.pojo.dto.WordFrequencyPageQueryDTO;

/**
* @author  caoshuai
* @description 针对表【word_frequency】的数据库操作Service
* @date 2025/11/05 18:22
*/
public interface WordFrequencyService extends IService<WordFrequency> {

    PageResult pageQuery(WordFrequencyPageQueryDTO queryDTO);
}
