package com.example.demo.service;

import com.example.demo.entity.User;
import org.springframework.data.domain.Page;

/**
 * 用户表 服务类
 */
public interface IUserService {

    String doLogin(String email, String password);

    String registered(String username, String email, String password, String code);

    String logout();

    String send(String email);

    String updataUser(User user);

    User getProfile(Long userId);

    /**
     * 获取用户列表（分页，支持关键词搜索）
     */
    Page<User> getUserList(String keyword, int pageNum, int pageSize);

    /**
     * 通过邮箱查找用户
     */
    User findByEmail(String email);

    /**
     * 获取指定用户的信息（用于展示，不包含密码）
     */
    User getUserInfo(Long userId);

    /**
     * 保存用户
     */
    User save(User user);

    /**
     * 根据ID查找用户
     */
    User getById(Long id);

    /**
     * 更新用户
     */
    boolean updateById(User user);
}