# 账号安全与跨端登录说明

本文记录当前安全邮箱、密码、邮箱验证码、token 失效和跨端迁移方案。该方案不改变 MQTT topic、WebSocket 路径、设备绑定规则和事件权限校验。

## 设计目标

- 保留微信小程序登录能力，继续只保存 `openid_hash`。
- 保留 `user.email` 作为通知邮箱，不把它作为登录凭证。
- 新增安全邮箱和密码体系，方便后续 Android/Web 复用同一套后端。
- 支持邮箱或 `user_id` 加密码登录。
- 小程序老账号可以在登录后设置安全邮箱和密码，完成跨端账号激活。
- 修改安全邮箱需要旧邮箱验证码和新邮箱验证码。
- 修改或重置密码后让旧 refresh token 失效，access token 保持短生命周期自然过期。

## 数据模型

`user.email` 仍然是通知邮箱，不用于登录。

`user` 表新增或变更字段：

| 字段 | 说明 |
| --- | --- |
| `openid_hash` | 微信 openid 哈希，可为空 |
| `security_email_hash` | 规范化安全邮箱的 HMAC-SHA256，唯一且可为空 |
| `security_email_cipher` | AES-GCM 加密后的安全邮箱 |
| `security_email_verified` | 安全邮箱是否已验证 |
| `security_email_verified_time` | 安全邮箱验证时间 |
| `security_email_update_time` | 安全邮箱最近修改时间 |
| `password_hash` | BCrypt 密码哈希；盐值和成本包含在该字符串中 |
| `password_set_time` | 密码设置或重置时间 |
| `token_version` | token 版本，修改或重置密码后递增 |
| `update_time` | 更新时间 |

索引：

```text
uk_user_id(user_id)
uk_openid_hash(openid_hash)
uk_security_email_hash(security_email_hash)
```

MySQL 唯一索引允许多个 `NULL`，因此同时支持：

- 微信小程序老账号：`openid_hash` 有值，安全邮箱和密码为空。
- 邮箱密码注册账号：安全邮箱和密码有值，`openid_hash` 为空。
- 已激活跨端登录的小程序账号：`openid_hash`、安全邮箱、密码均有值。

已有数据库升级执行：

```text
sql/upgrade_account_security.sql
```

## 哈希和加密

安全邮箱：

```text
normalized_email = trim(email).lowercase()
security_email_hash = HMAC-SHA256(normalized_email, campus.security.crypto.hmac-secret)
security_email_cipher = AES-GCM(normalized_email, campus.security.crypto.aes-key)
```

`security_email_hash` 用于查找和唯一约束，不能反推出邮箱；`security_email_cipher` 用于服务端发验证码和脱敏展示。

密码：

```text
password_hash = BCrypt(password)
```

不需要单独 `salt` 字段。BCrypt 会把盐值和成本写进 `password_hash`。

openid：

```text
openid_hash = SHA-256(openid)
```

openid 不保存明文，也不返回给前端。当前实现没有对 openid 使用 HMAC；由于 openid 本身不是低熵密码，查表风险远低于普通密码，但生产环境如需更强隔离，可后续升级为 HMAC 并做兼容迁移。

## HTTP 接口

所有请求和响应 JSON 字段使用 `snake_case`。

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

场景：

```text
PASSWORD_REGISTER
ACTIVATE_ACCOUNT
RESET_PASSWORD
CHANGE_SECURITY_EMAIL_OLD
CHANGE_SECURITY_EMAIL_NEW
```

规则：

- `PASSWORD_REGISTER`：免登录，需要 `security_email`，要求邮箱未被其他账号使用。
- `ACTIVATE_ACCOUNT`：需要登录，需要 `security_email`，用于小程序老账号首次设置安全邮箱和密码。
- `RESET_PASSWORD`：免登录，需要 `security_email`，不暴露邮箱是否存在。
- `CHANGE_SECURITY_EMAIL_OLD`：需要登录，不需要 `security_email`，后端自动向当前安全邮箱发送验证码。
- `CHANGE_SECURITY_EMAIL_NEW`：需要登录，需要 `security_email` 和有效 `change_ticket`，要求新邮箱未被其他账号使用。

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

注册成功后直接签发 access token 和 refresh token。

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

`login_id` 可以是安全邮箱或 `user_id`。密码登录必须满足：

```text
security_email_verified = true
password_hash is not null
```

### 小程序账号激活跨端登录

```text
POST /api/v1/auth/account/activate
Authorization: Bearer <access_token>
```

请求：

```json
{
  "security_email": "user@example.com",
  "verify_code": "123456",
  "password": "StrongPassword123"
}
```

该接口只允许已经通过小程序微信登录且尚未设置安全邮箱和密码的账号使用。设置完成后，同一账号可继续微信登录，也可在 Android/Web 使用安全邮箱或 `user_id` 加密码登录。

### 修改安全邮箱：验证旧邮箱

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

后端同一用户只保留一个最新有效 `change_ticket`。重复验证旧邮箱会覆盖之前的 ticket。

### 修改安全邮箱：确认新邮箱

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

确认时会原子校验并消费：

- 新邮箱验证码。
- 当前最新 `change_ticket`。
- 旧邮箱验证 grant。

安全邮箱修改成功不递增 `token_version`，不会触发当前端或其他端下线。

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

成功后 `token_version` 递增，旧 refresh token 失效。

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

成功后 `token_version` 递增，旧 refresh token 失效。

## Redis Key

| Key | 作用 | TTL |
| --- | --- | --- |
| `auth:email_code:{scene}:{email_hash}` | 验证码 HMAC | `campus.security.email-code.ttl-seconds` |
| `auth:email_code_limit:{scene}:{email_hash}` | 单邮箱重发冷却 | `campus.security.email-code.resend-interval-seconds` |
| `auth:email_code_attempt:{scene}:{email_hash}` | 验证码校验失败次数 | `campus.security.email-code.ttl-seconds` |
| `auth:email_code_ip_limit:{scene}:{ip_hash}` | 单 IP 发送次数限制 | `campus.security.email-code.ttl-seconds` |
| `auth:security_email_change_grant:{user_id}:{ticket_hash}` | 验证旧邮箱后的短时授权 | `campus.security.security-email-change.grant-ttl-seconds` |
| `auth:security_email_change_current:{user_id}` | 当前用户最新有效 ticket | `campus.security.security-email-change.grant-ttl-seconds` |
| `auth:login_fail:subject:{subject_hash}` | 登录账号维度失败次数 | `campus.security.login-limit.lock-seconds` |
| `auth:login_fail:ip:{ip_hash}` | IP 维度失败次数 | `campus.security.login-limit.lock-seconds` |

## 配置

```yaml
campus:
  security:
    jwt:
      access-token-expire-seconds: 1800
      refresh-token-expire-seconds: 604800
    password:
      min-length: 8
      max-length: 64
      bcrypt-strength: 10
    email-code:
      code-length: 6
      ttl-seconds: 300
      resend-interval-seconds: 60
      max-attempts: 5
      max-ip-sends: 20
      mock: false
      from: ${CAMPUS_MAIL_FROM:no-reply@example.com}
    security-email-change:
      grant-ttl-seconds: 600
      ticket-bytes: 32
    login-limit:
      enabled: true
      max-fail-count: 5
      lock-seconds: 900
    crypto:
      hmac-secret: ${CAMPUS_HMAC_SECRET:change-me}
      aes-key: ${CAMPUS_AES_KEY:change-me}
```

开发阶段可将 `email-code.mock=true`，验证码只写日志不发真实邮件。生产环境应配置 SMTP 并设置 `mock=false`。

## 小程序端现状

小程序已新增密保管理页：

```text
campus-miniprogram/pages/security/index
```

入口在个人页。当前支持：

- 未设置安全邮箱和密码时，先设置安全邮箱和登录密码。
- 修改安全邮箱：旧邮箱验证码 -> `change_ticket` -> 新邮箱验证码 -> 确认修改。
- 修改密码：输入原密码和新密码。
- 忘记原密码：通过完整安全邮箱和验证码重置密码。

修改密码或重置密码成功后，小程序提示重新登录。

## Android/Web 移植

后端账号能力已经基本可直接复用。Android/Web 不需要 openid，可从以下接口开始：

- `POST /api/v1/auth/password/register`
- `POST /api/v1/auth/password/login`
- `POST /api/v1/auth/password/reset`
- `POST /api/v1/auth/email-code/send`
- `GET /api/v1/users/me`

登录后再复用：

- `POST /api/v1/auth/security-email/change/verify-old`
- `POST /api/v1/auth/security-email/change/confirm`
- `POST /api/v1/auth/password/change`
- 设备和事件接口
- `/ws/v1/client`

客户端需要各自实现：

- 请求拦截器自动携带 `Authorization: Bearer <access_token>`。
- HTTP 401 时使用 `refresh_token` 调用 `/api/v1/auth/refresh`。
- refresh 失败后清理本地登录态并跳转登录。
- 验证码倒计时、表单校验和错误提示。
- token 安全存储。Android 建议使用系统安全存储；Web 正式环境建议评估 `HttpOnly Cookie` 或更严格的前端安全策略。

## 限制策略

| 策略 | 当前实现 |
| --- | --- |
| access token 有效期 | `campus.security.jwt.access-token-expire-seconds`，当前 1800 秒 |
| refresh token 有效期 | `campus.security.jwt.refresh-token-expire-seconds`，当前 604800 秒 |
| 密码长度 | `campus.security.password.min-length/max-length`，当前 8 到 64 位 |
| 密码复杂度 | 必须同时包含字母和数字 |
| BCrypt 强度 | `campus.security.password.bcrypt-strength`，当前 10 |
| 验证码长度 | `campus.security.email-code.code-length`，当前 6 位 |
| 验证码有效期 | `campus.security.email-code.ttl-seconds`，当前 300 秒 |
| 同邮箱重发间隔 | `campus.security.email-code.resend-interval-seconds`，当前 60 秒 |
| 验证码最大错误次数 | `campus.security.email-code.max-attempts`，当前 5 次 |
| 单 IP 发送限制 | `campus.security.email-code.max-ip-sends`，当前每个 TTL 窗口 20 次 |
| 修改安全邮箱 ticket 有效期 | `campus.security.security-email-change.grant-ttl-seconds`，当前 600 秒 |
| 登录失败锁定 | `campus.security.login-limit.max-fail-count/lock-seconds`，当前 5 次、900 秒 |

## 安全注意

- 生产环境必须使用独立强随机 `jwt.secret`、`hmac-secret`、`aes-key`。
- `aes-key` 变更会导致旧安全邮箱无法解密，必须妥善备份和管理。
- 不要在日志、响应、文档或截图里暴露明文 openid、JWT、邮箱验证码、设备密钥、MinIO secret。
- 邮箱验证码邮件只用于身份确认，邮件内容不要包含密码或 token。
- CORS 在正式 Web 部署时应限制为实际域名。
