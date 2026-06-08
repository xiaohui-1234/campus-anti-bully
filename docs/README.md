# 文档目录

本目录按当前代码实现整理，用于开发、联调、部署和验收。除 `笔记`、`stm32串口日志留档` 外，项目说明文档应以这里为准。

## 文档清单

- `HTTP接口文档.md`：小程序端和后台端 HTTP 接口。
- `MQTT协议文档.md`：设备和后端之间的 topic、payload、去重和处理流程。
- `WebSocket设计.md`：客户端单连接、订阅消息、推送消息和权限校验。
- `数据库设计.md`：MySQL 表结构、索引和敏感字段约束。
- `Redis缓存设计.md`：Redis key、TTL 和业务用途。
- `MinIO文件存储设计.md`：对象 Key、预签名上传/访问 URL、头像和报警音频存储。
- `部署说明.md`：本地与生产部署步骤。
- `硬件端接口说明.md`：STM32/ESP8266/TF 卡/MQTT/MinIO 上传联调说明。
- `开发者接口字段说明书.md`：字段、枚举、端到端流程和验收要点。

## 全局原则

- JSON 字段统一 `snake_case`。
- WebSocket 只使用 `/ws/v1/client` 单连接，客户端通过消息订阅多个设备。
- MQTT topic 使用 `device/{product_type}/{device_id}/{action}`，不要随意改名。
- 不明文保存或返回 `openid`、`device_secret`、`wifi_password`、JWT、MinIO `secret_key`。
- 设备和事件相关 HTTP/WebSocket 操作必须校验当前用户是否绑定目标设备。
- Controller 保持薄层，复杂业务逻辑放在 Service、MQTT Handler、Storage Service 或 WebSocket Handler。
