# HTTP 接口文档

后端默认端口 `8080`。普通用户端接口前缀为 `/api/v1`，配置后台接口前缀为 `/backend/v1`。返回 JSON 统一使用 `snake_case`。

当前账号体系同时支持：

- 微信小程序通过 `wx.login` 换取 openid 后登录。
- Android/Web/小程序跨端账号通过安全邮箱或 `user_id` 加密码登录。
- 已有微信小程序账号可在登录后设置安全邮箱和密码，之后可迁移到 Android/Web 登录。

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
  "expires_in": 1800,
  "user_info": {
    "user_id": "usr_xxx",
    "nickname": "张三",
    "avatar_url": "",
    "phone": "",
    "email": "",
    "security_email_masked": "u***@example.com",
    "security_email_verified": true,
    "password_enabled": true,
    "role": "USER",
    "created_at": "2026-06-08 12:00:00",
    "is_new_user": true
  }
}
```

后端只保存 `openid_hash`，不保存或返回明文 openid。

### 兼容开发管理员 openid 登录

```text
POST /api/v1/auth/openid/admin-login
```

请求：

```json
{
  "openid": "开发管理员 openid"
}
```

该接口仅作为兼容开发入口保留，当前配置后台默认不再使用 openid 登录。生产环境不要在文档、日志或响应中暴露真实 openid。

### 发送邮箱验证码

```text
POST /api/v1/auth/email-code/send
```

请求：

```json
{
  "scene": "PASSWORD_REGISTER",
  "security_email": "user@example.com",
  "change_ticket": ""
}
```

`scene` 支持：

| 场景 | 是否登录 | 是否需要 `security_email` | 用途 |
| --- | --- | --- | --- |
| `PASSWORD_REGISTER` | 否 | 是 | 邮箱密码注册 |
| `ACTIVATE_ACCOUNT` | 是 | 是 | 小程序旧账号首次设置安全邮箱和密码 |
| `RESET_PASSWORD` | 否 | 是 | 通过安全邮箱重置密码 |
| `CHANGE_SECURITY_EMAIL_OLD` | 是 | 否 | 修改安全邮箱前验证当前安全邮箱 |
| `CHANGE_SECURITY_EMAIL_NEW` | 是 | 是 | 修改安全邮箱时验证新邮箱 |

说明：

- `CHANGE_SECURITY_EMAIL_OLD` 不接收邮箱，后端会读取当前登录用户已绑定的安全邮箱。
- `CHANGE_SECURITY_EMAIL_NEW` 必须携带旧邮箱验证后返回的 `change_ticket`。
- 验证码发送有邮箱冷却、IP 发送次数限制和验证码错误次数限制。
- `RESET_PASSWORD` 为防止枚举账号，即使邮箱不存在或账号状态不允许重置，也不暴露具体原因。

### 邮箱密码注册

```text
POST /api/v1/auth/password/register
```

请求：

```json
{
  "security_email": "user@example.com",
  "verify_code": "123456",
  "password": "StrongPassword123"
}
```

响应同微信登录，注册后直接签发 token。密码长度和复杂度由 `campus.security.password.*` 配置控制，当前要求 8 到 64 位且同时包含字母和数字。

### 邮箱或用户 ID 加密码登录

```text
POST /api/v1/auth/password/login
```

请求：

```json
{
  "login_id": "user@example.com",
  "password": "StrongPassword123"
}
```

`login_id` 可以是安全邮箱，也可以是 `user_id`。只有同时满足以下条件才允许密码登录：

- `security_email_verified = true`
- `password_hash` 不为空

配置后台也使用该接口登录，但前端只保存 `user_info.role = ADMIN` 的登录态；普通用户即使能完成密码登录，也不能进入 `/backend/v1/**` 管理接口。

### 小程序账号激活跨端登录

```text
POST /api/v1/auth/account/activate
Authorization: Bearer <access_token>
```

用于已经通过小程序微信登录、但还没有安全邮箱和密码的账号。

请求：

```json
{
  "security_email": "user@example.com",
  "verify_code": "123456",
  "password": "StrongPassword123"
}
```

设置成功后，该账号可继续用微信登录，也可在 Android/Web/小程序通过安全邮箱或 `user_id` 加密码登录。

### 验证旧安全邮箱

```text
POST /api/v1/auth/security-email/change/verify-old
Authorization: Bearer <access_token>
```

请求：

```json
{
  "old_verify_code": "123456"
}
```

响应 `data`：

```json
{
  "change_ticket": "...",
  "expires_in": 600
}
```

`change_ticket` 短时间保存在 Redis 中。每个用户同一时间只保留一个最新有效 ticket，重新验证旧邮箱会覆盖旧 ticket。

### 确认修改安全邮箱

```text
POST /api/v1/auth/security-email/change/confirm
Authorization: Bearer <access_token>
```

请求：

```json
{
  "change_ticket": "...",
  "new_security_email": "new@example.com",
  "new_verify_code": "654321"
}
```

成功修改安全邮箱不会让当前登录态下线，也不会递增 `token_version`。

### 修改密码

```text
POST /api/v1/auth/password/change
Authorization: Bearer <access_token>
```

请求：

```json
{
  "old_password": "OldPassword123",
  "new_password": "NewPassword123"
}
```

成功后 `token_version` 递增，旧 refresh token 失效；已签发的 access token 按过期时间自然失效。

### 通过安全邮箱重置密码

```text
POST /api/v1/auth/password/reset
```

请求：

```json
{
  "security_email": "user@example.com",
  "verify_code": "123456",
  "new_password": "NewPassword123"
}
```

成功后同样递增 `token_version`，旧 refresh token 失效。

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
  "expires_in": 1800
}
```

### 退出登录

```text
POST /api/v1/auth/wx/logout
Authorization: Bearer <access_token>
```

当前路径保留 `wx/logout` 名称，但逻辑是通用退出登录：后端将 access token 的 `jti` 加入 Redis 黑名单。

## Users

### 当前用户

```text
GET /api/v1/users/me
```

响应字段同 `user_info`：

```json
{
  "user_id": "usr_xxx",
  "nickname": "张三",
  "avatar_url": "",
  "phone": "",
  "email": "notice@example.com",
  "security_email_masked": "u***@example.com",
  "security_email_verified": true,
  "password_enabled": true,
  "role": "USER",
  "created_at": "2026-06-08 12:00:00",
  "is_new_user": false
}
```

`email` 是通知邮箱，不用于登录；`security_email_masked` 是脱敏后的安全邮箱。

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

用于小程序或后续 Android/Web 客户端离线后补拉 Redis 中缓存的待推送事件。

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

### 获取事件详情

```text
GET /api/v1/events/{event_id}
```

仅在当前用户已绑定事件所属设备时返回事件详情。

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

配置后台登录方式：

```text
POST /api/v1/auth/password/login
```

开发种子管理员：

```text
账号：admin@example.com 或 usr_admin
密码：Admin123456
```

生产环境必须替换或删除种子管理员。

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
