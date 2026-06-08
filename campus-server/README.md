# campus-server

Spring Boot 3 后端服务，当前实现包含认证、用户、设备、事件、MinIO 存储、MQTT 接入、WebSocket 推送和 ADMIN 配置接口。

## 技术栈

- Java 17
- Spring Boot 3.3.5
- Spring Security + JWT
- MyBatis-Plus + MySQL 8
- Redis
- MinIO Java SDK
- Eclipse Paho MQTT
- Spring WebSocket
- springdoc-openapi

## 构建与运行

```bash
mvn clean package
mvn spring-boot:run
```

默认端口为 `8080`，Swagger UI 默认开启：

```text
/swagger-ui/index.html
/v3/api-docs
```

## 配置项

主要配置位于 `src/main/resources/application.yml`，生产环境建议用外部配置或环境变量覆盖：

- `spring.datasource.*`
- `spring.data.redis.*`
- `campus.security.jwt.secret`
- `campus.security.jwt.access-token-expire-seconds`
- `campus.security.jwt.refresh-token-expire-seconds`
- `campus.wx.miniapp.app-id`
- `campus.wx.miniapp.app-secret`
- `campus.mqtt.*`
- `campus.minio.*`
- `campus.cache.*`
- `campus.storage.object-key-pattern`
- `campus.storage.avatar-key-pattern`

文档不要写真实密码、JWT secret、MinIO secretKey 或微信 appSecret。

## API 前缀

- 小程序端接口：`/api/v1`
- 后台配置接口：`/backend/v1`
- WebSocket：`/ws/v1/client`

## 认证与权限

- 小程序登录：`POST /api/v1/auth/wx/login`
- 开发管理员登录：`POST /api/v1/auth/openid/admin-login`
- 刷新 access token：`POST /api/v1/auth/refresh`
- 受保护 HTTP 接口使用 `Authorization: Bearer <access_token>`。
- `/backend/v1/**` 要求 `ADMIN` 角色。
- WebSocket 握手支持 Header token 或 query token。

## 业务约束

- JSON 统一由 Jackson 配置为 `snake_case`。
- `openid` 只用于登录换取用户身份，数据库只保存 `openid_hash`。
- 设备绑定、设备查询、事件查询、事件删除、标记已读和刷新文件 URL 均校验当前用户是否绑定设备。
- MQTT 入口按 `Receiver -> Router -> DedupService -> Queue -> Handler -> Publisher` 处理。
- MQTT 去重使用 Redis `mqtt:dedup:{device_id}:{mqtt_msg_id}`，数据库再用 `event(device_table_id, mqtt_msg_id)` 兜底。
- WebSocket 单连接订阅多个设备，订阅时校验设备绑定关系。
