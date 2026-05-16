# syntax=docker/dockerfile:1.7
# Docker/Podman 通用构建文件

FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

# Copy only build descriptors first to maximize layer cache hit rate.
COPY pom.xml ./

# Docker BuildKit: --mount=type=cache 加速构建
# Podman: 不支持 cache mount，自动忽略 syntax 指令
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests dependency:go-offline

# Copy source code and build jar.
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /workspace/target/*.jar /app/app.jar

ENV JAVA_OPTS=""
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
