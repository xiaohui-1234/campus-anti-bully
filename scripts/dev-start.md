# 本地开发启动

## 1. 启动基础设施

准备 MySQL、Redis、MinIO。需要硬件/MQTT 联调时再启动 EMQX。

## 2. 初始化数据库

```bash
mysql -uroot -p < sql/campus.sql
```

## 3. 启动后端

```bash
cd campus-server
mvn spring-boot:run
```

如本机没有 EMQX，可先将 `campus.mqtt.enabled=false`，只联调 HTTP、WebSocket 和前端页面。

后端启动后会以 `campus.mqtt.client-id` 连接 EMQX，默认客户端 ID 为 `campus-server`。EMQX 客户端列表中看到 `campus-server` 在线，表示后端 MQTT 接收器正在运行。

本地不要同时启动两个启用 MQTT 的后端实例。如果 IDEA、命令行或 `java -jar` 残留进程同时使用 `campus-server` 作为 MQTT `client_id`，EMQX 会让新连接顶掉旧连接，日志会每隔几秒出现：

```text
MQTT connection lost
MqttException: 已断开连接
Caused by: java.io.EOFException
```

Windows 下可用下面命令检查是否有残留后端进程：

```powershell
Get-CimInstance Win32_Process |
  Where-Object { $_.Name -match 'java|javaw' -and $_.CommandLine -match 'campus-server' } |
  Select-Object ProcessId,CommandLine
```

确认是多余的验证或调试进程后再停止；不要误杀 IDEA 编译服务或正在使用的后端进程。

## 4. 启动后台

```bash
cd campus-admin-web
npm install
npm run dev
```

后台默认访问本地 Vite 地址，登录页使用开发管理员 openid 获取 ADMIN token。

## 5. 启动小程序

使用微信开发者工具打开 `campus-miniprogram`。根据本地或测试环境修改：

```js
baseUrl: 'http://127.0.0.1:8080/api/v1'
wsUrl: 'ws://127.0.0.1:8080/ws/v1/client'
```

## 6. 硬件联调

修改 `stm32主控源码/联网测试win/Hardware/campus_config.h` 后重新烧录。确认设备可以访问 EMQX 和 MinIO 预签名上传地址。
