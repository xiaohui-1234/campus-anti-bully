# WebSocket 设计

WebSocket 用于向小程序实时推送新事件和设备在线状态。当前实现只注册一个连接地址：

```text
/ws/v1/client
```

客户端只能建立这一条连接，再通过消息订阅多个设备。不要改成“一设备一连接”。

## 鉴权

握手支持两种方式：

```text
Authorization: Bearer <access_token>
```

或：

```text
/ws/v1/client?token=<access_token>
```

后端在 `JwtHandshakeInterceptor` 中解析 access token，并把登录用户放入 session 属性。

## 客户端消息

### 订阅事件

```json
{
  "type": "SUBSCRIBE_EVENTS",
  "device_ids": ["dev001", "dev002"]
}
```

后端会逐个校验当前用户是否绑定设备。存在无权限设备时返回失败 ACK。

成功 ACK：

```json
{
  "type": "SUBSCRIBE_EVENTS_ACK",
  "success": true,
  "data": {
    "device_ids": ["dev001", "dev002"]
  }
}
```

失败 ACK：

```json
{
  "type": "SUBSCRIBE_EVENTS_ACK",
  "success": false,
  "message": "存在无权限订阅的设备"
}
```

### 取消订阅

```json
{
  "type": "UNSUBSCRIBE_EVENTS",
  "device_ids": ["dev001"]
}
```

ACK：

```json
{
  "type": "UNSUBSCRIBE_EVENTS_ACK",
  "success": true,
  "data": {
    "device_ids": ["dev001"]
  }
}
```

### 心跳

```json
{
  "type": "PING",
  "timestamp": 1779177600000
}
```

响应：

```json
{
  "type": "PONG",
  "timestamp": 1779177600000
}
```

## 服务端推送

### 新事件

```json
{
  "type": "NEW_EVENT",
  "data": {
    "event_id": "evt_dev001_1779177600123",
    "device_id": "dev001",
    "event_type": "VOICE",
    "alarm_info": "fight",
    "file_status": "UPLOADING",
    "file_url": "",
    "push_status": "PENDING",
    "read_status": "UNREAD",
    "event_time": "2026-06-08 12:00:00"
  }
}
```

### 设备状态

```json
{
  "type": "DEVICE_STATUS",
  "data": {
    "device_id": "dev001",
    "online_status": "ONLINE",
    "last_online_time": "2026-06-08 12:00:00"
  }
}
```

## 小程序行为

- `services/websocket.js` 使用 `env.wsUrl?token=...` 建连。
- 建连成功后发送 `SUBSCRIBE_EVENTS`，订阅当前用户已绑定设备。
- 断线后指数退避重连，重连前尝试刷新 access token。
- 重连后通过 `/api/v1/events/unpulled` 补拉离线期间未收到的事件。
- 首页和事件页都复用同一条 WebSocket 服务。

## Session 管理

后端维护：

- `session -> user`
- `user -> sessions`
- `session -> devices`
- `device -> sessions`

推送时按 `device_id` 找到所有订阅 session 并发送消息。
