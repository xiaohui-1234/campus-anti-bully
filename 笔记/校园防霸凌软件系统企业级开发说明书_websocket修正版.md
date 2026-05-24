# 校园防霸凌软件系统企业级开发说明书

> 文档编码：UTF-8  
> 文档语言：中文  
> 目标用途：交给 Codex / AI 从 0 构建后端、小程序端、配置后台 Web 入口  
> 项目范围：软件层面的 Spring Boot 后端、微信小程序、管理配置后台入口；硬件端由项目方另行实现  
> 重要约束：MQTT 面向设备的 topic 与 payload 尽量保持当前草案，不做大改；数据库字段尽量保持当前草案，只在必要处进行规范化说明  

---

## 0. 文档定位

本文档不是普通功能说明，而是用于指导 AI 从 0 构建项目的**企业级开发规范说明书**。

AI 实现时必须遵守以下原则：

1. **稳定可靠优先**：不要为了“炫技”牺牲可维护性。
2. **分层清晰优先**：Controller、Service、Repository/Mapper、DTO、VO、Config、Security、MQTT、WebSocket 等必须拆分清楚。
3. **命名统一优先**：数据库使用 snake_case，Java 使用 camelCase，JSON 使用 snake_case。
4. **面向二次开发**：代码结构、配置项、注释、文档要方便企业后续继续开发。
5. **硬件协议保持稳定**：MQTT topic 和设备 payload 不做大改，除非存在明显 JSON 错误或安全风险。
6. **所有源文件、配置文件、文档均使用 UTF-8 编码**，不得出现中文乱码。

---

## 1. 项目概述

本项目是一个面向校园防霸凌场景的物联网报警系统。

系统由以下部分组成：

```text
硬件设备  →  EMQX MQTT服务器  →  Spring Boot后端  →  微信小程序 / Web配置后台
                                 ↓
                              MySQL
                              Redis
                              MinIO
                              WebSocket
```

### 1.1 核心业务目标

设备端负责采集报警事件，例如异常语音、打架、呼救等事件，并通过 MQTT 上报到服务器。

服务器负责：

1. 接收设备报警事件。
2. 对 MQTT 消息进行去重。
3. 创建事件记录。
4. 为设备生成 MinIO 临时上传地址。
5. 接收设备文件上传确认。
6. 将新事件通过 WebSocket 实时推送给小程序。
7. 在小程序上线后支持补拉未送达事件。
8. 支持用户绑定设备、查看事件、刷新语音访问链接、确认已读。
9. 支持后台 Web 入口管理系统配置、缓存规则、MQTT、MinIO、Redis 等可控参数。

### 1.2 软件开发范围

本次 AI 主要实现：

```text
1. Spring Boot 后端
2. 微信小程序端
3. 配置后台 Web 入口
4. MySQL 数据库脚本
5. Redis 缓存封装
6. MinIO 文件访问封装
7. EMQX/MQTT 对接模块
8. WebSocket 实时推送模块
9. 项目配置文件与说明文档
```

不要求 AI 实现：

```text
1. STM32 固件
2. ESP8266 固件
3. 麦克风采集
4. SD 卡录音
5. ASRPro 识别
6. SIM 卡通信
7. 设备侧 MQTT 底层代码
```

硬件部分只需要按本文档约定的 MQTT 协议与后端通信。

---

## 2. 技术选型

### 2.1 后端

```text
语言：Java 17
框架：Spring Boot 3.x
权限：Spring Security + JWT
ORM：MyBatis-Plus
数据库：MySQL 8.x
缓存：Redis
对象存储：MinIO
MQTT：EMQX + Java MQTT Client
实时通信：Spring WebSocket
接口文档：可选 SpringDoc OpenAPI
构建工具：Maven
```

### 2.2 小程序端

```text
平台：微信小程序
语言：JavaScript
接口请求：wx.request 封装
文件上传：wx.uploadFile
WebSocket：wx.connectSocket
界面风格：白色 / 灰色 / 简约 / 有层次感
```

### 2.3 配置后台 Web 入口

后台 Web 入口用于系统配置与运维管理，不作为复杂业务后台。

建议技术：

```text
Vue 3 + Vite + Element Plus
```

后台接口前缀：

```text
/backend/v1/
```

小程序业务接口前缀：

```text
/api/v1/
```

---

## 3. 全局开发规范

### 3.1 编码规范

所有文件必须使用：

```text
UTF-8
```

包括：

```text
Java 源码
YAML 配置
SQL 文件
Markdown 文档
小程序 JS/WXML/WXSS/JSON
Vue 文件
```

### 3.2 命名规范

```text
数据库表名：snake_case
数据库字段：snake_case
Java 类名：PascalCase
Java 字段：camelCase
JSON 字段：snake_case
URL 路径：kebab-case 或小写单词
MQTT 字段：snake_case
```

示例：

```sql
device_id
last_online_time
openid_hash
```

```java
private String deviceId;
private LocalDateTime lastOnlineTime;
private String openidHash;
```

```json
{
  "device_id": "dev001",
  "last_online_time": "2026-05-23 12:30:00"
}
```

Spring Boot 必须配置 Jackson 输出 snake_case：

```yaml
spring:
  jackson:
    property-naming-strategy: SNAKE_CASE
    time-zone: Asia/Shanghai
    date-format: yyyy-MM-dd HH:mm:ss
```

### 3.3 统一返回结构

所有 HTTP 接口统一返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

推荐错误码：

```text
0       成功
1       业务失败
-1      参数错误或系统异常
401     未登录或 token 失效
403     无权限
404     资源不存在
409     资源冲突或重复
500     服务器内部错误
```

分页返回结构统一为：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [],
    "total": 0,
    "page": 1,
    "size": 10
  }
}
```

### 3.4 认证规范

除登录、刷新 token 外，其余业务接口默认需要：

```http
Authorization: Bearer access_token
```

后端永远不能相信前端传来的 user_id。  
用户身份必须从 JWT 中解析。

### 3.5 时间格式规范

HTTP JSON 中时间统一：

```text
yyyy-MM-dd HH:mm:ss
```

MQTT payload 中设备上报时间可使用毫秒时间戳：

```json
{
  "timestamp": 1779177600123
}
```

后端入库时转换为 `DATETIME`。

---

## 4. 总体架构设计

### 4.1 后端推荐包结构

```text
campus-server
├─ src/main/java/com/campus
│  ├─ CampusApplication.java
│  │
│  ├─ common
│  │  ├─ result
│  │  │  ├─ ApiResult.java
│  │  │  └─ PageResult.java
│  │  ├─ exception
│  │  │  ├─ BizException.java
│  │  │  └─ GlobalExceptionHandler.java
│  │  ├─ enums
│  │  ├─ constants
│  │  └─ util
│  │
│  ├─ config
│  │  ├─ JacksonConfig.java
│  │  ├─ SecurityConfig.java
│  │  ├─ RedisConfig.java
│  │  ├─ MinioConfig.java
│  │  ├─ MqttConfig.java
│  │  └─ WebSocketConfig.java
│  │
│  ├─ security
│  │  ├─ JwtTokenProvider.java
│  │  ├─ JwtAuthenticationFilter.java
│  │  ├─ LoginUser.java
│  │  └─ SecurityContextUtil.java
│  │
│  ├─ module
│  │  ├─ auth
│  │  │  ├─ controller
│  │  │  ├─ service
│  │  │  ├─ dto
│  │  │  └─ vo
│  │  │
│  │  ├─ user
│  │  │  ├─ controller
│  │  │  ├─ service
│  │  │  ├─ entity
│  │  │  ├─ mapper
│  │  │  ├─ dto
│  │  │  └─ vo
│  │  │
│  │  ├─ device
│  │  │  ├─ controller
│  │  │  ├─ service
│  │  │  ├─ entity
│  │  │  ├─ mapper
│  │  │  ├─ dto
│  │  │  └─ vo
│  │  │
│  │  ├─ event
│  │  │  ├─ controller
│  │  │  ├─ service
│  │  │  ├─ entity
│  │  │  ├─ mapper
│  │  │  ├─ dto
│  │  │  └─ vo
│  │  │
│  │  ├─ storage
│  │  │  ├─ service
│  │  │  ├─ dto
│  │  │  └─ vo
│  │  │
│  │  ├─ websocket
│  │  │  ├─ handler
│  │  │  ├─ session
│  │  │  └─ dto
│  │  │
│  │  ├─ mqtt
│  │  │  ├─ receiver
│  │  │  ├─ router
│  │  │  ├─ dedup
│  │  │  ├─ queue
│  │  │  ├─ handler
│  │  │  ├─ publisher
│  │  │  ├─ dto
│  │  │  └─ topic
│  │  │
│  │  └─ admin
│  │     ├─ controller
│  │     ├─ service
│  │     ├─ dto
│  │     └─ vo
│  │
│  └─ infrastructure
│     ├─ redis
│     ├─ minio
│     ├─ emqx
│     └─ wx
│
├─ src/main/resources
│  ├─ application.yml
│  ├─ application-dev.yml
│  ├─ application-prod.yml
│  ├─ mapper
│  └─ sql
│
└─ pom.xml
```

### 4.2 MQTT 模块分层

MQTT 模块必须采用以下分层：

```text
Receiver → Router → DedupService → Queue → Handler → Publisher
```

含义：

```text
Receiver：只负责接收 MQTT 消息
Router：根据 topic 分拣到不同业务类型
DedupService：根据 mqtt_msg_id 做 Redis 去重
Queue：削峰、异步缓冲
Handler：处理具体业务，比如报警、状态、上传确认、WiFi配置回复
Publisher：统一向设备发布命令、ACK、配置、上传地址
```

设备上报处理流程：

```text
设备上报 MQTT
↓
Receiver 接收
↓
Router 按 topic 分发
↓
DedupService Redis 去重
↓
Queue 进入阻塞队列削峰
↓
Handler 处理业务
↓
MySQL / Redis / MinIO / WebSocket
```

---

## 5. 数据库设计

数据库名：

```text
campus
```

数据表：

```text
device
user
user_device_bind
event
device_config_wifi
```

> 说明：以下表结构尽量保持草案字段。  
> 如果 AI 实现时担心 `user` 是敏感表名，可在 SQL 中使用反引号 `user`，不要擅自大改业务字段。

---

### 5.1 device 设备表

用途：保存设备基础身份信息。在线状态主要放 Redis，数据库只保存最后在线时间。

```sql
CREATE TABLE IF NOT EXISTS device (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键自增',

    device_id VARCHAR(64) NOT NULL COMMENT '设备对外唯一标识',
    product_type VARCHAR(64) NOT NULL COMMENT '产品类型，同时可作为 EMQX 用户名/账号分组',
    device_secret VARCHAR(255) NOT NULL COMMENT '设备密钥哈希值，不保存明文',

    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '设备生产/注册时间',
    last_online_time DATETIME DEFAULT NULL COMMENT '最后一次在线时间',

    UNIQUE KEY uk_device_id (device_id),
    KEY idx_product_type (product_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备表';
```

字段说明：

```text
id                内部主键
device_id         对外设备标识，接口和 MQTT 使用
product_type      产品类型，MQTT topic 中使用
device_secret     哈希后的设备密钥
create_time       设备生产/注册时间
last_online_time  最后在线时间
```

---

### 5.2 user 用户表

用途：保存微信小程序用户信息。

```sql
CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键自增',

    user_id VARCHAR(64) NOT NULL COMMENT '用户对外唯一标识',
    openid_hash VARCHAR(255) NOT NULL COMMENT '微信 openid 哈希值，不保存明文 openid',

    nickname VARCHAR(64) DEFAULT NULL COMMENT '用户名/昵称',
    email VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    phone VARCHAR(32) DEFAULT NULL COMMENT '手机号',
    avatar_url VARCHAR(512) DEFAULT NULL COMMENT '头像访问URL',
    role VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '权限：USER/ADMIN',

    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',

    UNIQUE KEY uk_user_id (user_id),
    UNIQUE KEY uk_openid_hash (openid_hash),
    KEY idx_phone (phone),
    KEY idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

重要规则：

```text
1. 前端登录时只传 wx.login 获取到的临时 code。
2. 后端使用 code 换取 openid。
3. 数据库只保存 openid_hash，不保存明文 openid。
4. 不存 wx_code，wx_code 是临时值，不适合入库。
```

---

### 5.3 event 事件表

用途：保存设备上报的报警事件，以及文件上传状态、推送状态、阅读状态。

```sql
CREATE TABLE IF NOT EXISTS event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键id',

    mqtt_msg_id VARCHAR(128) NOT NULL COMMENT 'MQTT消息id，用于数据库兜底去重',
    device_table_id BIGINT NOT NULL COMMENT '外键，事件所属设备，对应 device.id',

    event_id VARCHAR(64) NOT NULL COMMENT '对外事件ID',
    event_type VARCHAR(64) NOT NULL COMMENT '事件类型，由设备决定并上报',
    alarm_info VARCHAR(255) DEFAULT NULL COMMENT '报警信息',

    file_key VARCHAR(512) DEFAULT NULL COMMENT '语音/图片/视频文件对象存储key',
    file_status VARCHAR(32) NOT NULL DEFAULT 'UPLOADING' COMMENT '文件上传状态：UPLOADING/SUCCESS/FAILED',

    push_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '推送状态：PENDING/PUSHED/FAILED',
    read_status VARCHAR(32) NOT NULL DEFAULT 'UNREAD' COMMENT '阅读状态：UNREAD/READ',

    event_time DATETIME NOT NULL COMMENT '设备事件发生时间',

    UNIQUE KEY uk_event_id (event_id),
    UNIQUE KEY uk_device_mqtt_msg (device_table_id, mqtt_msg_id),
    KEY idx_device_event_time (device_table_id, event_time),
    KEY idx_push_status (push_status),
    KEY idx_read_status (read_status),

    CONSTRAINT fk_event_device FOREIGN KEY (device_table_id) REFERENCES device(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事件表';
```

状态含义：

```text
file_status:
    UPLOADING   等待设备上传文件
    SUCCESS     文件已上传成功
    FAILED      文件上传失败

push_status:
    PENDING     未送达前端
    PUSHED      已通过 WebSocket 或补拉接口送达前端
    FAILED      推送失败，可由后期补偿任务处理

read_status:
    UNREAD      用户未读
    READ        用户已读
```

说明：

```text
event_time 是设备事件发生时间。
初版可以不加 create_time。
如果后期需要区分“设备发生时间”和“服务器入库时间”，再扩展 create_time。
```

---

### 5.4 user_device_bind 绑定表

用途：保存用户与设备之间的绑定关系，以及用户对设备的自定义备注信息。

```sql
CREATE TABLE IF NOT EXISTS user_device_bind (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键id',

    device_table_id BIGINT NOT NULL COMMENT '外键，对应 device.id',
    user_table_id BIGINT NOT NULL COMMENT '外键，对应 user.id',

    bind_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    device_name VARCHAR(64) DEFAULT NULL COMMENT '用户给绑定设备设置的名称',
    location VARCHAR(128) DEFAULT NULL COMMENT '设备所在地点备注',
    note VARCHAR(255) DEFAULT NULL COMMENT '设备备注信息',

    UNIQUE KEY uk_user_device (user_table_id, device_table_id),
    KEY idx_user_table_id (user_table_id),
    KEY idx_device_table_id (device_table_id),

    CONSTRAINT fk_bind_device FOREIGN KEY (device_table_id) REFERENCES device(id),
    CONSTRAINT fk_bind_user FOREIGN KEY (user_table_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户设备绑定表';
```

说明：

```text
device_table_id 保存 device.id，不是 device.device_id。
user_table_id 保存 user.id，不是 user.user_id。
接口对外仍然使用 device_id 和 user_id。
```

初版删除绑定可以物理删除。  
后期如需审计，可扩展 `bind_status` 和 `unbind_time`。

---

### 5.5 device_config_wifi WiFi配置表

用途：保存设备上报的 WiFi 配置列表，不保存 WiFi 密码。

```sql
CREATE TABLE IF NOT EXISTS device_config_wifi (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键id',

    device_table_id BIGINT NOT NULL COMMENT '外键，指向 device.id',
    config_id INT NOT NULL COMMENT '设备内部某一配置编号',
    wifi_name VARCHAR(128) NOT NULL COMMENT 'WiFi名称',
    set_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上一次更新时间',

    UNIQUE KEY uk_device_config (device_table_id, config_id),
    KEY idx_device_table_id (device_table_id),

    CONSTRAINT fk_wifi_device FOREIGN KEY (device_table_id) REFERENCES device(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备WiFi配置表';
```

重要规则：

```text
1. 平台可以下发 wifi_password。
2. 设备不能回传 wifi_password。
3. 数据库不长期保存 wifi_password。
```

---

## 6. Redis 缓存设计

Redis 用于保存短期状态、热点数据、去重标记、临时权限等。

### 6.1 Redis 使用原则

数据库保存：

```text
用户
设备
绑定关系
事件记录
文件 key
WiFi 配置记录
```

Redis 保存：

```text
设备在线状态
设备心跳时间
绑定码
MQTT 去重记录
WebSocket 连接关系
临时访问 URL 缓存
热点未推送事件辅助信息
JWT 黑名单
```

### 6.2 推荐 Key 规范

```text
mqtt:dedup:{device_id}:{mqtt_msg_id}
device:online:{device_id}
device:last_heartbeat:{device_id}
device:bind_code:{device_id}
jwt:blacklist:{token_id}
event:unpulled:user:{user_id}
minio:access_url:{event_id}
ws:user:{user_id}
ws:device:{device_id}
```

### 6.3 推荐 TTL 配置

TTL 不写死在代码中，必须在 YAML 中配置。

```yaml
campus:
  cache:
    mqtt-dedup-ttl-seconds: 86400
    device-online-ttl-seconds: 90
    bind-code-ttl-seconds: 60
    access-url-ttl-seconds: 3600
    jwt-blacklist-ttl-seconds: 7200
    unpulled-event-cache-ttl-seconds: 300
```

### 6.4 MQTT 去重 Key

```text
mqtt:dedup:dev001:1779177600123-001-dev001 = DONE
```

处理逻辑：

```text
1. 收到 MQTT 消息。
2. 解析 mqtt_msg_id。
3. Redis SETNX 去重 key。
4. 如果 SETNX 成功，继续业务。
5. 如果 SETNX 失败，说明重复投递，不重复执行业务。
6. 数据库再通过 unique(device_table_id, mqtt_msg_id) 兜底防重复。
```

---

## 7. MinIO 对象存储设计

### 7.1 存储原则

数据库只保存：

```text
file_key
```

不长期保存完整 URL。

原因：

```text
1. URL 可能过期。
2. 域名可能变化。
3. 桶名可能变化。
4. CDN 或代理规则可能变化。
5. 权限策略可能变化。
```

### 7.2 推荐 Bucket

```text
campus
```

也可以按环境区分：

```text
campus-dev
campus-prod
```

### 7.3 对象 Key 生成规则

默认表达式：

```text
{product_type}/audio/{device_id}/{yyyy}/{MM}/{dd}/{event_id}.{ext}
```

示例：

```text
anti_bullying/audio/dev001/2026/05/19/evt_20260519_dev001_xxxx.wav
```

配置文件：

```yaml
campus:
  storage:
    bucket: campus
    object-key-pattern: "{product_type}/audio/{device_id}/{yyyy}/{MM}/{dd}/{event_id}.{ext}"
    upload-url-expire-seconds: 300
    access-url-expire-seconds: 3600
```

### 7.4 文件上传流程

```text
设备 alarm/post 上报报警信息
↓
后端创建 event 记录，file_status = UPLOADING
↓
后端生成 MinIO 临时上传 URL
↓
后端通过 MQTT alarm/upload 下发 event_id、object_key、upload_url
↓
设备直传文件到 MinIO
↓
设备 alarm/confirm 确认上传结果
↓
后端更新 event.file_key 和 file_status
↓
后端通过 WebSocket 推送新事件
```

---

## 8. MQTT 协议设计

### 8.1 MQTT 连接鉴权

设备连接 MQTT 所需信息：

```json
{
  "mqtt_url": "mqtt://example.com:1883",
  "device_id": "dev001",
  "product_type": "anti_bullying",
  "device_secret": "xxxxxxxx"
}
```

建议：

```text
Client ID：device_id
Username：product_type
Password：device_secret
```

后端设备表保存：

```text
device_secret 哈希值
```

EMQX 可根据 product_type 和 device_id 限制发布/订阅 topic。

---

### 8.2 MQTT topic 总览

```text
设备发布：
device/{product_type}/{device_id}/alarm/post
device/{product_type}/{device_id}/alarm/confirm
device/{product_type}/{device_id}/status/online
device/{product_type}/{device_id}/config/wifi/set/reply
device/{product_type}/{device_id}/config/wifi/list

设备订阅：
device/{product_type}/{device_id}/alarm/upload
device/{product_type}/{device_id}/config/wifi/set
```

### 8.3 MQTT 消息 ID 规则

字段名统一使用：

```text
mqtt_msg_id
```

推荐格式：

```text
13位毫秒时间戳-3位自增序号-device_id
```

示例：

```text
1779177600123-001-dev001
```

用途：

```text
1. QoS1 重复投递去重
2. 请求与回复关联
3. 数据库兜底唯一约束
4. 日志追踪
```

---

### 8.4 设备上报警报事件

设备发布：

```text
device/{product_type}/{device_id}/alarm/post
```

说明：用于上传报警信息。

Payload：

```json
{
  "mqtt_msg_id": "1779177600123-001-dev001",
  "product_type": "anti_bullying",
  "device_id": "dev001",
  "event_type": "voice_alarm",
  "alarm_info": "打架",
  "file_name": "001.wav",
  "file_size": 123456,
  "content_type": "audio/wav",
  "timestamp": 1779177600123
}
```

后端处理：

```text
1. 校验 topic 中 product_type/device_id 与 payload 一致。
2. 根据 device_id 查询设备。
3. Redis + 数据库去重 mqtt_msg_id。
4. 生成 event_id。
5. 根据文件名和 content_type 生成 object_key。
6. 创建 event 记录，file_status = UPLOADING，push_status = PENDING，read_status = UNREAD。
7. 生成 MinIO 临时上传 URL。
8. 发布 alarm/upload 给设备。
```

---

### 8.5 后端返回上传地址

设备订阅：

```text
device/{product_type}/{device_id}/alarm/upload
```

说明：返回事件 ID、临时上传地址和有效时间。

Payload：

```json
{
  "mqtt_msg_id": "1779177600123-001-dev001",
  "event_id": "evt_20260519_dev001_xxxx",
  "object_key": "anti_bullying/audio/dev001/2026/05/19/evt_20260519_dev001_xxxx.wav",
  "upload_url": "https://xxx",
  "expire_seconds": 300
}
```

说明：

```text
mqtt_msg_id 使用原 alarm/post 的 mqtt_msg_id，用于设备关联请求。
event_id 是后端生成的业务事件 ID。
object_key 是最终入库的文件键名。
upload_url 是 MinIO 临时上传 URL。
```

---

### 8.6 设备确认文件上传结果

设备发布：

```text
device/{product_type}/{device_id}/alarm/confirm
```

Payload：

```json
{
  "mqtt_msg_id": "1779177600123-001-dev001",
  "product_type": "anti_bullying",
  "device_id": "dev001",
  "event_id": "evt_20260519_dev001_xxxx",
  "object_key": "anti_bullying/audio/dev001/2026/05/19/evt_20260519_dev001_xxxx.wav",
  "upload_status": "success"
}
```

后端处理：

```text
1. 校验设备身份。
2. 根据 event_id 查询事件。
3. 校验 object_key 是否匹配。
4. upload_status = success 时，更新 file_status = SUCCESS。
5. upload_status 失败时，更新 file_status = FAILED。
6. 尝试通过 WebSocket 推送给绑定用户。
7. 若用户不在线或推送失败，保持 push_status = PENDING，等待 unpulled 接口补拉。
```

---

### 8.7 设备在线状态

设备发布：

```text
device/{product_type}/{device_id}/status/online
```

说明：更新在线状态。下线由 MQTT 遗嘱消息实现，遗嘱状态为 offline，timestamp 为 0。

Payload：

```json
{
  "mqtt_msg_id": "1779177600123-001-dev001",
  "product_type": "anti_bullying",
  "device_id": "dev001",
  "status": "online",
  "timestamp": 1779177600123
}
```

遗嘱消息 topic：

```text
device/{product_type}/{device_id}/status/online
```

遗嘱消息 QoS：

```text
1
```

遗嘱 payload：

```json
{
  "mqtt_msg_id": "1779177600123-001-dev001",
  "product_type": "anti_bullying",
  "device_id": "dev001",
  "status": "offline",
  "timestamp": 0
}
```

后端处理：

```text
online:
    Redis 设置 device:online:{device_id} = online
    TTL 使用 campus.cache.device-online-ttl-seconds
    更新 device.last_online_time

offline:
    删除或更新 Redis 在线状态
    WebSocket 推送设备离线状态
```

---

### 8.8 WiFi 配置修改

设备订阅：

```text
device/{product_type}/{device_id}/config/wifi/set
```

说明：平台下发 WiFi 修改指令，修改指定配置号。

Payload：

```json
{
  "mqtt_msg_id": "1779177600123-001-dev001",
  "config_id": 1,
  "wifi_name": "12345",
  "wifi_password": "123456"
}
```

设备发布回复：

```text
device/{product_type}/{device_id}/config/wifi/set/reply
```

Payload：

```json
{
  "mqtt_msg_id": "1779177600123-001-dev001",
  "config_id": 1,
  "success": true
}
```

说明：

```text
1. wifi_password 只允许下发，不允许设备回传。
2. 后端不长期保存 wifi_password。
3. mqtt_msg_id 用于请求与回复关联。
```

---

### 8.9 WiFi 配置列表上报

设备发布：

```text
device/{product_type}/{device_id}/config/wifi/list
```

说明：设备返回已有 WiFi 配置。

Payload：

```json
{
  "mqtt_msg_id": "1779177600123-001-dev001",
  "max_size": 5,
  "id_using": 1,
  "config": [
    {
      "config_id": 1,
      "wifi_name": "12345"
    },
    {
      "config_id": 2,
      "wifi_name": "12345"
    }
  ]
}
```

后端处理：

```text
1. 不接收 wifi_password。
2. 根据 device_id + config_id upsert device_config_wifi。
3. 更新 set_time。
```

---

## 9. HTTP 接口设计

统一前缀：

```text
/api/v1
```

后台配置前缀：

```text
/backend/v1
```

---

## 9.1 Auth 用户认证接口

### 9.1.1 小程序登录

```http
POST /api/v1/auth/wx/login
Content-Type: application/json
```

请求：

```json
{
  "code": "wx_login_code"
}
```

说明：

```text
code 来自 wx.login。
前端不得直接传 openid。
后端用 code 请求微信服务器换取 openid/session_key。
后端保存 openid_hash。
```

成功返回：

```json
{
  "code": 0,
  "message": "登录成功",
  "data": {
    "access_token": "jwt_access_token",
    "refresh_token": "jwt_refresh_token",
    "token_type": "Bearer",
    "expires_in": 7200,
    "user_info": {
      "user_id": "usr_1433223",
      "nickname": "小灰",
      "avatar_url": "https://xxx.com/avatar/1.png",
      "phone": "15511760860",
      "email": "2923427847@qq.com",
      "created_at": "2026-05-22 12:00:00",
      "role": "USER",
      "is_new_user": false
    }
  }
}
```

失败返回：

```json
{
  "code": 1,
  "message": "登录失败",
  "data": null
}
```

---

### 9.1.2 刷新 Access Token

```http
POST /api/v1/auth/refresh
Content-Type: application/json
```

请求：

```json
{
  "refresh_token": "jwt_refresh_token"
}
```

成功返回：

```json
{
  "code": 0,
  "message": "刷新成功",
  "data": {
    "access_token": "new_jwt_access_token",
    "token_type": "Bearer",
    "expires_in": 7200
  }
}
```

失败返回：

```json
{
  "code": 401,
  "message": "刷新失败，请重新登录",
  "data": null
}
```

---

### 9.1.3 小程序登出

```http
POST /api/v1/auth/wx/logout
Authorization: Bearer access_token
Content-Type: application/json
```

请求：

```json
{}
```

成功返回：

```json
{
  "code": 0,
  "message": "退出登录成功",
  "data": null
}
```

说明：

```text
初版 logout 可以不传 refresh_token。
前端清空本地 access_token 和 refresh_token。
后端可选择将当前 access_token 加入 Redis 黑名单。
```

---

## 9.2 Users 用户接口

### 9.2.1 获取个人信息

```http
GET /api/v1/users/me
Authorization: Bearer access_token
```

成功返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "user_id": "usr_1",
    "nickname": "微信用户",
    "avatar_url": "https://xxx.com/avatar/user_1.jpg",
    "phone": null,
    "email": null,
    "role": "USER",
    "created_at": "2026-05-22 12:00:00"
  }
}
```

---

### 9.2.2 修改个人信息

```http
PUT /api/v1/users/me
Authorization: Bearer access_token
Content-Type: application/json
```

请求：

```json
{
  "nickname": "新的昵称",
  "phone": "13800138000",
  "email": "test@example.com"
}
```

成功返回：

```json
{
  "code": 0,
  "message": "修改成功",
  "data": {
    "user_id": "usr_1",
    "nickname": "新的昵称",
    "avatar_url": "https://xxx.com/avatar/user_1.jpg",
    "phone": "13800138000",
    "email": "test@example.com",
    "role": "USER",
    "created_at": "2026-05-22 12:00:00"
  }
}
```

---

### 9.2.3 上传个人头像

```http
POST /api/v1/users/me/avatar
Authorization: Bearer access_token
Content-Type: multipart/form-data
```

请求：

```text
file: 头像图片
```

成功返回：

```json
{
  "code": 0,
  "message": "头像上传成功",
  "data": {
    "avatar_url": "https://xxx.com/avatar/user_1.jpg"
  }
}
```

头像规则：

```text
存储：MinIO
上传方式：后端中转
大小限制：默认 2MB，可配置
格式：jpg / jpeg / png / webp
命名：avatar/user_{user_id}.{ext}
```

---

## 9.3 Devices 设备接口

### 9.3.1 展示设备列表

```http
GET /api/v1/devices
Authorization: Bearer access_token
```

Query 参数：

```text
page    可选，默认 1
size    可选，默认 10
```

成功返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [
      {
        "device_id": "dev001",
        "product_type": "anti_bullying",
        "device_name": "宿舍门口",
        "location": "3号楼",
        "note": "重点区域",
        "online_status": "ONLINE",
        "last_online_time": "2026-05-23 12:30:00",
        "bind_time": "2026-05-22 10:00:00"
      }
    ],
    "total": 1,
    "page": 1,
    "size": 10
  }
}
```

说明：

```text
online_status 从 Redis 读取。
last_online_time 从 device 表读取。
device_name/location/note 从 user_device_bind 表读取。
```

---

### 9.3.2 绑定设备

```http
POST /api/v1/devices/bind
Authorization: Bearer access_token
Content-Type: application/json
```

请求：

```json
{
  "device_id": "dev001",
  "bind_code": "123456",
  "device_name": "宿舍门口"
}
```

成功返回：

```json
{
  "code": 0,
  "message": "绑定成功",
  "data": {
    "device_id": "dev001",
    "device_name": "宿舍门口",
    "product_type": "anti_bullying",
    "bind_time": "2026-05-23 12:30:00"
  }
}
```

后端逻辑：

```text
1. 从 JWT 解析当前 user。
2. 根据 device_id 查询 device。
3. 从 Redis 查询 device:bind_code:{device_id}。
4. 校验 bind_code 是否正确且未过期。
5. 插入 user_device_bind。
6. 若已绑定，返回业务失败或幂等返回已绑定。
```

---

### 9.3.3 按条件查找设备

```http
GET /api/v1/devices/search
Authorization: Bearer access_token
```

Query 参数：

```text
keyword         可选，搜索 device_id/device_name/location
online_status   可选，ONLINE/OFFLINE
page            可选，默认 1
size            可选，默认 10
```

成功返回：同设备列表分页结构。

---

### 9.3.4 更新某个设备的信息

```http
PUT /api/v1/devices/{device_id}/info
Authorization: Bearer access_token
Content-Type: application/json
```

请求：

```json
{
  "device_name": "宿舍门口",
  "location": "3号楼",
  "note": "重点区域"
}
```

成功返回：

```json
{
  "code": 0,
  "message": "保存成功",
  "data": {
    "device_id": "dev001",
    "device_name": "宿舍门口",
    "location": "3号楼",
    "note": "重点区域"
  }
}
```

说明：

```text
只更新 user_device_bind 中当前用户对该设备的备注信息。
不修改 device 表的设备身份信息。
```

---

### 9.3.5 删除某个设备的绑定

```http
DELETE /api/v1/devices/{device_id}/binding
Authorization: Bearer access_token
```

成功返回：

```json
{
  "code": 0,
  "message": "解绑成功",
  "data": null
}
```

说明：

```text
初版可以物理删除 user_device_bind。
后期若需要审计，可扩展 bind_status 做软删除。
```

---

## 9.4 Events 事件接口

### 9.4.1 补拉未送达前端的事件

```http
GET /api/v1/events/unpulled
Authorization: Bearer access_token
```

说明：

```text
补拉当前用户所有已绑定设备中 push_status = PENDING 的事件。
返回成功后，将这些事件标记为 PUSHED。
这是非实时补拉接口，不是 WebSocket 实时推送。
```

成功返回：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "event_id": "evt_1001",
      "device_id": "dev001",
      "event_type": "voice_alarm",
      "alarm_info": "打架",
      "file_status": "SUCCESS",
      "file_url": "",
      "push_status": "PUSHED",
      "read_status": "UNREAD",
      "event_time": "2026-05-23 12:30:00"
    }
  ]
}
```

说明：

```text
file_url 可以为空字符串。
如果前端需要播放语音，再调用 refresh-url 获取临时访问链接。
```

---

### 9.4.2 确认事件已读

```http
PUT /api/v1/events/{event_id}/read
Authorization: Bearer access_token
```

请求：

```json
{}
```

成功返回：

```json
{
  "code": 0,
  "message": "success",
  "data": null
}
```

后端逻辑：

```text
1. 从 JWT 获取当前用户。
2. 根据 event_id 查询事件。
3. 校验事件所属设备是否绑定当前用户。
4. 更新 read_status = READ。
```

---

### 9.4.3 条件查询事件

```http
GET /api/v1/events/search
Authorization: Bearer access_token
```

说明：

```text
查找当前用户已绑定设备的事件。
device_id 可选，不传则查询当前用户全部绑定设备。
```

Query 参数：

```text
device_id      可选，设备对外ID，例如 dev001
page           可选，默认 1
size           可选，默认 10
event_type     可选，事件类型
file_status    可选，UPLOADING/SUCCESS/FAILED
push_status    可选，PENDING/PUSHED/FAILED
read_status    可选，UNREAD/READ
start_time     可选，格式 yyyy-MM-dd HH:mm:ss
end_time       可选，格式 yyyy-MM-dd HH:mm:ss
keyword        可选，查询 alarm_info
```

示例：

```http
GET /api/v1/events/search?device_id=dev001&page=1&size=10&read_status=UNREAD&start_time=2026-05-01 00:00:00&end_time=2026-05-23 23:59:59
```

成功返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [
      {
        "event_id": "evt_1001",
        "device_id": "dev001",
        "event_type": "voice_alarm",
        "alarm_info": "打架",
        "file_status": "SUCCESS",
        "file_url": "",
        "push_status": "PUSHED",
        "read_status": "UNREAD",
        "event_time": "2026-05-23 12:30:00"
      }
    ],
    "total": 1,
    "page": 1,
    "size": 10
  }
}
```

---

### 9.4.4 刷新事件语音访问链接

```http
POST /api/v1/events/{event_id}/refresh-url
Authorization: Bearer access_token
Content-Type: application/json
```

请求：

```json
{
  "expire_seconds": 3600
}
```

成功返回：

```json
{
  "code": 0,
  "message": "刷新成功",
  "data": {
    "event_id": "evt_1001",
    "file_url": "https://xxx.com/temp-url",
    "expire_seconds": 3600,
    "expire_at": "2026-05-23 13:30:00"
  }
}
```

后端逻辑：

```text
1. 校验当前用户是否有权限访问该事件。
2. 检查 event.file_key 是否存在。
3. 使用 MinIO 根据 file_key 生成临时访问 URL。
4. 返回 file_url。
```

---

## 10. WebSocket 设计

### 10.1 设计原则

WebSocket 不采用“一个设备一个连接”的方式。

原因：

```text
如果一个用户绑定多个设备，按 /ws/v1/events/{device_id}/new 建立连接，
会导致一个用户需要同时维护多个 WebSocket 连接，不利于小程序端、Web端和后端稳定扩展。
```

最终采用：

```text
一个客户端用户只建立一个 WebSocket 连接。
连接建立后，由客户端发送订阅消息，订阅一个或多个设备。
```

这样可以同时支持：

```text
1. 一个用户绑定多个设备
2. 小程序端统一接收事件推送
3. Web端复用同一套实时通信协议
4. 后端统一管理用户连接
5. 后期扩展设备状态、系统通知、后台消息
```

---

### 10.2 统一连接地址

连接地址：

```text
/ws/v1/client
```

认证方式：

```text
优先使用 Header：
Authorization: Bearer access_token

若小程序环境不方便设置 Header，可使用 query 参数：
/ws/v1/client?token=access_token
```

说明：

```text
1. WebSocket 建连时必须携带 access_token。
2. 后端解析 token 得到当前 user_id。
3. 后端建立 user_id 与 WebSocketSession 的映射。
4. 连接断开时清理 session 映射和订阅关系。
```

---

### 10.3 客户端订阅设备事件

连接建立成功后，小程序或 Web 端发送订阅消息。

客户端发送：

```json
{
  "type": "SUBSCRIBE_EVENTS",
  "device_ids": ["dev001", "dev002", "dev003"]
}
```

后端处理：

```text
1. 从 WebSocket 连接上下文中获取当前 user_id。
2. 校验 device_ids 是否全部属于当前用户。
3. 只允许订阅当前用户已绑定的设备。
4. 记录 user_id -> device_ids 的订阅关系。
5. 可同时建立 device_id -> session_id 的反向索引，方便按设备推送。
```

订阅成功回复：

```json
{
  "type": "SUBSCRIBE_EVENTS_ACK",
  "success": true,
  "data": {
    "device_ids": ["dev001", "dev002", "dev003"]
  }
}
```

订阅失败回复：

```json
{
  "type": "SUBSCRIBE_EVENTS_ACK",
  "success": false,
  "message": "存在无权限订阅的设备"
}
```

---

### 10.4 客户端取消订阅设备事件

客户端发送：

```json
{
  "type": "UNSUBSCRIBE_EVENTS",
  "device_ids": ["dev001"]
}
```

后端处理：

```text
1. 从当前连接订阅关系中移除指定 device_id。
2. 如果该连接没有任何订阅设备，连接可继续保持，用于接收系统通知或后续重新订阅。
```

服务端回复：

```json
{
  "type": "UNSUBSCRIBE_EVENTS_ACK",
  "success": true,
  "data": {
    "device_ids": ["dev001"]
  }
}
```

---

### 10.5 新事件实时推送

服务端推送 payload：

```json
{
  "type": "NEW_EVENT",
  "data": {
    "event_id": "evt_1001",
    "device_id": "dev001",
    "event_type": "voice_alarm",
    "alarm_info": "打架",
    "file_status": "SUCCESS",
    "file_url": "",
    "push_status": "PUSHED",
    "read_status": "UNREAD",
    "event_time": "2026-05-23 12:30:00"
  }
}
```

说明：

```text
1. 实时推送中 file_url 可以为空。
2. 前端需要播放文件时再调用 POST /api/v1/events/{event_id}/refresh-url。
3. 事件成功推送给至少一个在线客户端后，可以将 push_status 更新为 PUSHED。
4. 如果用户不在线或推送失败，保持 push_status = PENDING，等待 /api/v1/events/unpulled 补拉。
```

---

### 10.6 设备在线状态推送

设备在线状态不单独建立 WebSocket 连接，也通过统一连接推送。

服务端推送 payload：

```json
{
  "type": "DEVICE_STATUS",
  "data": {
    "device_id": "dev001",
    "online_status": "ONLINE",
    "last_online_time": "2026-05-23 12:30:00"
  }
}
```

说明：

```text
1. 后端收到设备上线、离线、心跳超时等状态变化后，推送给已绑定该设备的在线用户。
2. 小程序端根据 device_id 更新设备列表中的在线状态。
3. 在线状态最终以 Redis 中的 device:online:{device_id} 为准。
```

---

### 10.7 心跳与保活

客户端可以定时发送心跳：

```json
{
  "type": "PING",
  "timestamp": 1779177600123
}
```

服务端回复：

```json
{
  "type": "PONG",
  "timestamp": 1779177600123
}
```

要求：

```text
1. 小程序端应实现断线重连。
2. 重连成功后必须重新发送 SUBSCRIBE_EVENTS。
3. 重连频率必须限制，避免网络异常时无限高频重连。
4. 后端应清理长期失效的 session。
```

---

### 10.8 后端连接管理建议

后端建议维护以下结构：

```text
user_id -> WebSocketSession集合
session_id -> user_id
session_id -> device_id集合
device_id -> session_id集合
```

说明：

```text
1. 一个用户可能同时在小程序和 Web 端登录，所以 user_id 可对应多个 session。
2. 一个 session 可订阅多个 device_id。
3. 一个 device_id 可被多个用户或多个客户端订阅。
4. 所有订阅关系应在连接关闭时自动清理。
```

---

### 10.9 与 HTTP 补拉接口的关系

WebSocket 只负责实时通知，HTTP 负责可靠补偿。

保留补拉接口：

```http
GET /api/v1/events/unpulled
Authorization: Bearer access_token
```

职责划分：

```text
WebSocket:
    实时推送新事件、设备状态变化。

HTTP:
    查询历史事件、补拉未送达事件、刷新文件访问 URL、确认已读。

Redis:
    保存在线状态、连接映射、订阅关系、去重标记。

MySQL:
    保存最终事件记录、绑定关系、用户信息、设备信息。
```

结论：

```text
不要一个设备一个 WebSocket。
统一使用 /ws/v1/client。
一个客户端一个连接，连接后订阅多个设备。
```


## 11. 后台 Web 配置入口

后台路径前缀：

```text
/backend/v1
```

后台只允许 ADMIN 角色访问。

### 11.1 后台功能范围

后台 Web 入口至少支持查看或修改：

```text
1. Redis TTL 配置
2. MQTT/EMQX 连接信息
3. MinIO 连接信息
4. 对象存储 key 生成表达式
5. 上传 URL 有效期
6. 访问 URL 有效期
7. 设备在线 TTL
8. MQTT 去重 TTL
9. 绑定码 TTL
10. 系统基础信息
```

### 11.2 后台配置接口建议

```http
GET /backend/v1/config
Authorization: Bearer admin_access_token
```

返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "cache": {
      "mqtt_dedup_ttl_seconds": 86400,
      "device_online_ttl_seconds": 90,
      "bind_code_ttl_seconds": 60,
      "access_url_ttl_seconds": 3600
    },
    "storage": {
      "bucket": "campus",
      "object_key_pattern": "{product_type}/audio/{device_id}/{yyyy}/{MM}/{dd}/{event_id}.{ext}",
      "upload_url_expire_seconds": 300,
      "access_url_expire_seconds": 3600
    },
    "mqtt": {
      "broker_url": "tcp://127.0.0.1:1883",
      "client_id": "campus-server",
      "qos": 1
    }
  }
}
```

```http
PUT /backend/v1/config
Authorization: Bearer admin_access_token
Content-Type: application/json
```

请求：

```json
{
  "cache": {
    "mqtt_dedup_ttl_seconds": 86400,
    "device_online_ttl_seconds": 90,
    "bind_code_ttl_seconds": 60,
    "access_url_ttl_seconds": 3600
  },
  "storage": {
    "object_key_pattern": "{product_type}/audio/{device_id}/{yyyy}/{MM}/{dd}/{event_id}.{ext}",
    "upload_url_expire_seconds": 300,
    "access_url_expire_seconds": 3600
  }
}
```

安全要求：

```text
1. 不允许后台直接返回数据库密码。
2. 不允许后台直接返回 MinIO secretKey。
3. 不允许后台直接返回 JWT secret。
4. 敏感值只允许显示脱敏信息。
```

---

## 12. application.yml 配置要求

配置必须集中化，不得把 TTL、表达式、服务地址写死在代码里。

示例：

```yaml
server:
  port: 8080
  servlet:
    encoding:
      charset: UTF-8
      force: true
      enabled: true

spring:
  application:
    name: campus-server

  jackson:
    property-naming-strategy: SNAKE_CASE
    time-zone: Asia/Shanghai
    date-format: yyyy-MM-dd HH:mm:ss

  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/campus?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: root

  data:
    redis:
      host: localhost
      port: 6379
      password:
      database: 0

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      id-type: auto

campus:
  security:
    jwt:
      access-token-expire-seconds: 7200
      refresh-token-expire-seconds: 604800
      secret: "please-change-this-secret"

  wx:
    miniapp:
      app-id: "your-app-id"
      app-secret: "your-app-secret"

  mqtt:
    broker-url: "tcp://127.0.0.1:1883"
    client-id: "campus-server"
    username: "server"
    password: "server-password"
    qos: 1
    completion-timeout-ms: 5000

  minio:
    endpoint: "http://127.0.0.1:9000"
    access-key: "minioadmin"
    secret-key: "minioadmin"
    bucket: "campus"
    public-endpoint: "http://127.0.0.1:9000"

  cache:
    mqtt-dedup-ttl-seconds: 86400
    device-online-ttl-seconds: 90
    bind-code-ttl-seconds: 60
    access-url-ttl-seconds: 3600
    upload-url-ttl-seconds: 300
    jwt-blacklist-ttl-seconds: 7200

  storage:
    object-key-pattern: "{product_type}/audio/{device_id}/{yyyy}/{MM}/{dd}/{event_id}.{ext}"
    avatar-key-pattern: "avatar/{user_id}.{ext}"
    avatar-max-size-mb: 2

  event:
    default-page-size: 10
    max-page-size: 100
```

---

## 13. 小程序页面设计

底部 Tab 共 5 个：

```text
事件
设备
主页
状态
个人
```

其中主页位于中间，使用蓝色球形按钮样式。  
整体风格：

```text
白色
灰色
简约
有层次感
点击组件有轻微动态效果
符合微信小程序使用习惯
```

### 13.1 事件页面

功能：

```text
1. 顶部筛选框
2. 顶部搜索框
3. 事件卡片列表
4. 分页
5. 点击事件查看详情
6. 详情弹窗中可确认已读
7. 详情弹窗中可播放语音
8. 详情弹窗中可删除或隐藏，初版可只做确认已读
```

接口：

```text
GET /api/v1/events/search
PUT /api/v1/events/{event_id}/read
POST /api/v1/events/{event_id}/refresh-url
```

### 13.2 设备页面

功能：

```text
1. 搜索框
2. 筛选框
3. 添加设备入口
4. 设备卡片列表
5. 显示在线状态
6. 分页
7. 点击设备查看详情
8. 修改 device_name/location/note
9. 删除绑定
```

接口：

```text
GET /api/v1/devices
GET /api/v1/devices/search
POST /api/v1/devices/bind
PUT /api/v1/devices/{device_id}/info
DELETE /api/v1/devices/{device_id}/binding
```

### 13.3 主页页面

功能：

```text
1. 展示 WebSocket 实时推送的新事件
2. 小程序上线后调用 unpulled 接口补拉未送达事件
3. 新事件显示在最上面
4. 可分页或滚动加载
```

接口：

```text
GET /api/v1/events/unpulled
WebSocket /ws/v1/events/{device_id}/new
```

### 13.4 状态页面

功能：

```text
1. 展示设备总数
2. 展示在线设备数
3. 展示今日事件数
4. 展示未读事件数
5. 后期可扩展图表
```

接口暂留：

```text
/api/v1/status/**
```

### 13.5 个人页面

功能：

```text
1. 显示个人信息
2. 修改昵称、邮箱、手机号
3. 上传头像
4. 退出登录
```

接口：

```text
GET /api/v1/users/me
PUT /api/v1/users/me
POST /api/v1/users/me/avatar
POST /api/v1/auth/wx/logout
```

---

## 14. 小程序项目结构建议

```text
campus-miniprogram
├─ app.js
├─ app.json
├─ app.wxss
│
├─ pages
│  ├─ home
│  │  ├─ index.js
│  │  ├─ index.wxml
│  │  ├─ index.wxss
│  │  └─ index.json
│  ├─ events
│  ├─ devices
│  ├─ status
│  └─ profile
│
├─ components
│  ├─ event-card
│  ├─ device-card
│  ├─ empty-state
│  ├─ loading-view
│  └─ popup-panel
│
├─ services
│  ├─ request.js
│  ├─ auth-api.js
│  ├─ user-api.js
│  ├─ device-api.js
│  ├─ event-api.js
│  └─ websocket.js
│
├─ utils
│  ├─ storage.js
│  ├─ time.js
│  └─ format.js
│
└─ config
   └─ env.js
```

小程序要求：

```text
1. 所有接口请求通过 services/request.js 统一封装。
2. access_token 自动加到 Authorization。
3. 遇到 401 自动调用 refresh 接口。
4. refresh 失败则跳转登录。
5. WebSocket 断开后允许自动重连，但要限制重连频率。
6. 不要在页面中直接写大量 wx.request。
```

---

## 15. 关键业务流程

### 15.1 微信登录流程

```text
小程序 wx.login 获取 code
↓
POST /api/v1/auth/wx/login
↓
后端用 code 换 openid/session_key
↓
openid 做 hash
↓
查询 user.openid_hash
↓
不存在则创建用户
↓
生成 access_token 和 refresh_token
↓
返回用户信息和 token
```

### 15.2 设备绑定流程

```text
用户在小程序输入 device_id 和 bind_code
↓
POST /api/v1/devices/bind
↓
后端从 JWT 获取当前用户
↓
查询 device 表确认设备存在
↓
从 Redis 获取 device:bind_code:{device_id}
↓
校验 bind_code
↓
写入 user_device_bind
↓
返回绑定成功
```

### 15.3 报警事件上报流程

```text
设备发布 alarm/post
↓
后端 MQTT Receiver 接收
↓
Router 分发为报警事件
↓
DedupService 使用 Redis 去重
↓
写入 Queue
↓
AlarmPostHandler 处理
↓
创建 event 记录
↓
生成 MinIO upload_url
↓
MQTT 发布 alarm/upload 给设备
↓
设备上传文件到 MinIO
↓
设备发布 alarm/confirm
↓
后端更新 file_status
↓
WebSocket 推送给小程序
↓
若未送达，用户上线后 /events/unpulled 补拉
```

### 15.4 事件补拉流程

```text
小程序启动或进入主页
↓
GET /api/v1/events/unpulled
↓
后端查询当前用户绑定设备的 push_status = PENDING 事件
↓
返回事件列表
↓
后端更新 push_status = PUSHED
↓
前端展示在主页
```

### 15.5 文件播放流程

```text
用户点击事件详情
↓
前端调用 POST /api/v1/events/{event_id}/refresh-url
↓
后端校验用户权限
↓
根据 event.file_key 生成 MinIO 临时访问 URL
↓
返回 file_url
↓
小程序播放语音
```

---

## 16. 企业级可靠性要求

### 16.1 去重要求

必须两层去重：

```text
Redis 去重：快速过滤重复 MQTT 消息
数据库唯一约束：最终兜底
```

唯一约束：

```text
event(device_table_id, mqtt_msg_id)
```

### 16.2 事务要求

以下操作必须使用事务：

```text
1. 创建事件记录 + 生成上传信息前后的状态处理
2. 设备绑定
3. 更新事件文件上传状态
4. 标记事件已读
5. 解绑设备
```

### 16.3 权限要求

所有涉及设备、事件的接口必须校验：

```text
当前用户是否绑定该设备
```

禁止只根据 event_id 或 device_id 直接查询返回。

### 16.4 日志要求

关键流程必须记录日志：

```text
用户登录
设备绑定
MQTT 消息接收
MQTT 去重命中
事件创建
上传 URL 生成
文件上传确认
WebSocket 推送
Token 刷新失败
权限校验失败
```

日志中不能输出：

```text
明文 device_secret
明文 openid
明文 JWT
明文 wifi_password
MinIO secret_key
```

### 16.5 异常处理要求

必须有全局异常处理：

```text
参数错误
认证失败
权限不足
资源不存在
业务冲突
数据库异常
Redis异常
MinIO异常
MQTT异常
```

统一转成 ApiResult 返回。

---

## 17. AI 实现硬性要求

Codex / AI 实现时必须遵守：

```text
1. 不要把所有代码写到一个 Controller。
2. 不要在 Controller 写业务逻辑。
3. 不要在页面里直接散落 wx.request。
4. 不要把 Redis Key 写死在业务代码各处，必须集中常量或配置。
5. 不要把 MinIO key 拼接散落各处，必须封装 StorageService。
6. 不要把 MQTT topic 拼接散落各处，必须封装 TopicBuilder。
7. 不要明文保存 openid、device_secret、wifi_password。
8. 不要返回 wifi_password。
9. 不要擅自修改 MQTT topic。
10. 不要破坏 JSON snake_case 规范。
11. 不要出现中文乱码。
12. 每个核心类必须写清楚注释。
```

---

## 18. 推荐生成的后端核心类

```text
common.result.ApiResult
common.result.PageResult
common.exception.BizException
common.exception.GlobalExceptionHandler

security.JwtTokenProvider
security.JwtAuthenticationFilter
security.LoginUser

module.auth.controller.AuthController
module.auth.service.AuthService
module.auth.dto.WxLoginRequest
module.auth.dto.RefreshTokenRequest
module.auth.vo.LoginVO

module.user.controller.UserController
module.user.service.UserService
module.user.entity.User
module.user.mapper.UserMapper
module.user.dto.UpdateUserRequest
module.user.vo.UserInfoVO

module.device.controller.DeviceController
module.device.service.DeviceService
module.device.entity.Device
module.device.entity.UserDeviceBind
module.device.mapper.DeviceMapper
module.device.mapper.UserDeviceBindMapper
module.device.dto.BindDeviceRequest
module.device.dto.UpdateDeviceInfoRequest
module.device.vo.DeviceVO

module.event.controller.EventController
module.event.service.EventService
module.event.entity.Event
module.event.mapper.EventMapper
module.event.dto.EventSearchRequest
module.event.dto.RefreshUrlRequest
module.event.vo.EventVO

module.storage.service.StorageService
module.storage.service.MinioStorageService
module.storage.dto.PresignedUploadInfo
module.storage.dto.PresignedAccessInfo

module.mqtt.receiver.MqttReceiver
module.mqtt.router.MqttRouter
module.mqtt.dedup.MqttDedupService
module.mqtt.queue.MqttMessageQueue
module.mqtt.handler.AlarmPostHandler
module.mqtt.handler.AlarmConfirmHandler
module.mqtt.handler.StatusOnlineHandler
module.mqtt.handler.WifiSetReplyHandler
module.mqtt.handler.WifiListHandler
module.mqtt.publisher.MqttPublisher
module.mqtt.topic.MqttTopicBuilder

module.websocket.handler.EventWebSocketHandler
module.websocket.handler.DeviceStatusWebSocketHandler
module.websocket.session.WebSocketSessionManager
```

---

## 19. 第一阶段开发顺序

建议 Codex 按以下顺序实现：

```text
1. 创建 Spring Boot 项目结构
2. 配置 UTF-8、MySQL、Redis、MyBatis-Plus、Jackson snake_case
3. 创建数据库 SQL
4. 实现统一返回 ApiResult
5. 实现全局异常处理
6. 实现 JWT 与 Spring Security
7. 实现微信登录 Auth 模块
8. 实现 User 模块
9. 实现 Device 绑定与查询模块
10. 实现 Event 查询、已读、补拉、刷新 URL 模块
11. 实现 MinIO StorageService
12. 实现 MQTT 基础接收、发布、topic 分发
13. 实现 MQTT 报警事件完整链路
14. 实现 WebSocket 推送
15. 实现小程序 request 封装
16. 实现小程序五个页面
17. 实现配置后台 Web 入口
18. 补充 README、接口说明、运行脚本
```

---

## 20. 暂缓实现但预留扩展

以下功能可预留结构，不必第一版完整实现：

```text
1. 多管理员后台复杂权限
2. 事件删除回收站
3. 文件转码
4. 语音内容识别
5. 统计图表复杂分析
6. 设备 OTA 升级
7. 多租户
8. 分布式 MQTT 消费集群
9. 消息补偿任务
10. 审计日志表
```

---

## 21. 最终交付物要求

AI 最终应交付：

```text
campus-server/                 Spring Boot 后端
campus-miniprogram/            微信小程序
campus-admin-web/              配置后台 Web 入口
docs/
  ├─ README.md
  ├─ 数据库设计.md
  ├─ HTTP接口文档.md
  ├─ MQTT协议文档.md
  ├─ Redis缓存设计.md
  ├─ MinIO文件存储设计.md
  └─ 部署说明.md
sql/
  └─ campus.sql
```

所有文件：

```text
UTF-8 编码
中文无乱码
命名清晰
注释充分
结构分层
方便二次开发
```

---

## 22. 总结

本项目的核心不是简单 CRUD，而是一个包含：

```text
设备接入
MQTT 消息去重
事件入库
对象存储
WebSocket 实时推送
HTTP 补拉
用户设备绑定
微信小程序展示
后台配置管理
```

的完整物联网软件系统。

因此实现时必须以企业级标准完成：

```text
分层清晰
配置集中
命名统一
接口稳定
协议稳定
缓存可控
文件可控
权限可控
日志可查
异常可控
方便维护
方便二次开发
```
