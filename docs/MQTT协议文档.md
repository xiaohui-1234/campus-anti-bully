# MQTT 协议文档

## 设备发布

- `device/{product_type}/{device_id}/alarm/post`
- `device/{product_type}/{device_id}/alarm/confirm`
- `device/{product_type}/{device_id}/bind`
- `device/{product_type}/{device_id}/status/online`
- `device/{product_type}/{device_id}/config/wifi/set/reply`
- `device/{product_type}/{device_id}/config/wifi/list`

### `device/{product_type}/{device_id}/bind`

设备发布一次性临时绑定码，后端写入 Redis `device:bind_code:{device_id}`，TTL 使用
`campus.cache.bind_code_ttl_seconds`。同一设备多次发布时覆盖上一条绑定码。

```json
{
  "mqtt_msg_id": "1779177600123-001-dev001",
  "product_type": "anti_bullying",
  "device_id": "dev001",
  "bind_code": "123456"
}
```

## 设备订阅

- `device/{product_type}/{device_id}/alarm/upload`
- `device/{product_type}/{device_id}/config/wifi/set`

## 消息 ID

字段名固定为 `mqtt_msg_id`，推荐格式：

```text
13位毫秒时间戳-3位自增序号-device_id
```

## 后端分层

```text
Receiver → Router → DedupService → Queue → Handler → Publisher
```

Redis 使用 `mqtt:dedup:{device_id}:{mqtt_msg_id}` 做第一层去重，数据库唯一索引 `event(device_table_id, mqtt_msg_id)` 兜底。
