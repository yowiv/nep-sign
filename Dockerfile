# 网易大神签名服务 - Unidbg Docker 镜像
# 
# 构建: docker build -t nep-sign .
# 运行: docker run -d -p 8080:8080 --name nep-sign nep-sign
#
# API:
#   POST /sign      - POST 请求签名 {"url": "...", "content": "..."}
#   POST /sign_get  - GET 请求签名  {"url": "..."}
#   GET  /health    - 健康检查

FROM maven:3.8-openjdk-11

WORKDIR /app

# 安装 curl 用于健康检查
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# 先复制 pom.xml 下载依赖 (利用 Docker 缓存)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源码和 so 文件
COPY src ./src

# 编译项目
RUN mvn compile -q

# 暴露端口
EXPOSE 8080


# 启动签名服务
CMD ["mvn", "exec:java", "-Dexec.mainClass=com.netease.NepSignServer", "-q"]
