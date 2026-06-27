package com.example.cloud.common.api.user;

import java.io.Serializable;

/**
 * 用户脱敏信息，跨服务传输用。
 * <p>
 * 不含密码、邮箱等隐私字段，调用方拿到就直接渲染。
 *
 * @param userId   用户 ID
 * @param username 用户名（昵称）
 * @param avatar   头像 URL
 */
public record UserInfoDTO(
        Long userId,
        String username,
        String avatar
) implements Serializable {
}
