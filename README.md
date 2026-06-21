# 校园防霸凌软件系统

本仓库包含校园防霸凌系统的完整交付代码与说明文档，当前实现由 Spring Boot 后端、微信小程序、Vue 配置后台、STM32 主控端、SQL 初始化脚本和联调文档组成。

## 目录结构

- `campus-server`：Spring Boot 3 后端，提供微信登录、邮箱密码跨端账号、JWT 鉴权、用户、设备、事件、MinIO、MQTT、WebSocket 和后台配置接口。
- `campus-miniprogram`：微信小程序端，包含登录、首页、事件、设备、状态、个人资料和密保管理页面。
- `campus-admin-web`：Vue 3 + Vite + Element Plus 配置后台，包含缓存、存储、MQTT、系统信息和硬件 MQTT 测试工具。
- `stm32主控源码`：STM32F103 主控工程，负责按键/语音报警、WAV 录音、TF 卡、ESP8266、MQTT 上报和 HTTP 直传对象存储。
- `sql/campus.sql`：MySQL 初始化脚本，包含开发用设备和管理员种子数据。
- `docs`：HTTP、MQTT、WebSocket、Redis、MinIO、数据库、部署和硬件端说明。
- `scripts`：本地启动、后端检查和配置示例说明。

## 当前核心能力

- 微信小程序登录，后端只保存 `openid_hash`，不返回明文 openid。
- 安全邮箱和密码账号体系，支持邮箱或 `user_id` 加密码登录，便于后续 Android/Web 端复用。
- 小程序老账号可在密保管理中设置安全邮箱和密码，之后可跨端登录。
- 安全邮箱加密保存并额外保存 HMAC 哈希；密码使用 BCrypt，盐值包含在 `password_hash` 中。
- 用户绑定设备必须使用设备上报到 Redis 的临时绑定码。
- 所有设备和事件接口都会校验当前用户是否绑定目标设备。
- 设备通过固定 MQTT topic 上报报警、在线状态、绑定码和 WiFi 列表。
- 后端收到报警后生成事件和 MinIO 预签名上传 URL，并通过 MQTT 下发给设备。
- STM32 设备收到上传 URL 后用 ESP8266 进行 HTTP PUT 上传本地 WAV 文件。
- WebSocket 固定使用 `/ws/v1/client` 单连接，客户端订阅多个设备。
- 小程序事件列表支持中英映射显示，例如 `VOICE/fight` 显示为 `声音/打架`，未知值原样显示。
- 后台配置页只允许 ADMIN 角色访问，敏感密钥在文档中只使用占位符。

## 快速启动

1. 准备 JDK 17、Maven、MySQL 8、Redis、MinIO、Node.js 20+，需要硬件联调时再准备 EMQX。
2. 执行 `sql/campus.sql` 创建数据库表和开发种子数据。
3. 修改或外部覆盖 `campus-server/src/main/resources/application.yml` 中的数据库、Redis、邮件、MinIO、MQTT、JWT、账号安全、微信小程序配置。
4. 启动后端：

```bash
cd campus-server
mvn spring-boot:run
```

5. 启动配置后台：

```bash
cd campus-admin-web
npm install
npm run dev
```

6. 使用微信开发者工具打开 `campus-miniprogram`，按实际环境修改 `config/env.js`。

## 开发账号说明

`sql/campus.sql` 内置开发设备 `dev001` 和开发管理员 `usr_admin`。后台开发登录走邮箱或用户 ID 加密码：

```text
账号：admin@example.com 或 usr_admin
密码：Admin123456
```

生产环境必须替换或移除种子管理员，并为正式管理员单独设置安全邮箱和强密码。

如果是已经初始化过的开发库，可执行 `sql/init_admin_password_login.sql` 补齐这个管理员密码登录种子数据。

普通用户跨端登录走：

```text
POST /api/v1/auth/password/login
```

`login_id` 可以传安全邮箱或 `user_id`。没有安全邮箱和密码的小程序账号，需要先在小程序密保管理中完成账号激活。

## 重要约束

- HTTP 和 WebSocket JSON 字段统一使用 `snake_case`。
- WebSocket 只能使用 `/ws/v1/client` 单连接。
- MQTT topic 必须保持文档和代码定义，不要随意改名。
- 不要明文保存或返回 `openid`、`device_secret`、`wifi_password`、JWT、MinIO `secret_key`。
- `email` 是通知邮箱，不用于登录；登录与找回使用 `security_email_hash/security_email_cipher`。
- Controller 只做参数接收和调用 Service，复杂业务逻辑放 Service/Handler。

## 文档入口

优先阅读 `docs/README.md`。账号体系和跨端登录看 `docs/account-security-extension.md` 与 `docs/HTTP接口文档.md`；硬件联调优先看 `docs/硬件端接口说明.md` 和 `docs/MQTT协议文档.md`；小程序/后台联调优先看 `docs/HTTP接口文档.md` 和 `docs/WebSocket设计.md`。
