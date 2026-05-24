设备端 MQTT QoS1 去重采用“msgId + 环形缓存”方案。设备收到 MQTT 消息后，先解析 JSON 获取 msgId，再遍历本地最近消息缓存数组判断是否已处理过；若存在则视为 QoS1 重复投递，不重复执行业务，仅返回 ACK；若不存在则执行业务逻辑，并将当前 msgId 写入环形缓存，通过游标循环覆盖旧记录，实现固定内存占用。msgId 推荐采用“13位毫秒时间戳 + 3位自增序号 + deviceId”格式，例如 1747670405123_001_DEV001，既保证全局唯一性，又具备时间有序性。



数组+游标缓存

1. 取出 msgId
2. 遍历 recentMsgIds
3. 如果存在：重复，丢弃业务，但可以回 ACK
4. 如果不存在：执行业务
5. 把 msgId 写入 recentMsgIds[cursor]
6. cursor = (cursor + 1) % DEDUP_SIZE