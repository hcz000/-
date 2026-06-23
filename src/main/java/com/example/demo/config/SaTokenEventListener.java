package com.example.demo.config;

import cn.dev33.satoken.listener.SaTokenListener;
import cn.dev33.satoken.stp.SaLoginModel;
import com.example.demo.service.UserVectorService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Sa-Token 会话事件监听器
 * <p>
 * 兜底清理：token 过期、被踢下线等非主动登出场景，
 * 确保用户兴趣模型不会残留在 Redis 中。
 */
@Slf4j
@Component
public class SaTokenEventListener implements SaTokenListener {

    @Resource
    private UserVectorService userVectorService;

    @Override
    public void doLogin(String loginType, Object loginId, String tokenValue, SaLoginModel loginModel) {
        // 登录已在 UserServiceImpl.doLogin() 中处理加载，此处不重复
    }

    @Override
    public void doLogout(String loginType, Object loginId, String tokenValue) {
        // 主动登出已在 UserServiceImpl.logout() 中处理，此处幂等兜底
        try {
            userVectorService.removeFromRedis(Long.parseLong(loginId.toString()));
        } catch (NumberFormatException e) {
            log.warn("[sa-token] invalid loginId on logout: {}", loginId);
        }
    }

    @Override
    public void doKickout(String loginType, Object loginId, String tokenValue) {
        try {
            userVectorService.removeFromRedis(Long.parseLong(loginId.toString()));
        } catch (NumberFormatException e) {
            log.warn("[sa-token] invalid loginId on kickout: {}", loginId);
        }
    }

    @Override
    public void doReplaced(String loginType, Object loginId, String tokenValue) {
        // 被顶替下线时也清理
        try {
            userVectorService.removeFromRedis(Long.parseLong(loginId.toString()));
        } catch (NumberFormatException e) {
            log.warn("[sa-token] invalid loginId on replaced: {}", loginId);
        }
    }

    @Override
    public void doDisable(String loginType, Object loginId, String service, int level, long disableTime) {
        // 封禁时清理
        try {
            userVectorService.removeFromRedis(Long.parseLong(loginId.toString()));
        } catch (NumberFormatException e) {
            log.warn("[sa-token] invalid loginId on disable: {}", loginId);
        }
    }

    @Override
    public void doUntieDisable(String loginType, Object loginId, String service) {
        // 解封时不需要重新加载，用户下次登录时会自动加载
    }

    @Override
    public void doOpenSafe(String loginType, String loginId, String service, long safeTime) {
        // 开启二级认证
    }

    @Override
    public void doCloseSafe(String loginType, String loginId, String service) {
        // 关闭二级认证
    }

    @Override
    public void doCreateSession(String id) {
        // Session 创建
    }

    @Override
    public void doLogoutSession(String id) {
        // Session 注销
    }

    @Override
    public void doRenewTimeout(String loginType, Object loginId, long timeout) {
        // Sa-Token 每次请求续期 token 时，同步续期向量 TTL
        try {
            userVectorService.renewTtl(Long.parseLong(loginId.toString()));
        } catch (NumberFormatException e) {
            log.warn("[sa-token] invalid loginId on renew: {}", loginId);
        }
    }
}
