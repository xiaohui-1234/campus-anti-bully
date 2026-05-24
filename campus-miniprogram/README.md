# campus-miniprogram

微信小程序端，包含事件、设备、主页、状态、个人五个 tab。

## 配置

编辑 `config/env.js`：

```js
baseUrl: 'http://127.0.0.1:8080/api/v1'
wsUrl: 'ws://127.0.0.1:8080/ws/v1/client'
```

## 约束

- 页面不直接使用 `wx.request`，统一走 `services/request.js`。
- WebSocket 统一走 `services/websocket.js`。
- 401 自动刷新 token，刷新失败跳转个人页重新登录。
