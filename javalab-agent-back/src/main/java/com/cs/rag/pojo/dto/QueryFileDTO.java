package com.cs.rag.pojo.dto;

import lombok.Data;

/**
 * @Title: QueryFileDTO
 * @author  caoshuai
 * @Package com.cs.rag.pojo.dto
 * @date 2025/11/05 18:19
 * @description: 查找文件dto
 */
@Data
public class QueryFileDTO {
    private Integer page;
    private Integer pageSize;
    private String fileName;
}
