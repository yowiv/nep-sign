# 网易大神 libnep.so 签名调用

使用 Unidbg 在 PC/Docker 上模拟调用 libnep.so 实现签名算法。

## 方式一：Docker 部署（推荐）

无需本地安装 Java/Maven，直接使用容器：

```bash
# 构建并启动
docker-compose up -d --build

# 查看日志
docker-compose logs -f

# 停止服务
docker-compose down
```

### API 接口

```bash
# 健康检查
curl http://localhost:8080/health

# POST 签名
curl -X POST http://localhost:8080/sign \
  -H "Content-Type: application/json" \
  -d '{"url":"https://god.gameyw.netease.com/v1/xxx","content":"{}"}'

# GET 签名  
curl -X POST http://localhost:8080/sign_get \
  -H "Content-Type: application/json" \
  -d '{"url":"https://god.gameyw.netease.com/v1/xxx"}'
```

### Python 客户端

```python
from sign_client import NepSignClient

client = NepSignClient("http://localhost:8080")
signed_url = client.sign_post(url, content)
```

---

## 方式二：本地运行

### 1. 提取 libnep.so

从手机提取：
```bash
# 方法1: ADB 提取 (需要 root)
adb shell su -c "cat /data/app/com.netease.gl-*/lib/arm64/libnep.so" > libnep.so

# 方法2: 从 APK 解压
# 使用 7-Zip/WinRAR 打开 APK，提取 lib/arm64-v8a/libnep.so

# 方法3: Frida 内存 dump (推荐，获取解密后的 so)
# 使用 dump_libnep_v5.js 脚本
```

### 2. 放置 so 文件

将 `libnep.so` 放到：
```
unidbg-nep/src/main/resources/libnep.so
```

### 3. 安装依赖

需要 JDK 8+ 和 Maven：
```bash
cd unidbg-nep
mvn clean compile
```

## 运行测试

```bash
mvn exec:java -Dexec.mainClass="com.netease.NepSign"
```

## 可能的问题

### 1. 缺少依赖库

libnep.so 可能依赖其他 so 库，需要一起提取：
- `libc++_shared.so`
- 其他网易 SDK 的 so

### 2. JNI 回调

so 库可能调用 Java 层的方法获取设备信息等，需要在 `NepSign.java` 中实现对应的 JNI 回调。

### 3. 签名校验

部分 so 库有签名校验，可能需要：
- 绕过签名检测
- 使用原版 APK 签名

## 文件结构

```
unidbg-nep/
├── pom.xml
├── README.md
└── src/
    └── main/
        ├── java/
        │   └── com/
        │       └── netease/
        │           └── NepSign.java
        └── resources/
            └── libnep.so  # 需要手动放置
```

## 替代方案：Frida RPC

如果 Unidbg 调用遇到困难，可以使用 Frida RPC：

```javascript
// frida_rpc_server.js
rpc.exports = {
    sign: function(url, content) {
        var result = null;
        Java.perform(function() {
            var Tools = Java.use("com.netease.nep.Tools");
            result = Tools.getPostMethodSignatures(url, content);
        });
        return result;
    }
};
```

Python 调用：
```python
import frida

device = frida.get_usb_device()
session = device.attach("com.netease.gl")
script = session.create_script(open("frida_rpc_server.js").read())
script.load()

# 调用签名
result = script.exports.sign(url, content)
```
