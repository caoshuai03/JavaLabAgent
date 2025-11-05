package com.cs.rag.service;

import com.cs.rag.common.PageResult;
import com.cs.rag.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cs.rag.pojo.dto.UserDTO;
import com.cs.rag.pojo.dto.UserPageQueryDTO;

import javax.security.auth.login.AccountLockedException;
import javax.security.auth.login.AccountNotFoundException;

/**
* @author  caoshuai
* @description 针对表【user】的数据库操作Service
* @date 2025/11/05 18:22
*/
public interface UserService extends IService<User> {

    User login(String userName, String password) throws AccountNotFoundException, AccountLockedException;

    void saveUser(UserDTO userDTO);

    PageResult pageQuery(UserPageQueryDTO userPageQueryDTO);

    void startOrStop(Integer status, Integer id);

    void updateUser(UserDTO userDTO);

    void register(User user);

    boolean getByUsername(String userName);
}