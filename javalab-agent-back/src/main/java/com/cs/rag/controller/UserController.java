package com.cs.rag.controller;

import com.cs.rag.common.*;
import com.cs.rag.config.JwtProperties;
import com.cs.rag.constant.JwtClaimsConstant;
import com.cs.rag.constant.UserMessageConstant;
import com.cs.rag.entity.User;
import com.cs.rag.pojo.dto.PasswordDTO;
import com.cs.rag.pojo.dto.UserDTO;
import com.cs.rag.pojo.dto.UserPageQueryDTO;
import com.cs.rag.pojo.vo.UserInfoVO;
import com.cs.rag.pojo.vo.UserLoginVO;
import com.cs.rag.service.UserService;
import com.cs.rag.utils.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;

import javax.security.auth.login.AccountLockedException;
import javax.security.auth.login.AccountNotFoundException;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户管理控制器
 *
 * <p>负责处理用户相关的HTTP请求，包括:</p>
 * <ul>
 *   <li>用户认证（登录/登出/注册）</li>
 *   <li>用户信息管理（查询/更新）</li>
 *   <li>密码管理</li>
 *   <li>账号状态管理（启用/禁用）</li>
 * </ul>
 *
 * @author caoshuai
 * @since 1.0
 */
@Tag(name = "UserController", description = "用户管理")
@Slf4j
@RestController
@RequestMapping(ApplicationConstant.API_VERSION + "/user")
public class UserController {

    /**
     * 用户业务服务
     */
    @Autowired
    private UserService userService;

    /**
     * JWT配置属性
     */
    @Autowired
    private JwtProperties jwtProperties;


    /**
     * 修改密码
     */
    @PostMapping("/updatePassword")
    @Operation(summary = "updatePassword", description = "修改密码")
    public BaseResponse updatePassword(@RequestBody PasswordDTO passwordDTO) {
        log.info("修改密码：{}", passwordDTO.toString());
        if (!passwordDTO.getNewPassword().equals(passwordDTO.getConfirmPassword())) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, UserMessageConstant.PASSWORD_NOT_MATCH);
        }
        User user = userService.getById(passwordDTO.getId());
        String s = DigestUtils.md5DigestAsHex(passwordDTO.getOldPassword().getBytes());
        if (!user.getPassword().equals(s)) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, UserMessageConstant.OLD_PASSWORD_ERROR);
        }
        user.setPassword(DigestUtils.md5DigestAsHex(passwordDTO.getNewPassword().getBytes()));
        userService.updateById(user);
        return ResultUtils.success(UserMessageConstant.PASSWORD_EDIT_SUCCESS);
    }

    /**
     * 注册
     */
    @PostMapping("/register")
    @Operation(summary = "register", description = "注册")
    public BaseResponse register(@RequestBody User user) {
        log.info("注册：{}", user.toString());

        if (userService.getByUsername(user.getUserName())) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, UserMessageConstant.USERNAME_ALREADY_EXISTS);
        } else {
            userService.register(user);
        }
        return ResultUtils.success(UserMessageConstant.REGISTER_SUCCESS);
    }


    /**
     * 登录
     *
     * @param
     * @return
     */
    @PostMapping("/login")
    @Operation(summary = "login", description = "登录")
    public BaseResponse login(@RequestParam(value = "userName", defaultValue = "admin") String userName,
                              @RequestParam(value = "password", defaultValue = "123456") String password) throws AccountLockedException, AccountNotFoundException {
        log.info("登录：{}", userName + ":" + password);

        User user = userService.login(userName, password);

        //登录成功后，生成jwt令牌
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        String token = JwtUtil.createJWT(
                jwtProperties.getUserSecretKey(),
                jwtProperties.getUserTtl(),
                claims);

        UserLoginVO userLoginVO = UserLoginVO.builder()
                .id(user.getId())
                .userName(user.getUserName())
                .name(user.getName())
                .token(token)
                .build();

        return ResultUtils.success(userLoginVO);
    }

    /**
     * 退出
     *
     * @return
     */
    @PostMapping("/logout")
    @Operation(summary = "logout", description = "退出")
    public BaseResponse<String> logout() {
        return ResultUtils.success(UserMessageConstant.LOGOUT_SUCCESS);
    }

    /**
     * 新增
     *
     * @param userDTO
     * @return
     */
    @PostMapping("/addUser")
    @Operation(summary = "logout", description = "新增user")
    public BaseResponse save(@RequestBody UserDTO userDTO) {
        log.info("新增员工：{}", userDTO);
        userService.saveUser(userDTO);
        return ResultUtils.success(UserMessageConstant.ADD_SUCCESS);
    }

    /**
     * 员工分页查询
     *
     * @param userPageQueryDTO
     * @return
     */
    @GetMapping("/page")
    @Operation(summary = "page", description = "user分页查询")
    public BaseResponse<PageResult> page(UserPageQueryDTO userPageQueryDTO) {
        log.info("员工分页查询，参数为：{}", userPageQueryDTO);
        PageResult pageResult = userService.pageQuery(userPageQueryDTO);
        return ResultUtils.success(pageResult);
    }

    /**
     * 启用禁用员工账号
     *
     * @param status
     * @param id
     * @return
     */
    @PostMapping("/status/{status}")
    @Operation(summary = "status", description = "启用禁用账号")
    public BaseResponse startOrStop(@PathVariable Integer status, Integer id) {
        log.info("启用禁用员工账号：{},{}", status, id);
        userService.startOrStop(status, id);
        return ResultUtils.success(UserMessageConstant.DISABLE_SUCCESS);
    }

    /**
     * 根据id查询员工信息
     *
     * @param id
     * @return 返回 UserInfoVO，隐藏敏感信息（密码、身份证号等）
     */
    @GetMapping("/{id}")
    @Operation(summary = "info", description = "根据id查询user信息")
    public BaseResponse<UserInfoVO> getById(@PathVariable Long id) {
        User user = userService.getById(id);
        // 转换为 VO，隐藏敏感字段
        UserInfoVO userInfoVO = UserInfoVO.builder()
                .id(user.getId())
                .name(user.getName())
                .userName(user.getUserName())
                .phone(user.getPhone())
                .sex(user.getSex())
                .status(user.getStatus())
                .build();
        return ResultUtils.success(userInfoVO);
    }

    /**
     * 编辑员工信息
     *
     * @param user
     * @return
     */
    @PutMapping("/update")
    @Operation(summary = "info", description = "编辑user信息")
    public BaseResponse update(@RequestBody User user) {
        log.info("编辑员工信息：{}", user);
        userService.updateById(user);
        return ResultUtils.success(UserMessageConstant.EDIT_SUCCESS);
    }

    /**
     * 验证token有效性
     *
     * @param id
     * @return 返回 UserInfoVO，隐藏敏感信息
     */
    @GetMapping("/validate")
    @Operation(summary = "validate", description = "验证用户token有效性")
    public BaseResponse<UserInfoVO> validateToken(@RequestParam Long id) {
        // JwtTokenUserInterceptor 会自动验证token有效性
        // 如果能执行到这里，说明token有效
        User user = userService.getById(id);
        // 转换为 VO，隐藏敏感字段
        UserInfoVO userInfoVO = UserInfoVO.builder()
                .id(user.getId())
                .name(user.getName())
                .userName(user.getUserName())
                .phone(user.getPhone())
                .sex(user.getSex())
                .status(user.getStatus())
                .build();
        return ResultUtils.success(userInfoVO);
    }

    /**
     * 更新当前用户信息
     *
     * @param userDTO
     * @return
     */
    @PutMapping("/update/info")
    @Operation(summary = "updateInfo", description = "更新当前用户信息")
    public BaseResponse updateInfo(@RequestBody UserDTO userDTO) {
        log.info("更新用户信息：{}", userDTO);
        try {
            userService.updateUser(userDTO);
            return ResultUtils.success(UserMessageConstant.UPDATE_SUCCESS);
        } catch (Exception e) {
            log.error("更新用户信息失败：", e);
            return ResultUtils.error(ErrorCode.UPDATE_ERROR, UserMessageConstant.UPDATE_FAILED + ": " + e.getMessage());
        }
    }
}