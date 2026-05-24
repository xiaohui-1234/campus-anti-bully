# 后端检查

```bash
cd campus-server
mvn clean package
```

检查重点：

- JSON 是否 snake_case。
- WebSocket 是否为 `/ws/v1/client`。
- Redis Key、MQTT Topic、MinIO Key 是否集中封装。
- 设备和事件接口是否校验绑定权限。
- 日志是否避免输出敏感明文。
