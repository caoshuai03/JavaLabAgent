package com.cs.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.pagehelper.Page;
import com.cs.rag.entity.User;
import com.cs.rag.pojo.dto.UserPageQueryDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
* @author  caoshuai
* @description 针对表【user】的数据库操作Mapper
* @date 2025/11/05 18:22
* @Entity com.cs.rag.entity.User
*/
@Mapper
public interface UserMapper extends BaseMapper<User> {
    @Select("select * from tb_user where user_name = #{userName}")
    User getByUsername(@Param("userName") String userName);

    Integer updateUser(User user);

    Page<User> pageQuery(UserPageQueryDTO userPageQueryDTO);
}