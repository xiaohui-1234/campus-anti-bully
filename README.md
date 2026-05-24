# 校园防霸凌软件系统

本仓库按根目录两份说明书构建，包含：

- `campus-server`：Spring Boot 企业级后端。
- `campus-miniprogram`：微信小程序，五个 tab 页面。
- `campus-admin-web`：Vue 3 + Element Plus 配置后台。
- `sql/campus.sql`：MySQL 8 建表脚本。
- `docs`：HTTP、MQTT、Redis、MinIO、WebSocket、部署说明。

## 快速启动

1. 执行 `sql/campus.sql` 创建数据库表。
2. 修改 `campus-server/src/main/resources/application.yml` 中 MySQL、Redis、MinIO、MQTT、微信小程序配置。
3. 后端：`cd campus-server && mvn clean package`，再运行生成的 jar。
4. 后台：`cd campus-admin-web && npm install && npm run dev`。
5. 小程序：用微信开发者工具打开 `campus-miniprogram`。

## 本地 ADMIN token

SQL 内置了一个开发管理员用户，后端微信登录开发兜底模式下使用 code `admin` 会命中 `usr_admin` 并返回 ADMIN token。生产环境请配置真实微信 `app-id/app-secret` 并移除开发种子数据。
