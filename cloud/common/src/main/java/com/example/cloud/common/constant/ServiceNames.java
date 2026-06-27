package com.example.cloud.common.constant;

/**
 * 微服务名称常量，所有 Feign 客户端用这里的常量声明 {@code @FeignClient(name = ...)}。
 * 值要和各服务 {@code spring.application.name} 完全一致，注册到 Nacos 时用同名。
 */
public final class ServiceNames {

    public static final String USER_SVC = "user-svc";
    public static final String POST_SVC = "post-svc";
    public static final String PUSH_SVC = "push-svc";
    public static final String GATEWAY = "api-gateway";

    private ServiceNames() {}
}
