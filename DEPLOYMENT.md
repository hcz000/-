# 部署指南

KnowledgePlanet 后端 + Admin 管理后台的部署文档。

## 一、环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 21 | 项目使用了虚拟线程（`spring.threads.virtual.enabled=true`） |
| Maven | 3.9.x | 推荐用项目自带的 `mvnw`，自动下载 3.9.12 |
| MySQL | 8.0+ | `utf8mb4_0900_ai_ci` collation |
| Redis | 6.2+ | 缓存、限流、点赞计数缓冲 |
| RabbitMQ | 3.10+ | 通知、点赞刷盘 |
| Node.js | 18+ | 只在构建 admin 前端时需要 |

可选：
- **Zipkin** — 链路追踪（`http://localhost:9411`），不启不影响业务
- **阿里云 OSS** — 图片上传，没配置会用环境变量占位符

---

## 二、中间件准备

### MySQL

```sql
-- 1. 创建数据库（脚本里已包含）
mysql -uroot -p < database_schema.sql
```

默认连接信息（[application.yml](src/main/resources/application.yml)）：

```
url:      jdbc:mysql://localhost:3306/knowledgeplanet
username: root
password: 123456
```

生产环境**务必**改账号密码，并通过环境变量注入（见第五节）。

### Redis

```bash
# Linux
sudo systemctl start redis

# Docker（最快）
docker run -d --name redis -p 6379:6379 redis:7-alpine
```

默认 `localhost:6379`，无密码。

### RabbitMQ

```bash
# Docker
docker run -d --name rabbitmq \
  -p 5672:5672 -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=root \
  -e RABBITMQ_DEFAULT_PASS=123456 \
  rabbitmq:3.12-management
```

管理后台：`http://localhost:15672` （root / 123456）

首次启动后 [RabbitMQConfig.java](src/main/java/com/example/demo/config/RabbitMQConfig.java) 会自动建队列：
- `notification` — 通知推送
- `like-sync` — 点赞计数刷盘
- `like-user-sync` — 用户点赞列表刷盘
- `dead-letter.queue` — 死信兜底

---

## 三、构建

### 后端

```bash
# Linux / macOS
./mvnw clean package -DskipTests

# Windows
mvnw.cmd clean package -DskipTests
```

产物：`target/KnowledgePlanet-0.0.1-SNAPSHOT.jar`

### Admin 前端

```bash
cd admin
npm install
npm run build
```

产物：`admin/dist/`，部署到 Nginx 静态目录即可。

---

## 四、启动

### 直接运行 jar

```bash
java -jar target/KnowledgePlanet-0.0.1-SNAPSHOT.jar
```

服务端口：`8080`

### 推荐的生产启动参数

```bash
java \
  -Xms2g -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/var/log/knowledgeplanet/heapdump.hprof \
  -Dfile.encoding=UTF-8 \
  -jar KnowledgePlanet-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod
```

虚拟线程已默认启用，不需要 `-Djdk.virtualThreadScheduler.parallelism` 等额外参数。

### systemd 服务（Linux）

`/etc/systemd/system/knowledgeplanet.service`：

```ini
[Unit]
Description=KnowledgePlanet Backend
After=network.target mysql.service redis.service rabbitmq-server.service

[Service]
Type=simple
User=app
WorkingDirectory=/opt/knowledgeplanet
EnvironmentFile=/opt/knowledgeplanet/env
ExecStart=/usr/bin/java -Xms2g -Xmx2g -jar app.jar --spring.profiles.active=prod
Restart=always
RestartSec=10
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl enable --now knowledgeplanet
sudo journalctl -u knowledgeplanet -f
```

---

## 五、配置（生产）

**不要把账号密码写进 yaml 提交到 git**。用环境变量覆盖，Spring Boot 会自动读取。

`/opt/knowledgeplanet/env`：

```bash
# 数据库
SPRING_DATASOURCE_URL=jdbc:mysql://db.internal:3306/knowledgeplanet?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
SPRING_DATASOURCE_USERNAME=app_user
SPRING_DATASOURCE_PASSWORD=********

# Redis
SPRING_DATA_REDIS_HOST=redis.internal
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_PASSWORD=********

# RabbitMQ
SPRING_RABBITMQ_HOST=mq.internal
SPRING_RABBITMQ_USERNAME=app
SPRING_RABBITMQ_PASSWORD=********

# 阿里云 OSS
OSS_ACCESS_KEY_ID=********
OSS_ACCESS_KEY_SECRET=********
OSS_ENDPOINT=oss-cn-hangzhou.aliyuncs.com
OSS_BUCKET_NAME=knowledgeplanet-prod
OSS_DOMAIN=https://cdn.example.com

# 邮件（注册验证码）
SPRING_MAIL_USERNAME=noreply@example.com
SPRING_MAIL_PASSWORD=********

# 生产环境开启限流
RATE_LIMIT_ENABLED=true
```

---

## 六、前端 Nginx 配置

```nginx
server {
    listen 80;
    server_name admin.example.com;

    # Admin 静态资源
    root /var/www/admin;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    # API 反代到后端
    location /api/ {
        proxy_pass http://127.0.0.1:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket（聊天 / 推送）
    location /ws/ {
        proxy_pass http://127.0.0.1:8080/ws/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 600s;
    }
}
```

---

## 七、健康检查

| 端点 | 用途 |
|---|---|
| `GET /actuator/health` | K8s liveness / readiness |
| `GET /actuator/metrics` | 指标列表 |
| `GET /actuator/prometheus` | Prometheus 抓取 |
| `GET /actuator/hikaricp` | 数据库连接池状态 |
| `GET /swagger-ui.html` | 接口文档（生产建议关闭）|

启动后冒烟：

```bash
curl http://localhost:8080/actuator/health
# 期望：{"status":"UP"}
```

---

## 八、日志

默认输出到控制台，systemd 下走 journald。如果需要文件落盘，启动参数加：

```bash
--logging.file.name=/var/log/knowledgeplanet/app.log
--logging.file.max-size=100MB
--logging.file.max-history=30
```

---

## 九、升级 / 回滚

```bash
# 升级
sudo systemctl stop knowledgeplanet
cp app.jar app.jar.bak                # 留好回滚版本
cp /path/to/new/app.jar /opt/knowledgeplanet/app.jar
sudo systemctl start knowledgeplanet
sudo journalctl -u knowledgeplanet -f # 观察 30 秒确认启动

# 回滚
sudo systemctl stop knowledgeplanet
mv app.jar.bak app.jar
sudo systemctl start knowledgeplanet
```

**数据库迁移**：本项目使用 `ddl-auto: none`，schema 变更需手动执行 [database_schema.sql](database_schema.sql) 或对应 migration。**不要依赖 JPA 自动建表**。

---

## 十、常见问题

### 启动报 `Failed to configure a DataSource`
MySQL 没起，或账号密码错。检查 `SPRING_DATASOURCE_*` 环境变量。

### `Cannot connect to RabbitMQ`
RabbitMQ 没起，或账号没权限访问 `/` 虚拟主机。`docker exec rabbitmq rabbitmqctl list_users` 检查。

### `OutOfMemoryError: Java heap space`
默认 `-Xmx` 是物理内存的 1/4，对一台 8G 机器只有 2G。压测前显式设置 `-Xms2g -Xmx2g`。

### 虚拟线程相关报错
本项目运行需要 **JDK 21+**，JDK 17 会启动失败。`java -version` 确认。

### 上传图片报 OSS 错误
没配 OSS 环境变量。开发环境可暂时把 `OssController` 的相关接口禁用。

---

## 部署架构参考（单机）

```
┌────────────────────────────────────────────────┐
│                  Nginx (80/443)                │
│   ┌──────────┐         ┌────────────────┐      │
│   │ /admin/  │────────▶│ /var/www/admin │      │
│   └──────────┘         └────────────────┘      │
│   ┌──────────┐         ┌────────────────┐      │
│   │ /api/    │────────▶│ localhost:8080 │      │
│   │ /ws/     │         │  SpringBoot    │      │
│   └──────────┘         └────────┬───────┘      │
└──────────────────────────────────┼─────────────┘
                                   │
        ┌──────────────┬───────────┼──────────────┐
        ▼              ▼           ▼              ▼
   ┌─────────┐   ┌──────────┐ ┌──────────┐ ┌───────────┐
   │  MySQL  │   │  Redis   │ │ RabbitMQ │ │ Zipkin    │
   │  :3306  │   │  :6379   │ │  :5672   │ │ :9411(可选)│
   └─────────┘   └──────────┘ └──────────┘ └───────────┘
```

如果上规模，**MySQL / Redis / RabbitMQ 应该拆到独立机器**，应用层做横向扩展。注意：

- Redis 里有点赞计数缓冲（[LikeBufferTrigger](src/main/java/com/example/demo/task/LikeBufferTrigger.java)），多实例无影响（用的是 Redis 原子操作）
- Spring 事件（如 [PrimaryCommentDeletedEvent](src/main/java/com/example/demo/task/PrimaryCommentDeletedEvent.java)）**只在当前 JVM 内传递**——级联清理消息不会丢，因为生产者和监听器在同一进程
- WebSocket 连接有粘性要求，多实例要在 Nginx 配 `ip_hash` 或上 Redis pub-sub 广播
