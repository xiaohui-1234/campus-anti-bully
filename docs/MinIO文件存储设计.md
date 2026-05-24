# MinIO 文件存储设计

数据库只长期保存 `file_key`，不保存完整访问 URL。

默认对象 Key：

```text
{product_type}/audio/{device_id}/{yyyy}/{MM}/{dd}/{event_id}.{ext}
```

上传流程：

1. 设备发布 `alarm/post`。
2. 后端创建事件，状态为 `UPLOADING`。
3. 后端生成 MinIO 临时上传 URL。
4. 后端通过 MQTT `alarm/upload` 下发上传信息。
5. 设备直传 MinIO 后发布 `alarm/confirm`。
6. 后端更新文件状态并尝试 WebSocket 推送。
