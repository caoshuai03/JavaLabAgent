package com.cs.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cs.rag.common.PageResult;
import com.cs.rag.entity.WordFrequency;
import com.cs.rag.mapper.WordFrequencyMapper;
import com.cs.rag.pojo.dto.WordFrequencyPageQueryDTO;
import com.cs.rag.service.WordFrequencyService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
* @author  caoshuai
* @description 针对表【word_frequency】的数据库操作Service实现
* @date 2025/11/05 18:22
*/
@Service
public class WordFrequencyServiceImpl extends ServiceImpl<WordFrequencyMapper, WordFrequency>
    implements WordFrequencyService {

    @Override
    public PageResult pageQuery(WordFrequencyPageQueryDTO queryDTO) {
        Page<WordFrequency> page = new Page<>(queryDTO.getPage(), queryDTO.getPageSize());

        LambdaQueryWrapper<WordFrequency> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotEmpty(queryDTO.getWord()),
                        WordFrequency::getWord, queryDTO.getWord())
                .eq(StringUtils.isNotEmpty(queryDTO.getBusinessType()),
                        WordFrequency::getBusinessType, queryDTO.getBusinessType())
                .gt(queryDTO.getCountNumMin() != null,
                        WordFrequency::getCountNum, queryDTO.getCountNumMin())
                .orderByDesc(WordFrequency::getCountNum);

        this.page(page, wrapper);
        return new PageResult(page.getTotal(), page.getRecords());
    }
}




