# 网易大神签名服务 - Unidbg Docker 镜像
# 
# 构建: docker build -t nep-sign .
# 运行: docker run -d -p 8080:8080 --name nep-sign nep-sign
#
# API:
#   POST /sign      - POST 请求签名 {"url": "...", "content": "..."}
#   POST /sign_get  - GET 请求签名  {"url": "..."}
#   GET  /health    - 健康检查

FROM maven:3.8-openjdk-11 AS builder

WORKDIR /build

# 先复制 pom.xml 下载依赖 (利用 Docker 缓存)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源码并编译
COPY src ./src
RUN mvn package -DskipTests -q

# 运行时镜像 (更小)
FROM openjdk:11-jre-slim

WORKDIR /app

# 复制编译好的文件和依赖
COPY --from=builder /build/target/classes /app/classes
COPY --from=builder /root/.m2/repository /root/.m2/repository
COPY --from=builder /build/pom.xml /app/

# 复制 so 文件
COPY src/main/resources/libnep.so /app/src/main/resources/

# 安装 Maven (用于运行)
RUN apt-get update && apt-get install -y maven curl && rm -rf /var/lib/apt/lists/*

# 复制源码 (exec:java 需要)
COPY src ./src
COPY pom.xml .

# 暴露端口
EXPOSE 8080


# 启动签名服务
CMD ["mvn", "exec:java", "-Dexec.mainClass=com.netease.NepSignServer", "-q"]
