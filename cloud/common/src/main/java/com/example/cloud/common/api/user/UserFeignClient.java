package com.example.cloud.common.api.user;

import com.example.cloud.common.constant.ServiceNames;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * user-svc 的内部接口 Feign 客户端。
 * <p>
 * 路径约定：{@code /internal/**} 表示「服务间内部接口」，
 * <ul>
 *   <li>不需要登录鉴权（默认调用方就是其他可信微服务）</li>
 *   <li>不通过网关对外暴露</li>
 *   <li>返回的 DTO 是脱敏版（不含密码）</li>
 * </ul>
 *
 * 当前未启用 Feign 自带的 fallback，因为：
 * <ul>
 *   <li>fallback 类需要在调用方作为 Spring Bean，写在 common 会形成反向依赖</li>
 *   <li>降级逻辑放在调用方的 service 层（用 @CircuitBreaker 注解），职责更清晰</li>
 * </ul>
 */
@FeignClient(name = ServiceNames.USER_SVC, contextId = "userFeignClient")
public interface UserFeignClient {

    /**
     * 单个用户脱敏信息。
     */
    @GetMapping("/internal/user/{userId}")
    UserInfoDTO getUserById(@PathVariable("userId") Long userId);

    /**
     * 批量获取用户脱敏信息。
     * <p>
     * 避免 N+1：推送返回 10 条帖子时，把 10 个 userId 一次性问过来，
     * 而不是发 10 次 RPC。
     *
     * @param userIds 逗号分隔的用户 ID 列表（HTTP 查询参数限制）
     * @return userId → UserInfoDTO 的映射（找不到的 userId 不在 map 中）
     */
    @GetMapping("/internal/user/batch")
    Map<Long, UserInfoDTO> getUsersByIds(@RequestParam("ids") List<Long> userIds);

    /**
     * 获取用户角色（如 "admin" / "user"）。
     * 供其他服务做 admin 鉴权时使用。
     */
    @GetMapping("/internal/user/{userId}/role")
    String getUserRole(@PathVariable("userId") Long userId);
}
