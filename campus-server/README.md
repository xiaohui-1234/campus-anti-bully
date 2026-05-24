# campus-server

Spring Boot 3 后端，提供微信登录、用户、设备、事件、MinIO、MQTT、WebSocket 和后台配置接口。

## 构建

```bash
mvn clean package
```

## 运行前配置

编辑 `src/main/resources/application.yml`：

- `spring.datasource.*`
- `spring.data.redis.*`
- `campus.security.jwt.secret`
- `campus.wx.miniapp.*`
- `campus.minio.*`
- `campus.mqtt.*`

默认 `campus.mqtt.enabled=false`，避免本地没有 EMQX 时阻塞应用启动；接入 EMQX 后改为 `true`。

## API 前缀

- 小程序接口：`/api/v1`
- 后台配置接口：`/backend/v1`
- WebSocket：`/ws/v1/client?token=access_token`
