# Redis 缓存设计

Key 规范：

- `mqtt:dedup:{device_id}:{mqtt_msg_id}`
- `device:online:{device_id}`
- `device:last_heartbeat:{device_id}`
- `device:bind_code:{device_id}`
- `jwt:blacklist:{token_id}`
- `event:unpulled:user:{user_id}`
- `minio:access_url:{event_id}`
- `ws:user:{user_id}`
- `ws:device:{device_id}`

TTL 统一在 `application.yml` 的 `campus.cache` 下配置，代码不在业务处散落硬编码 TTL。
