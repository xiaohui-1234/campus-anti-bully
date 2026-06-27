# MinIO 文件存储设计

MinIO 用于存储报警音频和用户头像。数据库长期保存对象 Key，不长期保存预签名访问 URL。

## 配置项

```yaml
campus:
  minio:
    endpoint: http://minio:9000
    internal-endpoint: http://minio:9000
    access-key: campus
    secret-key: change-me
    bucket: campus
    public-endpoint: https://oss.example.com
    upload-endpoint: http://minio.example.com:9000
  storage:
    object-key-pattern: "{product_type}/audio/{device_id}/{yyyy}/{MM}/{dd}/{event_id}.{ext}"
    avatar-key-pattern: "avatar/{user_id}.{ext}"
    avatar-max-size-mb: 2
```

文档和日志不得输出真实 `secret-key`。

`endpoint` 为历史兼容配置。新部署建议使用三类 endpoint：`internal-endpoint` 用于后端内部连接 MinIO；`public-endpoint` 用于小程序、后台访问头像和报警音频，生产环境应使用 HTTPS 域名；`upload-endpoint` 用于生成下发给设备的 `upload_url`，若 ESP8266 仅稳定支持 HTTP，应保持 HTTP 可达地址。

## 报警音频对象 Key

默认格式：

```text
{product_type}/audio/{device_id}/{yyyy}/{MM}/{dd}/{event_id}.{ext}
```

示例：

```text
anti_bullying/audio/dev001/2026/06/08/evt_dev001_1779177600123.wav
```

扩展名从设备上报的 `file_name` 推导；当前 STM32 默认上报 `alarm.wav`，`content_type` 为 `audio/wav`。

## 报警上传流程

1. 设备发布 MQTT `alarm/post`。
2. 后端创建事件，`file_status=UPLOADING`。
3. 后端基于 `upload-endpoint` 生成 MinIO 预签名上传 URL。
4. 后端通过 MQTT `alarm/upload` 下发 `event_id`、`object_key`、`upload_url`、`expire_seconds`。
5. STM32 使用 ESP8266 对 `upload_url` 执行 HTTP PUT，上传本地 WAV。
6. STM32 发布 MQTT `alarm/confirm`。
7. 后端根据 `upload_status` 标记 `SUCCESS` 或 `FAILED`，并通过 WebSocket 推送事件。

## 访问 URL

小程序播放音频前调用：

```text
POST /api/v1/events/{eventId}/refresh-url
```

请求：

```json
{
  "expire_seconds": 3600
}
```

后端基于 `public-endpoint` 和 `file_key` 生成临时访问 URL，返回 `file_url`、`expire_seconds`、`expire_at`。

`expire_seconds` 范围为 `60` 到 `86400`。

## 头像

头像上传接口：

```text
POST /api/v1/users/me/avatar
```

使用 multipart 表单字段 `file`。对象 Key 按：

```text
avatar/{user_id}.{ext}
```

头像大小由 `campus.storage.avatar-max-size-mb` 限制，当前默认 `2MB`。

头像对象由后端通过 `internal-endpoint` 写入 MinIO，返回的 `avatar_url` 使用 `public-endpoint` 拼接。
