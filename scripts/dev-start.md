# 本地开发启动

1. 启动 MySQL、Redis、MinIO。
2. 执行 `sql/campus.sql`。
3. 后端：`cd campus-server && mvn spring-boot:run`。
4. 后台：`cd campus-admin-web && npm install && npm run dev`。
5. 微信开发者工具打开 `campus-miniprogram`。

如需 MQTT 联调，启动 EMQX 后将 `campus.mqtt.enabled` 改为 `true`。
