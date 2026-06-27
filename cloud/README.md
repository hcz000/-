# KnowledgePlanet 微服务版本

单体 `../src/` 的微服务化版本。**仅用于面试技能展示**，不替代单体项目。

## 架构

```
                    ┌──────────────────┐
                    │   api-gateway    │  :8080
                    │ (Spring Cloud GW)│
                    └────────┬─────────┘
                             │
       ┌─────────────────────┼─────────────────────┐
       ▼                     ▼                     ▼
┌─────────────┐       ┌─────────────┐       ┌─────────────┐
│  user-svc   │       │  post-svc   │       │  push-svc   │
│   :8081     │◄─────►│   :8082     │◄─────►│   :8083     │
│ 用户/认证   │ Feign │ 帖子/评论   │ Feign │ 推荐/去重   │
└─────────────┘       └──────┬──────┘       └──────┬──────┘
                             │ outbox + MQ          │
                             └──────────────────────┘
                                       │
                  ┌────────────────────┴───────────────────┐
                  ▼                                        ▼
        ┌────────────────┐                      ┌────────────────┐
        │  Nacos :8848   │                      │  MySQL :3306   │
        │  注册 + 配置   │                      │  Redis :6379   │
        └────────────────┘                      │  RabbitMQ      │
                                                │  Zipkin :9411  │
        ┌────────────────┐                      └────────────────┘
        │  Seata :8091   │
        │  分布式事务    │
        └────────────────┘
```

## 模块说明

| 模块 | 端口 | 职责 |
|---|---|---|
| `common` | — | 跨服务共享：DTO、Feign 接口、统一响应 `R<T>`、服务名常量 |
| `gateway` | 8080 | API 入口、sa-token 鉴权下沉、Redis 限流 |
| `user-svc` | 8081 | 用户信息、注册登录、JWT、好友关系 |
| `post-svc` | 8082 | 帖子、评论、星球、审核、**outbox 发布事件** |
| `push-svc` | 8083 | 推荐策略、候选池、已曝光去重、**消费 outbox 事件** |

## 技术选型

| 类别 | 选型 | 理由 |
|---|---|---|
| 注册 + 配置中心 | **Nacos** | 二合一，运维成本低 |
| 服务调用 | **OpenFeign + LoadBalancer** | 声明式，集成简单 |
| 网关 | **Spring Cloud Gateway** | 基于 Reactor，性能比 Zuul 好 |
| 强一致分布式事务 | **Seata AT** | 关键场景（如用户注销级联删除） |
| 最终一致 | **本地消息表 + RabbitMQ** | 非关键场景（如发帖入推送池），性能高 10× |
| 链路追踪 | **Micrometer + Zipkin** | 单体已经在用，沿用 |

## 版本

- Spring Boot **3.3.5**
- Spring Cloud **2023.0.3**
- Spring Cloud Alibaba **2023.0.3.2**
- Java **21**

> 注：与单体（SB 3.4.3）版本不一致是因为 SCA 当前还不兼容 SB 3.4.x，等 SCA 跟进后可统一升级。

## 编译

```bash
cd cloud
mvn clean install -DskipTests
```

## 启动顺序

1. 启动中间件（Nacos / MySQL / Redis / RabbitMQ / Zipkin）
2. 把各服务 `application.yml` 里的 `nacos.discovery.enabled` 和 `nacos.config.enabled` 改为 `true`
3. 启动顺序：`gateway` → `user-svc` → `post-svc` → `push-svc`（其实顺序不重要，Spring Cloud 容忍乱序）

## 当前进度

- [x] 多模块 Maven 骨架
- [x] 5 个模块的 pom.xml + Application 类
- [ ] Nacos / 中间件 docker-compose
- [ ] 把单体代码按领域搬过来
- [ ] Feign 跨服务调用
- [ ] 网关 sa-token 鉴权 Filter
- [ ] outbox 表 + 后台扫描任务
- [ ] Seata AT 模式接入
