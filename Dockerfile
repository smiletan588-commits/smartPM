# ---------- 后端构建阶段 ----------
ARG DOCKER_REGISTRY=m.daocloud.io/docker.io
FROM ${DOCKER_REGISTRY}/library/maven:3.9.9-eclipse-temurin-17 AS backend-builder

WORKDIR /build
COPY pom.xml .
COPY src ./src

RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

# ---------- 后端运行阶段 ----------
FROM ${DOCKER_REGISTRY}/library/eclipse-temurin:17-jre-alpine

# curl 用于 Docker healthcheck
RUN apk add --no-cache curl font-noto-cjk

# 创建非 root 用户
RUN addgroup -S app && adduser -S app -G app
USER app

WORKDIR /app

# 复制 Docker 内部构建好的 jar，无需本机预先执行 Maven
COPY --from=backend-builder /build/target/app.jar app.jar

EXPOSE 8080

# JVM 优化参数：
#   -Xms256m  初始堆（避免启动后频繁扩容）
#   -Xmx512m  最大堆
#   -XX:+UseG1GC  G1 收集器（低延迟，适合 Web 应用）
#   -XX:MaxGCPauseMillis=200  目标最大停顿
#   -XX:+ExitOnOutOfMemoryError  容器内 OOM 立即退出，由编排工具重启
#   -XX:+UseContainerSupport  启用容器感知（JDK 17 默认开启，显式声明）
ENTRYPOINT ["java", \
    "-Xms256m", "-Xmx512m", \
    "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=200", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-jar", "app.jar"]

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1
