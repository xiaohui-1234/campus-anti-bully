# WebSocket 设计

连接地址固定：

```text
/ws/v1/client
```

认证方式：

- Header：`Authorization: Bearer access_token`
- 或 query：`/ws/v1/client?token=access_token`

客户端建连后发送订阅：

```json
{
  "type": "SUBSCRIBE_EVENTS",
  "device_ids": ["dev001", "dev002"]
}
```

服务端推送：

- `NEW_EVENT`
- `DEVICE_STATUS`
- `PONG`

设计原则：一个客户端用户只维护一个 WebSocket 连接，连接后订阅多个设备。禁止回退到一个设备一个连接。
