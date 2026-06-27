package com.example.cloud.user.service;

import com.example.cloud.user.entity.User;

/**
 * 用户服务接口
 */
public interface IUserService {

    String doLogin(String email, String password);

    String registered(String username, String email, String password, String code);

    String logout();

    String send(String email);

    String updateUser(User user);

    User getProfile(Long userId);

    /**
     * 通过邮箱查找用户（脱敏，不返回密码）
     */
    User findByEmail(String email);

    /**
     * 获取指定用户的信息（脱敏，不返回密码）
     */
    User getUserInfo(Long userId);

    /**
     * 根据 ID 查找用户（原始数据，包含密码 —— 仅供服务内部使用）
     */
    User getById(Long id);

    /**
     * 软删除用户（账号注销）。
     * <p>
     * 仅本服务内的 DB 写操作；跨服务级联（如删帖）由 Controller 层
     * 在 {@code @GlobalTransactional} 内通过 Feign 调用，本方法不负责。
     *
     * @return 是否实际改动了行（true = 之前未删除，false = 用户不存在或已注销）
     */
    boolean softDelete(Long userId);
}
