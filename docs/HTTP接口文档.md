# HTTP 接口文档

后端默认端口 `8080`。小程序端接口前缀为 `/api/v1`，后台端接口前缀为 `/backend/v1`。返回 JSON 统一使用 `snake_case`。

## 通用返回

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

失败时 `code` 非 0，`message` 为错误说明。受保护接口需带：

```text
Authorization: Bearer <access_token>
```

## Auth

### 微信登录

```text
POST /api/v1/auth/wx/login
```

请求：

```json
{
  "code": "wx.login 返回的 code"
}
```

响应 `data`：

```json
{
  "access_token": "...",
  "refresh_token": "...",
  "token_type": "Bearer",
  "expires_in": 7200,
  "user_info": {
    "user_id": "usr_xxx",
    "nickname": "张三",
    "avatar_url": "",
    "phone": "",
    "email": "",
    "role": "USER",
    "created_at": "2026-06-08 12:00:00",
    "is_new_user": true
  }
}
```

### 开发管理员 openid 登录

```text
POST /api/v1/auth/openid/admin-login
```

请求：

```json
{
  "openid": "开发管理员 openid"
}
```

仅用于开发或受控后台入口。生产环境不要在文档、日志或响应中暴露真实 openid。

### 刷新 token

```text
POST /api/v1/auth/refresh
```

请求：

```json
{
  "refresh_token": "..."
}
```

响应：

```json
{
  "access_token": "...",
  "token_type": "Bearer",
  "expires_in": 7200
}
```

### 退出登录

```text
POST /api/v1/auth/wx/logout
```

后端将 access token 加入 Redis 黑名单。

## Users

### 当前用户

```text
GET /api/v1/users/me
```

响应字段同 `user_info`。

### 更新当前用户

```text
PUT /api/v1/users/me
```

请求：

```json
{
  "nickname": "张三",
  "phone": "13800000000",
  "email": "user@example.com"
}
```

### 上传头像

```text
POST /api/v1/users/me/avatar
Content-Type: multipart/form-data
```

表单字段：

```text
file=<图片文件>
```

响应：

```json
{
  "avatar_url": "https://..."
}
```

## Devices

设备接口均只返回当前用户已绑定设备。

### 我的设备

```text
GET /api/v1/devices?page=1&size=10
```

### 搜索设备

```text
GET /api/v1/devices/search?keyword=dev&onlineStatus=ONLINE&page=1&size=10
```

`onlineStatus` 支持 `ONLINE`、`OFFLINE`。

分页响应：

```json
{
  "records": [
    {
      "device_id": "dev001",
      "product_type": "anti_bullying",
      "device_name": "一号设备",
      "location": "教学楼",
      "note": "",
      "online_status": "ONLINE",
      "last_online_time": "2026-06-08 12:00:00",
      "bind_time": "2026-06-08 10:00:00"
    }
  ],
  "total": 1,
  "page": 1,
  "size": 10
}
```

### 绑定设备

```text
POST /api/v1/devices/bind
```

请求：

```json
{
  "device_id": "dev001",
  "bind_code": "123456",
  "device_name": "一号设备"
}
```

绑定码由设备通过 MQTT `bind` 上报，后端存入 Redis，TTL 由 `campus.cache.bind-code-ttl-seconds` 控制。

### 更新设备备注

```text
PUT /api/v1/devices/{deviceId}/info
```

请求：

```json
{
  "device_name": "一号设备",
  "location": "教学楼三层",
  "note": "靠近楼梯"
}
```

### 解绑设备

```text
DELETE /api/v1/devices/{deviceId}/binding
```

## Events

事件接口均校验当前用户是否绑定事件所属设备。

### 未拉取事件

```text
GET /api/v1/events/unpulled
```

用于小程序离线后补拉 Redis 中缓存的待推送事件。

### 搜索事件

```text
GET /api/v1/events/search
```

查询参数：

| 参数 | 说明 |
| --- | --- |
| `device_id` | 设备 ID |
| `page` | 页码，默认 1 |
| `size` | 每页数量，默认 10，最大值由配置限制 |
| `event_type` | 事件类型，如 `VOICE`、`BUTTON` |
| `file_status` | `UPLOADING`、`SUCCESS`、`FAILED` |
| `push_status` | `PENDING`、`PUSHED`、`FAILED` |
| `read_status` | `UNREAD`、`READ` |
| `start_time` | `yyyy-MM-dd HH:mm:ss` |
| `end_time` | `yyyy-MM-dd HH:mm:ss` |
| `keyword` | 在报警信息中模糊匹配 |

事件响应字段：

```json
{
  "event_id": "evt_dev001_...",
  "device_id": "dev001",
  "event_type": "VOICE",
  "alarm_info": "fight",
  "file_status": "SUCCESS",
  "file_url": "https://...",
  "push_status": "PUSHED",
  "read_status": "UNREAD",
  "event_time": "2026-06-08 12:00:00"
}
```

### 标记已读

```text
PUT /api/v1/events/{eventId}/read
```

### 删除事件

```text
DELETE /api/v1/events/{event_id}
```

### 刷新音频访问 URL

```text
POST /api/v1/events/{eventId}/refresh-url
```

请求：

```json
{
  "expire_seconds": 3600
}
```

`expire_seconds` 范围为 `60` 到 `86400`。

响应：

```json
{
  "event_id": "evt_dev001_...",
  "file_url": "https://...",
  "expire_seconds": 3600,
  "expire_at": "2026-06-08 13:00:00"
}
```

## Admin

后台接口要求 `ADMIN` 角色。

### 获取配置

```text
GET /backend/v1/config
```

返回缓存、存储、MQTT、MinIO 当前配置。敏感值应脱敏展示。

### 更新配置

```text
PUT /backend/v1/config
```

当前支持更新：

```json
{
  "cache": {
    "mqtt_dedup_ttl_seconds": 86400,
    "device_online_ttl_seconds": 90,
    "bind_code_ttl_seconds": 60,
    "access_url_ttl_seconds": 3600
  },
  "storage": {
    "object_key_pattern": "{product_type}/audio/{device_id}/{yyyy}/{MM}/{dd}/{event_id}.{ext}",
    "upload_url_expire_seconds": 300,
    "access_url_expire_seconds": 3600
  }
}
```

### 系统信息

```text
GET /backend/v1/system/info
```

返回应用名、Java 版本、操作系统、用户数、设备数、事件数。
