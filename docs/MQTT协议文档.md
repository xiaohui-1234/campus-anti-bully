# MQTT 协议文档

MQTT 用于 STM32/ESP8266 设备与后端之间的报警、上传指令、在线状态、绑定码和 WiFi 配置同步。当前后端使用 Eclipse Paho 客户端，QoS 由 `campus.mqtt.qos` 配置，默认按 QoS 1 使用。

## Topic 规范

统一格式：

```text
device/{product_type}/{device_id}/{action}
```

示例：

```text
device/anti_bullying/dev001/alarm/post
```

后端订阅使用通配：

```text
device/+/+/alarm/post
device/+/+/alarm/confirm
device/+/+/bind
device/+/+/status/online
device/+/+/config/wifi/set/reply
device/+/+/config/wifi/list
```

设备订阅：

```text
device/{product_type}/{device_id}/alarm/upload
device/{product_type}/{device_id}/config/wifi/set
```

`config/wifi/set` 当前为预留下发能力，后端已有 Publisher 方法，但尚未暴露后台 HTTP 操作入口。

## 公共字段

设备上行消息应包含：

| 字段 | 说明 |
| --- | --- |
| `mqtt_msg_id` | MQTT 消息唯一 ID，用于 Redis 和数据库去重 |
| `product_type` | 产品类型，需要与 topic 中一致 |
| `device_id` | 设备 ID，需要与 topic 中一致 |

后端会校验 payload 中的 `product_type`、`device_id` 是否与 topic 一致，不一致则忽略。

推荐 `mqtt_msg_id` 格式：

```text
13位毫秒时间戳-3位序号-device_id
```

STM32 当前优先使用网络时间生成，未校时时回退运行毫秒数。

## 上行：报警上报

Topic：

```text
device/{product_type}/{device_id}/alarm/post
```

Payload：

```json
{
  "mqtt_msg_id": "1779177600123-001-dev001",
  "product_type": "anti_bullying",
  "device_id": "dev001",
  "event_type": "VOICE",
  "alarm_info": "fight",
  "file_name": "alarm.wav",
  "file_size": 123456,
  "content_type": "audio/wav",
  "timestamp": 1779177600123
}
```

处理结果：

1. 后端校验设备存在。
2. 生成 `event_id`。
3. 按 MinIO 对象 Key 规则生成 `object_key`。
4. 创建事件，初始状态为 `file_status=UPLOADING`、`push_status=PENDING`、`read_status=UNREAD`。
5. 生成预签名上传 URL。
6. 下发 `alarm/upload`。
7. 尝试通过 WebSocket 推送 `NEW_EVENT`。

## 下行：上传指令

Topic：

```text
device/{product_type}/{device_id}/alarm/upload
```

Payload：

```json
{
  "mqtt_msg_id": "1779177600123-001-dev001",
  "event_id": "evt_dev001_1779177600123",
  "object_key": "anti_bullying/audio/dev001/2026/06/08/evt_dev001_1779177600123.wav",
  "upload_url": "http://minio.example/campus/...",
  "expire_seconds": 300
}
```

设备收到后应：

1. 通过 `mqtt_msg_id + event_id` 去重。
2. 使用 `upload_url` 对本地 WAV 文件执行 HTTP PUT。
3. 上传完成后发布 `alarm/confirm`。

## 上行：上传确认

Topic：

```text
device/{product_type}/{device_id}/alarm/confirm
```

Payload：

```json
{
  "mqtt_msg_id": "1779177600456-002-dev001",
  "product_type": "anti_bullying",
  "device_id": "dev001",
  "event_id": "evt_dev001_1779177600123",
  "object_key": "anti_bullying/audio/dev001/2026/06/08/evt_dev001_1779177600123.wav",
  "upload_status": "success"
}
```

`upload_status=success` 时事件文件状态更新为 `SUCCESS`，其他值更新为 `FAILED`。后端会校验 `event_id` 属于该设备，且 `object_key` 与事件记录一致。

## 上行：绑定码

Topic：

```text
device/{product_type}/{device_id}/bind
```

Payload：

```json
{
  "mqtt_msg_id": "1779177600000-001-dev001",
  "product_type": "anti_bullying",
  "device_id": "dev001",
  "bind_code": "123456"
}
```

后端写入 Redis：

```text
device:bind_code:{device_id}
```

TTL 使用 `campus.cache.bind-code-ttl-seconds`。小程序绑定设备时必须提交同一个绑定码。

## 上行：在线状态

Topic：

```text
device/{product_type}/{device_id}/status/online
```

Payload：

```json
{
  "mqtt_msg_id": "1779177600000-001-dev001",
  "product_type": "anti_bullying",
  "device_id": "dev001",
  "status": "online",
  "timestamp": 1779177600000
}
```

`status=online` 时后端写入：

- `device:online:{device_id}`
- `device:last_heartbeat:{device_id}`

并更新数据库 `device.last_online_time`，通过 WebSocket 推送 `DEVICE_STATUS`。非 `online` 状态会删除在线 Key 并推送 `OFFLINE`。

## 上行：WiFi 列表

Topic：

```text
device/{product_type}/{device_id}/config/wifi/list
```

Payload：

```json
{
  "mqtt_msg_id": "1779177600000-001-dev001",
  "max_size": 5,
  "id_using": 1,
  "config": [
    {
      "config_id": 1,
      "wifi_name": "Campus-WiFi"
    }
  ]
}
```

后端只保存 `wifi_name`、`config_id` 和更新时间，不保存 `wifi_password`。

## 上行：WiFi 设置回复

Topic：

```text
device/{product_type}/{device_id}/config/wifi/set/reply
```

Payload：

```json
{
  "mqtt_msg_id": "1779177600000-001-dev001",
  "config_id": 1,
  "success": true
}
```

当前实现只记录日志。

## 去重与队列

处理链路：

```text
MqttReceiver -> MqttRouter -> MqttDedupService -> MqttMessageQueue -> MqttMessageHandler -> MqttPublisher
```

Redis 去重 Key：

```text
mqtt:dedup:{device_id}:{mqtt_msg_id}
```

数据库兜底唯一索引：

```text
event(device_table_id, mqtt_msg_id)
```

如果 Redis 不可用，后端会放行到数据库唯一索引兜底。
