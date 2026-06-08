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
