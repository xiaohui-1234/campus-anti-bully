# 后端检查

推荐检查命令：

```bash
cd campus-server
mvn clean package
```

检查重点：

- JSON 是否保持 `snake_case`。
- WebSocket 是否只有 `/ws/v1/client` 一个客户端入口。
- MQTT topic 是否与 `docs/MQTT协议文档.md` 一致。
- Redis Key、MinIO Key、MQTT topic 是否集中封装。
- 设备和事件接口是否校验当前用户绑定关系。
- Controller 是否保持薄层，复杂逻辑是否在 Service/Handler。
- 响应、日志、文档是否避免输出 openid、device_secret、wifi_password、JWT、MinIO secretKey。
- MQTT 去重是否同时有 Redis 和数据库唯一索引兜底。
