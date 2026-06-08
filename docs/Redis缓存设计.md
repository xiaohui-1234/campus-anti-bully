# Redis 缓存设计

Redis 用于 MQTT 去重、设备在线、临时绑定码、JWT 黑名单、未拉取事件、MinIO 访问 URL 缓存和 WebSocket 辅助状态。

## Key 规范

| Key | 作用 | TTL 配置 |
| --- | --- | --- |
| `mqtt:dedup:{device_id}:{mqtt_msg_id}` | MQTT 消息去重 | `campus.cache.mqtt-dedup-ttl-seconds` |
| `device:online:{device_id}` | 设备在线状态 | `campus.cache.device-online-ttl-seconds` |
| `device:last_heartbeat:{device_id}` | 最近心跳时间戳 | `campus.cache.device-online-ttl-seconds` |
| `device:bind_code:{device_id}` | 临时绑定码 | `campus.cache.bind-code-ttl-seconds` |
| `jwt:blacklist:{token_id}` | 已退出或失效 access token | `campus.cache.jwt-blacklist-ttl-seconds` |
| `event:unpulled:user:{user_id}` | 用户离线未拉取事件 | `campus.cache.unpulled-event-cache-ttl-seconds` |
| `minio:access_url:{event_id}` | 文件访问预签名 URL 缓存 | `campus.cache.access-url-ttl-seconds` |
| `ws:user:{user_id}` | WebSocket 用户辅助状态预留 | 当前代码未持久写入 |
| `ws:device:{device_id}` | WebSocket 设备辅助状态预留 | 当前代码未持久写入 |

TTL 集中放在 `application.yml` 的 `campus.cache` 下，业务代码不要散落硬编码。

## MQTT 去重

`MqttDedupService.firstSeen(deviceId, mqttMsgId)` 使用 `SETNX` 写入：

```text
mqtt:dedup:{device_id}:{mqtt_msg_id}
```

值为 `DONE`。写入成功表示首次处理；写入失败表示重复消息。Redis 不可用时，后端会继续处理并依赖数据库唯一索引兜底。

## 设备在线

设备通过 MQTT `status/online` 上报 `status=online` 后：

- 写入 `device:online:{device_id}`，值为 `online`。
- 写入 `device:last_heartbeat:{device_id}`，值为上报时间戳。
- 更新数据库 `device.last_online_time`。
- 推送 WebSocket `DEVICE_STATUS`。

如果收到非 online 状态，则删除 `device:online:{device_id}` 并推送离线。

## 绑定码

设备上报 `bind` 后，后端写入：

```text
device:bind_code:{device_id}
```

小程序绑定时提交 `device_id + bind_code`，后端校验通过后创建 `user_device_bind` 记录。

## JWT 黑名单

用户退出登录后，后端解析 access token 的 `jti`，写入：

```text
jwt:blacklist:{token_id}
```

后续请求若命中黑名单则返回 401。

## 未拉取事件

当后端 WebSocket 推送新事件失败或需要补拉时，可使用：

```text
event:unpulled:user:{user_id}
```

小程序重连后会调用 `/api/v1/events/unpulled` 补拉。

## MinIO 访问 URL 缓存

刷新访问 URL 时可缓存：

```text
minio:access_url:{event_id}
```

数据库长期只保存 `file_key`，不保存完整预签名访问 URL。
