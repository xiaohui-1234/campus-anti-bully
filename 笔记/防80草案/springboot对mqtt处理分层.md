系统 MQTT 模块采用“接收器（Receiver）→ 分拣器（Router）→ 去重器（DedupService）→ 队列（Queue）→ 处理器（Handler）→ 发布器（Publisher）”的分层架构设计。Receiver 仅负责接收 MQTT 消息，不直接处理业务；Router 根据 Topic 对消息进行分类分拣；DedupService 使用 Redis 基于 msgId 实现 QoS1 消息去重；去重成功后消息进入内存阻塞队列进行削峰与异步处理；不同业务由对应 Handler 独立处理，例如报警、状态、遥测、绑定、指令回复等；Publisher 统一负责向设备发布 MQTT 指令、ACK、配置等消息。整个架构实现了“接收与业务解耦、消息与处理解耦、上行与下行解耦”，既适合当前 Spring Boot + EMQX 的中小规模物联网项目，也方便后期扩展为多节点、分布式、高并发架构。



MQTT模块
├─ 接收器 Receiver
├─ 分拣器 Router
├─ 去重器 DedupService
├─ 队列 Queue
├─ 处理器 Handler
└─ 发布器 Publisher



Receiver：只负责接收 MQTT 消息
Router：根据 topic 分拣到不同业务类型
DedupService：根据 msgId 做 Redis 去重
Queue：削峰、异步缓冲
Handler：处理具体业务，比如报警、状态、遥测、绑定
Publisher：统一向设备发布命令、ACK、配置



设备上报
↓
Receiver
↓
Router
↓
DedupService
↓
Queue
↓
Handler
↓
数据库 / Redis / WebSocket / MinIO




com.xxx.mqtt
├─ receiver
├─ router
├─ dedup
├─ queue
├─ handler
├─ publisher
├─ dto
└─ config




alarm/post      → AlarmPostHandler
status/post     → StatusPostHandler
telemetry/post  → TelemetryPostHandler
bind/post       → BindPostHandler
cmd/reply       → CmdReplyHandler


缓存：
mqtt:dedup:{deviceId}:{msgId} = DONE

mqtt:dedup:DEV001:1747670405123_001 = DONE
mqtt:dedup:DEV001:1747670405124_002 = DONE
mqtt:dedup:DEV001:1747670405125_003 = DONE
设置过期时间