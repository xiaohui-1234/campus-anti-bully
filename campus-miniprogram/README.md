# campus-miniprogram

微信小程序端，当前包含登录、首页、事件、设备、状态和个人资料 6 个页面，其中 5 个主业务页面在 tabBar 中展示。

## 页面

- `pages/login`：微信登录，引导填写头像和昵称。
- `pages/home`：今日守护首页，显示在线设备数量、待关注事件和最新事件。
- `pages/events`：事件列表，支持设备、阅读状态、文件状态、推送状态、时间范围筛选；支持播放报警音频、查看详情、标记已读、删除事件。
- `pages/devices`：设备列表、绑定设备、编辑设备名称/位置/备注、解绑设备。
- `pages/status`：设备在线、未读事件等状态汇总。
- `pages/profile`：个人资料维护、头像上传、退出登录。

## 配置

编辑 `config/env.js`：

```js
const env = {
  baseUrl: 'https://your-domain.example/api/v1',
  wsUrl: 'wss://your-domain.example/ws/v1/client'
}
```

`baseUrl` 只写到 `/api/v1`，业务服务里再拼接 `/auth`、`/events` 等路径。`wsUrl` 固定为后端单连接地址 `/ws/v1/client`。

## 服务封装

- `services/request.js`：统一封装 `wx.request`，自动带 Bearer token，401 时尝试刷新 token。
- `services/websocket.js`：统一 WebSocket 单连接、重连、订阅设备和离线补拉。
- `services/auth-api.js`：微信登录和退出。
- `services/device-api.js`：设备列表、搜索、绑定、更新和解绑。
- `services/event-api.js`：事件列表、未拉取事件、标记已读、删除、刷新音频访问 URL。
- `services/user-api.js`：个人资料、头像上传。

## 展示规则

- 小程序端不直接显示设备上报的英文报警值，而是在 `utils/event-labels.js` 里维护两份映射：
  - `EVENT_TYPE_LABELS`：事件类型映射，如 `VOICE -> 声音`。
  - `ALARM_INFO_LABELS`：报警信息映射，如 `fight -> 打架`。
- 映射查不到时显示原始值，防止设备新增类型后页面空白。
- 原始接口字段仍保持 `snake_case`，例如 `event_type`、`alarm_info`。

## 开发约束

- 页面不要直接调用 `wx.request`，统一走 `services/request.js`。
- WebSocket 只通过 `services/websocket.js` 建立 `/ws/v1/client` 单连接。
- token 使用 `utils/storage.js` 管理，不在页面里散落保存。
- 不在前端持久化 openid、设备密钥、JWT 明文日志或 WiFi 密码。
