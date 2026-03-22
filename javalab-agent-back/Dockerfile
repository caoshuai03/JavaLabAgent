# 后端 Dockerfile：多阶段构建（Maven 打包 -> JRE 运行）

# ---------- 构建阶段 ----------
FROM maven:3.9.9-eclipse-temurin-17 AS build

# 说明：在容器中构建，避免要求宿主机安装 Maven
WORKDIR /app

# 先拷贝 pom.xml 以利用 Docker layer cache
COPY pom.xml ./

# 预下载依赖（加速后续构建）
RUN mvn -q -e -DskipTests dependency:go-offline

# 再拷贝源码
COPY src ./src

# 打包 jar（跳过测试，提升构建速度）
RUN mvn -q -DskipTests package

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre

# 安装 Node.js 和 npm（提供 npx），供 MCP stdio 模式的子进程使用
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl ca-certificates && \
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y --no-install-recommends nodejs && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# 说明：Spring Boot 默认会读取环境变量覆盖配置
WORKDIR /app

# 从构建阶段拷贝 jar
COPY --from=build /app/target/*.jar /app/app.jar

# 后端端口（与你 application.yml 保持一致）
EXPOSE 8989

# 运行 Spring Boot
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
