# campus-admin-web

Vue 3 + Vite + Element Plus 配置后台，用于查看和调整后端运行参数，并提供硬件 MQTT 联调工具。

## 命令

```bash
npm install
npm run dev
npm run build
npm run preview
```

`npm run dev` 默认监听 `127.0.0.1`。

## 页面

- `/login`：管理员登录，当前开发入口输入已登记管理员 openid，调用 `/api/v1/auth/openid/admin-login`。
- `/config`：配置总览，展示缓存、存储、MQTT、MinIO 当前配置。
- `/cache`：Redis TTL 配置，提交后影响后续业务。
- `/storage`：对象 Key 表达式、上传 URL 和访问 URL 有效期配置。
- `/mqtt`：MQTT 连接配置只读展示，基础设施连接参数变更后需要重启服务。
- `/mqtt-test`：纯前端硬件测试工具，可配置模拟设备、MQTT WebSocket Broker、遗嘱和定时任务。
- `/system`：系统信息，展示用户、设备、事件数量和运行环境。

## 接口与权限

- 普通登录接口走 `/api/v1`。
- 后台配置接口统一走 `/backend/v1`。
- 请求会从 `localStorage.admin_access_token` 读取 access token 并加到 `Authorization` Header。
- `/backend/v1/**` 后端要求 `ADMIN` 角色。
- 401 时后台会使用 `admin_refresh_token` 调用 `/api/v1/auth/refresh` 刷新 access token。

## 注意事项

- 后台展示的 MinIO `secret_key` 等敏感值应由后端脱敏，文档和页面不要写真实密钥。
- MQTT 测试工具的数据保存在浏览器本地，适合联调，不作为生产设备管理来源。
- 硬件测试工具的 topic/payload 模板应保持与 `docs/MQTT协议文档.md` 一致。
