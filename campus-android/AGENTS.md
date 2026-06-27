# AGENTS.md

## Android 移植定位

Android 客户端功能对齐微信小程序，但不复用微信登录流程。登录、注册、找回密码、刷新 token 使用跨端账号接口，业务权限仍由 Spring Boot 后端统一校验。

## 最高优先级规则

1. JSON 字段保持 snake_case。
2. 请求域名固定使用 `https://campus.1314-520.xyz/api/v1/`。
3. WebSocket 只能连接 `wss://campus.1314-520.xyz/ws/v1/client` 单连接。
4. Android 端不得保存或打印 openid、device_secret、wifi_password、JWT 明文、MinIO secretKey。
5. JWT 只能写入 Android 加密存储，不得明文写入普通 SharedPreferences。
6. MinIO accessKey/secretKey 不得出现在 Android 端。
7. 音频访问必须通过后端 refresh-url 获取短期 URL，不允许拼接 MinIO 公网地址。
8. 删除事件只调用后端事件删除接口，不允许客户端直接操作 MinIO。
9. 业务权限以后端返回为准，Android UI 隐藏不作为安全边界。
10. 日志禁止输出 Authorization、token、upload_url、file_url、object_key、device_secret、wifi_password。

## 技术栈

Kotlin、Jetpack Compose、Material 3、Retrofit、OkHttp WebSocket、Coroutine/Flow、EncryptedSharedPreferences、WorkManager、Media3。

## UI 风格

参考浅色简约卡片风，整体克制、可信、舒适。使用浅灰蓝背景、白色/淡蓝卡片、8-12dp 圆角和低饱和蓝绿主色。风险用红/橙/蓝标签和细条表达，不使用大面积刺眼红色。
