# Redis 缓存设计

Redis 用于 MQTT 去重、设备在线、临时绑定码、JWT 黑名单、邮箱验证码、登录失败限流、安全邮箱修改凭证、未拉取事件、MinIO 访问 URL 缓存和 WebSocket 辅助状态。

## Key 规范

| Key | 作用 | TTL 配置 |
| --- | --- | --- |
| `mqtt:dedup:{device_id}:{mqtt_msg_id}` | MQTT 消息去重 | `campus.cache.mqtt-dedup-ttl-seconds` |
| `device:online:{device_id}` | 设备在线状态 | `campus.cache.device-online-ttl-seconds` |
| `device:last_heartbeat:{device_id}` | 最近心跳时间戳 | `campus.cache.device-online-ttl-seconds` |
| `device:bind_code:{device_id}` | 临时绑定码 | `campus.cache.bind-code-ttl-seconds` |
| `jwt:blacklist:{token_id}` | 已退出或失效 access token | `campus.cache.jwt-blacklist-ttl-seconds` |
| `auth:email_code:{scene}:{email_hash}` | 邮箱验证码 HMAC | `campus.security.email-code.ttl-seconds` |
| `auth:email_code_limit:{scene}:{email_hash}` | 单邮箱重发冷却 | `campus.security.email-code.resend-interval-seconds` |
| `auth:email_code_attempt:{scene}:{email_hash}` | 验证码校验失败次数 | `campus.security.email-code.ttl-seconds` |
| `auth:email_code_ip_limit:{scene}:{ip_hash}` | 单 IP 发送次数限制 | `campus.security.email-code.ttl-seconds` |
| `auth:security_email_change_grant:{user_id}:{ticket_hash}` | 验证旧邮箱后的短时授权 | `campus.security.security-email-change.grant-ttl-seconds` |
| `auth:security_email_change_current:{user_id}` | 当前用户最新有效邮箱修改 ticket | `campus.security.security-email-change.grant-ttl-seconds` |
| `auth:login_fail:subject:{subject_hash}` | 按登录账号输入统计失败次数 | `campus.security.login-limit.lock-seconds` |
| `auth:login_fail:ip:{ip_hash}` | 按 IP 统计登录失败次数 | `campus.security.login-limit.lock-seconds` |
| `event:unpulled:user:{user_id}` | 用户离线未拉取事件 | `campus.cache.unpulled-event-cache-ttl-seconds` |
| `minio:access_url:{event_id}` | 文件访问预签名 URL 缓存 | `campus.cache.access-url-ttl-seconds` |
| `ws:user:{user_id}` | WebSocket 用户辅助状态预留 | 当前代码未持久写入 |
| `ws:device:{device_id}` | WebSocket 设备辅助状态预留 | 当前代码未持久写入 |

TTL 集中放在 `application.yml` 的 `campus.cache` 和 `campus.security` 下，业务代码不要散落硬编码。

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

## JWT 黑名单和 token_version

用户退出登录后，后端解析 access token 的 `jti`，写入：

```text
jwt:blacklist:{token_id}
```

后续请求若命中黑名单则返回 401。

修改密码或通过邮箱重置密码时，后端递增数据库中的 `user.token_version`。refresh token 中也包含版本号，刷新时版本不一致会返回 401，从而让旧 refresh token 失效。已签发的 access token 仍按较短过期时间自然失效。

## 邮箱验证码

验证码仅以 HMAC 形式存储，不保存明文验证码。发送验证码时会写入：

```text
auth:email_code:{scene}:{email_hash}
auth:email_code_limit:{scene}:{email_hash}
auth:email_code_ip_limit:{scene}:{ip_hash}
```

校验验证码时会增加：

```text
auth:email_code_attempt:{scene}:{email_hash}
```

验证成功后会删除验证码 key 和失败次数 key。验证码错误次数超过配置后，要求重新获取验证码。

支持的 `scene`：

```text
PASSWORD_REGISTER
ACTIVATE_ACCOUNT
RESET_PASSWORD
CHANGE_SECURITY_EMAIL_OLD
CHANGE_SECURITY_EMAIL_NEW
```

## 安全邮箱修改凭证

修改安全邮箱必须先验证旧邮箱。旧邮箱验证码通过后，后端生成一次性 `change_ticket`，并写入：

```text
auth:security_email_change_grant:{user_id}:{ticket_hash}
auth:security_email_change_current:{user_id}
```

同一用户只保留一个最新有效 ticket。再次验证旧邮箱会删除旧 grant 并覆盖 current。确认修改新邮箱时，会原子校验并消费新邮箱验证码、grant 和 current，成功后删除相关 Redis key。

安全邮箱修改成功不递增 `token_version`，不会强制当前设备或其他端下线。

## 登录失败限流

密码登录失败时同时记录：

```text
auth:login_fail:subject:{subject_hash}
auth:login_fail:ip:{ip_hash}
```

成功登录只清理当前 subject 的失败次数，IP 失败次数等待 TTL 自然过期，避免攻击者用一个可登录账号清空同 IP 的失败计数。

## 未拉取事件

当后端 WebSocket 推送新事件失败或需要补拉时，可使用：

```text
event:unpulled:user:{user_id}
```

客户端重连后会调用 `/api/v1/events/unpulled` 补拉。

## MinIO 访问 URL 缓存

刷新访问 URL 时可缓存：

```text
minio:access_url:{event_id}
```

数据库长期只保存 `file_key`，不保存完整预签名访问 URL。
