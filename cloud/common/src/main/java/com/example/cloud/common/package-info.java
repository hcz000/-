/**
 * 跨服务共享的契约层。
 * <p>
 * 包含：
 * <ul>
 *   <li>{@code dto}      —— 跨服务传输的数据对象</li>
 *   <li>{@code api}      —— Feign 客户端接口（user-svc / post-svc / push-svc 各自子包）</li>
 *   <li>{@code constant} —— 服务名、Redis key 前缀等常量</li>
 *   <li>{@code result}   —— 统一响应体 {@code R<T>}</li>
 * </ul>
 *
 * 注意：这个模块只能依赖 spring-web / validation / lombok，
 * 不要拉入任何具体服务的实现细节，避免环依赖。
 */
package com.example.cloud.common;
