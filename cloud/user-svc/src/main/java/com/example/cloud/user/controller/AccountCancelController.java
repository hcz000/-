package com.example.cloud.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.example.cloud.common.api.post.PostFeignClient;
import com.example.cloud.common.exception.BusinessException;
import com.example.cloud.user.service.IUserService;
import io.seata.spring.annotation.GlobalTransactional;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 账号注销 Controller（Seata AT 分布式事务演示）。
 * <p>
 * 「注销」是一个典型的跨服务级联清理场景，要求 user-svc 标记用户为已删除、
 * post-svc 软删除该用户的全部帖子，二者必须原子性完成——不能出现「用户没了，
 * 但帖子还在」的情况。
 * <p>
 * 实现要点：
 * <ul>
 *   <li>用 {@link GlobalTransactional} 注解开启全局事务，Seata 生成 xid 并透传给 Feign 调用</li>
 *   <li>user-svc 的本地 UPDATE 走 Hibernate + JPA + Hikari，被 Seata DataSource Proxy 拦截，
 *       写入 undo_log（保存修改前的快照），等待全局事务决议</li>
 *   <li>post-svc 在 InternalPostController 里同样走本地事务 + undo_log</li>
 *   <li>任何一个分支抛异常 → 全局回滚 → 两个服务都把 undo_log 反向应用，数据复原</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class AccountCancelController {

    @Resource
    private IUserService userService;

    @Resource
    private PostFeignClient postFeignClient;

    /**
     * 注销当前登录账号。
     *
     * @param simulateFailure 如果为 true，在 Feign 调用后故意抛异常，
     *                        用于演示 Seata 回滚：user 表和 postings 表都不会改动。
     */
    @PostMapping("/cancel")
    @GlobalTransactional(name = "user-cancel-tx", rollbackFor = Exception.class, timeoutMills = 60000)
    public SaResult cancelAccount(@RequestParam(required = false, defaultValue = "false") boolean simulateFailure) {
        if (!StpUtil.isLogin()) {
            return SaResult.error("用户未登录").setCode(401);
        }
        Long userId = StpUtil.getLoginIdAsLong();
        log.info("[cancel] start userId={}, simulateFailure={}", userId, simulateFailure);

        // Step 1 (本地分支): 软删 user
        boolean userDeleted = userService.softDelete(userId);
        if (!userDeleted) {
            throw new BusinessException("用户不存在或已注销");
        }

        // Step 2 (远程分支): 通过 Feign 让 post-svc 软删该用户所有帖子
        Integer postsAffected = postFeignClient.deletePostsByUser(userId);
        log.info("[cancel] post-svc returned {} posts deleted", postsAffected);

        // 故意失败演示：到此两个分支都已经写了 undo_log，抛异常会触发全局回滚
        if (simulateFailure) {
            log.warn("[cancel] simulateFailure=true → throw to trigger global rollback");
            throw new BusinessException("演示用：注销最后一步失败，触发 Seata 回滚");
        }

        // 成功：sa-token 登出
        StpUtil.logout(userId);

        Map<String, Object> data = new HashMap<>(3);
        data.put("userId", userId);
        data.put("postsAffected", postsAffected);
        data.put("message", "账号注销成功");
        return SaResult.ok().setData(data);
    }
}
