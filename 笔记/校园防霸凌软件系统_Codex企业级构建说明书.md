# 校园防霸凌软件系统 Codex 企业级构建说明书

> 文档编码：UTF-8  
> 文档语言：中文  
> 适用对象：Codex / AI 编程代理 / 后续企业二次开发人员  
> 配套文档：`校园防霸凌软件系统企业级开发说明书_websocket修正版.md`  
> 项目范围：Spring Boot 后端、微信小程序端、配置后台 Web 入口  
> 非本阶段范围：STM32/ESP8266/ASRPro/设备固件/硬件驱动实现  

---

## 0. 本文档定位

本文档不是普通需求文档，而是给 Codex 从 0 构建项目使用的**执行型构建说明书**。

它解决的问题是：

```text
1. Codex 应该先做什么，后做什么。
2. 每一阶段应该生成哪些文件。
3. 哪些设计不能擅自修改。
4. 哪些地方必须企业级分层。
5. 每一阶段如何自检。
6. 最终交付物应该是什么样。
```

Codex 必须先阅读并遵守配套总说明书，再阅读本文档。本文档优先规定“执行顺序、生成策略、验收标准”，配套总说明书优先规定“业务规则、接口协议、数据库、MQTT、缓存、文件存储”。

---

## 1. 给 Codex 的总原则

Codex 实现本项目时必须严格遵守以下原则。

### 1.1 稳定可靠优先

不要为了快速生成代码而牺牲结构。

禁止：

```text
1. 把大量业务逻辑写在 Controller。
2. 把 Redis Key、MQTT Topic、MinIO Key 到处硬编码。
3. 把权限校验散落在各个 Controller。
4. 把微信登录、JWT、用户创建写成不可维护的一坨代码。
5. 把小程序 wx.request 直接写在页面文件里。
6. 擅自修改 MQTT topic 和 payload。
7. 擅自把 JSON 字段改成 camelCase。
8. 明文保存 openid、device_secret、wifi_password。
```

必须：

```text
1. 后端清晰分层。
2. 核心类有中文注释。
3. 配置集中到 application.yml。
4. 错误统一处理。
5. 返回结构统一。
6. 权限校验统一。
7. 缓存 Key 集中管理。
8. MQTT topic 集中构建。
9. MinIO 操作集中封装。
10. 小程序请求统一封装。
```

### 1.2 不要过度发挥

本项目已经有明确草案。Codex 不要重新发明一套架构。

除非存在以下情况，否则不得大改：

```text
1. 明显安全问题。
2. 明显 JSON 语法错误。
3. 明显命名冲突。
4. 明显会导致无法实现。
5. 明显违反前后端交互一致性。
```

### 1.3 分阶段提交

Codex 不要一次性生成所有代码后再检查。

推荐按阶段完成：

```text
阶段 1：项目骨架
阶段 2：通用基础设施
阶段 3：数据库与实体
阶段 4：认证与用户
阶段 5：设备模块
阶段 6：事件模块
阶段 7：MinIO
阶段 8：MQTT
阶段 9：WebSocket
阶段 10：小程序
阶段 11：后台 Web
阶段 12：集成测试与文档
```

每阶段完成后必须自检，能编译则编译，能运行测试则运行测试。

---

## 2. 推荐仓库结构

建议一个总仓库下包含三个子项目：

```text
campus-anti-bullying/
├─ AGENTS.md
├─ README.md
├─ docs/
│  ├─ 校园防霸凌软件系统企业级开发说明书.md
│  ├─ Codex企业级构建说明书.md
│  ├─ HTTP接口文档.md
│  ├─ MQTT协议文档.md
│  ├─ 数据库设计.md
│  ├─ Redis缓存设计.md
│  ├─ MinIO文件存储设计.md
│  └─ 部署说明.md
│
├─ campus-server/
│  ├─ pom.xml
│  ├─ src/main/java/...
│  ├─ src/main/resources/...
│  └─ README.md
│
├─ campus-miniprogram/
│  ├─ app.js
│  ├─ app.json
│  ├─ app.wxss
│  ├─ pages/
│  ├─ components/
│  ├─ services/
│  ├─ utils/
│  └─ README.md
│
├─ campus-admin-web/
│  ├─ package.json
│  ├─ vite.config.js
│  ├─ src/
│  └─ README.md
│
├─ sql/
│  └─ campus.sql
│
└─ scripts/
   ├─ dev-start.md
   ├─ backend-check.md
   └─ env-example.md
```

说明：

```text
campus-server        Spring Boot 后端
campus-miniprogram   微信小程序
campus-admin-web     配置后台 Web 入口
sql                  数据库脚本
docs                 所有规范文档
scripts              运行、检查、部署辅助说明
```

---

## 3. AGENTS.md 建议内容

建议在仓库根目录创建 `AGENTS.md`。该文件给 Codex 提供全局约束。

```markdown
# AGENTS.md

## 项目定位

本项目是校园防霸凌物联网报警系统，包含 Spring Boot 后端、微信小程序、配置后台 Web 入口。

硬件端不由本仓库实现，只按照 MQTT 协议与后端交互。

## 最高优先级规则

1. 所有源码、配置、SQL、Markdown 必须使用 UTF-8。
2. 数据库字段使用 snake_case。
3. Java 字段使用 camelCase。
4. HTTP JSON 字段使用 snake_case。
5. MQTT payload 字段使用 snake_case。
6. 不得擅自修改 MQTT topic 与 payload，除非修复明显错误。
7. 不得明文保存 openid、device_secret、wifi_password。
8. 不得返回 wifi_password。
9. Controller 不得写复杂业务逻辑。
10. Redis Key、MQTT Topic、MinIO Key 必须集中封装。
11. 所有设备、事件接口必须校验当前用户是否绑定该设备。
12. WebSocket 使用一个客户端一个连接：/ws/v1/client，连接后订阅多个 device_id。

## 后端技术栈

- Java 17
- Spring Boot 3.x
- Spring Security + JWT
- MyBatis-Plus
- MySQL 8.x
- Redis
- MinIO
- EMQX/MQTT
- Spring WebSocket
- Maven

## 前端技术栈

微信小程序：原生小程序 JavaScript。
配置后台：Vue 3 + Vite + Element Plus。

## 必读文档

开始任何编码前，必须先阅读：

1. docs/校园防霸凌软件系统企业级开发说明书.md
2. docs/Codex企业级构建说明书.md

## 执行要求

每完成一个阶段，必须：

1. 简要说明完成了哪些文件。
2. 说明是否编译通过。
3. 说明是否存在未完成项。
4. 不要静默吞掉错误。
5. 遇到不确定处，优先遵守文档，不要自行扩展。
```

如果后续不同子项目需要差异化规则，可以在 `campus-server/AGENTS.md`、`campus-miniprogram/AGENTS.md` 中增加局部规则。

---

## 4. 第一次交给 Codex 的总提示词

建议第一次给 Codex 的任务不要直接说“把整个项目写完”。应该先让它读取文档、建立计划、生成骨架。

推荐提示词：

```text
请先阅读仓库根目录 AGENTS.md、docs/校园防霸凌软件系统企业级开发说明书.md、docs/Codex企业级构建说明书.md。

本项目要求从 0 构建校园防霸凌软件系统，范围包括：
1. Spring Boot 后端 campus-server
2. 微信小程序 campus-miniprogram
3. 配置后台 Web 入口 campus-admin-web

硬件端不实现，硬件只通过 MQTT 协议与后端通信。

请不要立即生成所有业务代码。先完成以下任务：
1. 检查文档并总结你理解的系统边界。
2. 给出分阶段构建计划。
3. 创建推荐仓库目录结构。
4. 创建基础 README、docs 目录、sql 目录。
5. 生成后端、小程序、后台 Web 的空项目骨架。
6. 不要擅自修改 MQTT topic、数据库字段、HTTP JSON 命名规范。

完成后请列出已创建文件和下一阶段建议。
```

---

## 5. 阶段 1：项目骨架

### 5.1 目标

创建三个子项目骨架：

```text
campus-server
campus-miniprogram
campus-admin-web
```

### 5.2 后端骨架要求

目录：

```text
campus-server
├─ pom.xml
├─ README.md
└─ src/main
   ├─ java/com/campus/CampusApplication.java
   └─ resources
      ├─ application.yml
      ├─ application-dev.yml
      ├─ application-prod.yml
      ├─ mapper
      └─ sql
```

`pom.xml` 至少包含：

```text
spring-boot-starter-web
spring-boot-starter-security
spring-boot-starter-validation
spring-boot-starter-websocket
mybatis-plus-spring-boot3-starter
mysql-connector-j
spring-boot-starter-data-redis
minio
jjwt 或 java-jwt
mqtt client
lombok
springdoc-openapi 可选
```

要求：

```text
1. Java 17。
2. Spring Boot 3.x。
3. Maven 构建。
4. 初始项目可以启动。
5. application.yml 包含 UTF-8、Jackson snake_case、MySQL、Redis、JWT、MQTT、MinIO、缓存 TTL 配置骨架。
```

### 5.3 小程序骨架要求

目录：

```text
campus-miniprogram
├─ app.js
├─ app.json
├─ app.wxss
├─ project.config.json
├─ pages
│  ├─ home
│  ├─ events
│  ├─ devices
│  ├─ status
│  └─ profile
├─ components
├─ services
├─ utils
└─ config
```

要求：

```text
1. 五个页面：主页、事件、设备、状态、个人。
2. tabBar 配置完整。
3. services/request.js 统一请求封装。
4. services/websocket.js 统一 WebSocket 封装。
5. 不要在页面中直接散落 wx.request。
```

### 5.4 后台 Web 骨架要求

目录：

```text
campus-admin-web
├─ package.json
├─ vite.config.js
├─ index.html
└─ src
   ├─ main.js
   ├─ App.vue
   ├─ router
   ├─ services
   ├─ views
   └─ components
```

要求：

```text
1. Vue 3 + Vite + Element Plus。
2. 有登录占位页、配置管理页、系统信息页。
3. API 前缀 /backend/v1。
4. 敏感配置仅脱敏显示。
```

### 5.5 阶段 1 自检

Codex 必须确认：

```text
1. 项目目录是否完整。
2. 后端是否可 Maven 编译。
3. 小程序是否有基本页面结构。
4. 后台 Web 是否可 npm install 后启动。
5. 所有文件是否 UTF-8。
```

---

## 6. 阶段 2：后端通用基础设施

### 6.1 目标

构建企业级后端基础能力。

### 6.2 必须生成的包

```text
com.campus.common.result
com.campus.common.exception
com.campus.common.enums
com.campus.common.constants
com.campus.common.util
com.campus.config
com.campus.security
```

### 6.3 统一返回

生成：

```text
ApiResult<T>
PageResult<T>
```

`ApiResult` 字段：

```text
code
message
data
```

静态方法：

```text
success()
success(data)
success(message, data)
fail(message)
fail(code, message)
```

`PageResult` 字段：

```text
records
total
page
size
```

### 6.4 全局异常

生成：

```text
BizException
GlobalExceptionHandler
ErrorCode
```

必须处理：

```text
参数校验异常
认证失败
权限不足
业务异常
资源不存在
数据库异常
Redis异常
MinIO异常
MQTT异常
系统未知异常
```

### 6.5 配置类

生成：

```text
JacksonConfig
SecurityConfig
RedisConfig
MinioConfig
MqttConfig
WebSocketConfig
```

### 6.6 配置属性类

建议生成：

```text
CampusProperties
JwtProperties
CacheProperties
StorageProperties
MqttProperties
MinioProperties
WxMiniAppProperties
```

要求：

```text
1. TTL、对象 key 表达式、MQTT 配置、MinIO 配置、JWT 时间都从配置读取。
2. 不允许业务代码写死这些值。
```

### 6.7 阶段 2 自检

```text
1. ApiResult 是否全局统一。
2. Jackson 是否输出 snake_case。
3. 全局异常是否生效。
4. 配置属性是否能绑定 application.yml。
5. 后端是否能启动。
```

---

## 7. 阶段 3：数据库与实体

### 7.1 目标

生成数据库 SQL、实体类、Mapper、枚举。

### 7.2 SQL 文件

生成：

```text
sql/campus.sql
campus-server/src/main/resources/sql/campus.sql
```

表：

```text
device
user
user_device_bind
event
device_config_wifi
```

必须保留总说明书中的核心字段。

### 7.3 实体类

生成：

```text
Device
User
UserDeviceBind
Event
DeviceConfigWifi
```

注意：

```text
数据库字段 snake_case
Java 字段 camelCase
MyBatis-Plus 开启 map-underscore-to-camel-case
```

### 7.4 Mapper

生成：

```text
DeviceMapper
UserMapper
UserDeviceBindMapper
EventMapper
DeviceConfigWifiMapper
```

### 7.5 枚举

至少生成：

```text
RoleEnum: USER / ADMIN
FileStatusEnum: UPLOADING / SUCCESS / FAILED
PushStatusEnum: PENDING / PUSHED / FAILED
ReadStatusEnum: UNREAD / READ
OnlineStatusEnum: ONLINE / OFFLINE
```

### 7.6 注意事项

```text
1. event.device_table_id 对应 device.id。
2. user_device_bind.device_table_id 对应 device.id。
3. user_device_bind.user_table_id 对应 user.id。
4. 接口对外仍使用 device_id/user_id/event_id。
5. 不要把 device.device_id 和 device.id 混用。
```

### 7.7 阶段 3 自检

```text
1. SQL 是否可执行。
2. 唯一索引是否完整。
3. 外键说明是否清楚。
4. Entity 字段是否和 SQL 对齐。
5. Mapper 是否能正常注入。
```

---

## 8. 阶段 4：认证与用户模块

### 8.1 目标

实现微信小程序登录、JWT、刷新 token、登出、个人信息。

### 8.2 Auth 模块文件

```text
module.auth.controller.AuthController
module.auth.service.AuthService
module.auth.dto.WxLoginRequest
module.auth.dto.RefreshTokenRequest
module.auth.vo.LoginVO
module.auth.vo.TokenVO
```

### 8.3 Security 文件

```text
JwtTokenProvider
JwtAuthenticationFilter
LoginUser
SecurityContextUtil
```

### 8.4 微信登录逻辑

流程：

```text
1. 小程序传 code。
2. 后端请求微信接口换取 openid。
3. openid 做 hash。
4. 根据 openid_hash 查用户。
5. 不存在则创建用户。
6. 生成 user_id。
7. 生成 access_token 和 refresh_token。
8. 返回 token 和 user_info。
```

注意：

```text
1. 不保存明文 openid。
2. 不保存 wx_code。
3. 不把 session_key 返回给前端。
```

### 8.5 用户模块文件

```text
module.user.controller.UserController
module.user.service.UserService
module.user.entity.User
module.user.mapper.UserMapper
module.user.dto.UpdateUserRequest
module.user.vo.UserInfoVO
```

### 8.6 用户接口

实现：

```text
POST /api/v1/auth/wx/login
POST /api/v1/auth/refresh
POST /api/v1/auth/wx/logout
GET  /api/v1/users/me
PUT  /api/v1/users/me
POST /api/v1/users/me/avatar
```

头像上传可以先后端中转到 MinIO。

### 8.7 阶段 4 自检

```text
1. 登录接口 JSON 是否 snake_case。
2. token 是否能访问 /users/me。
3. 未登录是否返回 401。
4. 修改个人信息是否只修改当前用户。
5. 头像是否限制格式和大小。
```

---

## 9. 阶段 5：设备模块

### 9.1 目标

实现设备查询、绑定、搜索、修改备注、解绑。

### 9.2 文件

```text
module.device.controller.DeviceController
module.device.service.DeviceService
module.device.entity.Device
module.device.entity.UserDeviceBind
module.device.mapper.DeviceMapper
module.device.mapper.UserDeviceBindMapper
module.device.dto.BindDeviceRequest
module.device.dto.UpdateDeviceInfoRequest
module.device.dto.DeviceSearchRequest
module.device.vo.DeviceVO
```

### 9.3 接口

```text
GET    /api/v1/devices
POST   /api/v1/devices/bind
GET    /api/v1/devices/search
PUT    /api/v1/devices/{device_id}/info
DELETE /api/v1/devices/{device_id}/binding
```

### 9.4 绑定逻辑

```text
1. 从 JWT 获取当前 user.id。
2. 根据 device_id 查 device。
3. 从 Redis 查 device:bind_code:{device_id}。
4. 校验 bind_code。
5. 插入 user_device_bind。
6. 若已绑定，返回明确业务提示。
```

### 9.5 查询设备列表

```text
1. 从 JWT 获取 user.id。
2. 查询 user_device_bind。
3. 关联 device。
4. 从 Redis 查询 online_status。
5. 返回 device_name/location/note/last_online_time/online_status。
```

### 9.6 权限要求

```text
更新设备备注、解绑设备时必须校验该设备已绑定当前用户。
```

### 9.7 阶段 5 自检

```text
1. 未绑定设备不能修改。
2. 未绑定设备不能解绑。
3. 查询只返回当前用户绑定设备。
4. online_status 从 Redis 或默认 OFFLINE 返回。
```

---

## 10. 阶段 6：事件模块

### 10.1 目标

实现事件补拉、查询、已读、刷新文件访问链接。

### 10.2 文件

```text
module.event.controller.EventController
module.event.service.EventService
module.event.entity.Event
module.event.mapper.EventMapper
module.event.dto.EventSearchRequest
module.event.dto.RefreshUrlRequest
module.event.vo.EventVO
```

### 10.3 接口

```text
GET  /api/v1/events/unpulled
PUT  /api/v1/events/{event_id}/read
GET  /api/v1/events/search
POST /api/v1/events/{event_id}/refresh-url
```

### 10.4 补拉逻辑

```text
1. 从 JWT 获取当前用户。
2. 查询当前用户绑定的所有设备。
3. 查询这些设备下 push_status = PENDING 的事件。
4. 返回事件列表。
5. 将这些事件更新为 PUSHED。
```

注意：

```text
该接口是 HTTP 补拉，不是 WebSocket 实时推送。
```

### 10.5 查询逻辑

```text
1. device_id 可选。
2. 如果传 device_id，先校验当前用户是否绑定。
3. 如果不传，查当前用户所有绑定设备。
4. 支持 event_type/file_status/push_status/read_status/start_time/end_time/keyword/page/size。
5. 按 event_time 倒序。
```

### 10.6 已读逻辑

```text
1. 根据 event_id 查事件。
2. 校验事件所属设备是否绑定当前用户。
3. 更新 read_status = READ。
```

### 10.7 刷新 URL 逻辑

```text
1. 根据 event_id 查事件。
2. 校验权限。
3. 检查 file_key 不为空。
4. 使用 StorageService 生成临时访问 URL。
5. 返回 file_url、expire_seconds、expire_at。
```

### 10.8 阶段 6 自检

```text
1. 用户不能查未绑定设备事件。
2. 用户不能刷新无权限事件 URL。
3. file_url 默认可以为空。
4. 查询分页结构统一。
5. push_status/read_status 分工正确。
```

---

## 11. 阶段 7：MinIO 存储模块

### 11.1 目标

封装对象存储操作，不允许业务模块直接操作 MinIO Client。

### 11.2 文件

```text
module.storage.service.StorageService
module.storage.service.MinioStorageService
module.storage.dto.PresignedUploadInfo
module.storage.dto.PresignedAccessInfo
infrastructure.minio.MinioClientFactory
```

### 11.3 StorageService 方法

```java
PresignedUploadInfo generateUploadUrl(UploadUrlCommand command);
PresignedAccessInfo generateAccessUrl(String objectKey, Integer expireSeconds);
String buildObjectKey(ObjectKeyContext context);
String buildAvatarKey(String userId, String ext);
void ensureBucketExists();
```

### 11.4 对象 Key 规则

从配置读取：

```text
campus.storage.object-key-pattern
campus.storage.avatar-key-pattern
```

禁止在业务里写死：

```text
anti_bullying/audio/dev001/...
```

### 11.5 阶段 7 自检

```text
1. 业务层是否只依赖 StorageService。
2. MinIO endpoint/accessKey/secretKey 是否来自配置。
3. 返回给前端的是临时访问 URL，不是 file_key。
4. 数据库保存 file_key，不长期保存完整 URL。
```

---

## 12. 阶段 8：MQTT 模块

### 12.1 目标

实现 EMQX/MQTT 接收、分发、去重、业务处理、发布。

### 12.2 模块结构

```text
module.mqtt.receiver
module.mqtt.router
module.mqtt.dedup
module.mqtt.queue
module.mqtt.handler
module.mqtt.publisher
module.mqtt.dto
module.mqtt.topic
```

### 12.3 文件

```text
MqttReceiver
MqttRouter
MqttDedupService
MqttMessageQueue
MqttPublisher
MqttTopicBuilder
AlarmPostHandler
AlarmConfirmHandler
StatusOnlineHandler
WifiSetReplyHandler
WifiListHandler
```

### 12.4 必须支持的 topic

设备发布：

```text
device/{product_type}/{device_id}/alarm/post
device/{product_type}/{device_id}/alarm/confirm
device/{product_type}/{device_id}/status/online
device/{product_type}/{device_id}/config/wifi/set/reply
device/{product_type}/{device_id}/config/wifi/list
```

设备订阅：

```text
device/{product_type}/{device_id}/alarm/upload
device/{product_type}/{device_id}/config/wifi/set
```

### 12.5 TopicBuilder 要求

必须集中构建 topic：

```java
String alarmUpload(String productType, String deviceId);
String wifiSet(String productType, String deviceId);
```

禁止业务类到处字符串拼接。

### 12.6 去重逻辑

必须两层去重：

```text
Redis SETNX mqtt:dedup:{device_id}:{mqtt_msg_id}
数据库 unique(device_table_id, mqtt_msg_id)
```

### 12.7 alarm/post 处理

```text
1. 校验 topic 与 payload 一致。
2. 查设备。
3. Redis 去重。
4. 生成 event_id。
5. 生成 object_key。
6. 创建 event，file_status = UPLOADING。
7. 生成 upload_url。
8. 发布 alarm/upload 给设备。
```

### 12.8 alarm/confirm 处理

```text
1. 校验设备。
2. 根据 event_id 查事件。
3. 校验 object_key。
4. 更新 file_status。
5. 推送 WebSocket。
6. 推送失败则 push_status 保持 PENDING。
```

### 12.9 status/online 处理

```text
online:
    Redis device:online:{device_id} = ONLINE，设置 TTL
    更新 last_online_time
    WebSocket 推送 DEVICE_STATUS_CHANGE

offline:
    Redis 标记 OFFLINE 或删除 online key
    WebSocket 推送 DEVICE_STATUS_CHANGE
```

### 12.10 WiFi 处理

```text
config/wifi/set/reply:
    记录日志，后期可扩展命令状态表

config/wifi/list:
    upsert device_config_wifi
    不接收 wifi_password
```

### 12.11 阶段 8 自检

```text
1. MQTT topic 是否未被擅自大改。
2. payload 字段是否 snake_case。
3. mqtt_msg_id 是否用于去重。
4. 设备身份是否校验。
5. 密码和 secret 是否不输出日志。
```

---

## 13. 阶段 9：WebSocket 模块

### 13.1 目标

实现一个客户端一个 WebSocket 连接，支持多设备订阅，统一推送事件和设备状态。

### 13.2 连接地址

```text
/ws/v1/client
```

认证方式：

```text
优先 Header：Authorization: Bearer access_token
小程序兼容：/ws/v1/client?token=access_token
```

### 13.3 文件

```text
module.websocket.handler.ClientWebSocketHandler
module.websocket.session.WebSocketSessionManager
module.websocket.dto.WsClientMessage
module.websocket.dto.WsServerMessage
module.websocket.dto.SubscribeEventsMessage
module.websocket.dto.EventPushMessage
module.websocket.dto.DeviceStatusPushMessage
```

旧设计中的以下类如已生成，应改造或删除：

```text
EventWebSocketHandler
DeviceStatusWebSocketHandler
```

### 13.4 客户端订阅消息

小程序连接成功后发送：

```json
{
  "type": "SUBSCRIBE_EVENTS",
  "device_ids": ["dev001", "dev002"]
}
```

后端处理：

```text
1. 根据 WebSocket token 获取 user。
2. 校验 device_ids 是否全部属于当前用户。
3. 保存 user_id -> session 映射。
4. 保存 session -> device_ids 订阅关系。
```

### 13.5 服务端推送新事件

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

### 13.6 服务端推送设备状态

```json
{
  "type": "DEVICE_STATUS_CHANGE",
  "data": {
    "device_id": "dev001",
    "online_status": "ONLINE",
    "last_online_time": "2026-05-23 12:30:00"
  }
}
```

### 13.7 心跳

客户端发送：

```json
{
  "type": "PING"
}
```

服务端返回：

```json
{
  "type": "PONG",
  "server_time": "2026-05-23 12:30:00"
}
```

### 13.8 断线重连

小程序断线后：

```text
1. 延迟重连。
2. 重连成功后重新发送 SUBSCRIBE_EVENTS。
3. 调用 GET /api/v1/events/unpulled 补拉遗漏事件。
```

### 13.9 阶段 9 自检

```text
1. 是否只有一个 WebSocket 地址 /ws/v1/client。
2. 是否支持一个用户订阅多个设备。
3. 是否校验设备绑定权限。
4. 是否能同时推送事件和设备状态。
5. 断线重连后是否能重新订阅。
```

---

## 14. 阶段 10：小程序端

### 14.1 目标

实现五个页面、请求封装、token 管理、WebSocket 管理。

### 14.2 目录

```text
campus-miniprogram
├─ pages
│  ├─ home
│  ├─ events
│  ├─ devices
│  ├─ status
│  └─ profile
├─ components
│  ├─ event-card
│  ├─ device-card
│  ├─ empty-state
│  ├─ loading-view
│  └─ popup-panel
├─ services
│  ├─ request.js
│  ├─ auth-api.js
│  ├─ user-api.js
│  ├─ device-api.js
│  ├─ event-api.js
│  └─ websocket.js
├─ utils
│  ├─ storage.js
│  ├─ time.js
│  └─ format.js
└─ config
   └─ env.js
```

### 14.3 request.js 要求

必须实现：

```text
1. baseUrl 配置。
2. 自动读取 access_token。
3. 自动加 Authorization。
4. 统一处理 code != 0。
5. 遇到 401 自动 refresh。
6. refresh 失败跳转登录。
7. 防止多个请求同时 refresh 造成并发问题。
```

### 14.4 websocket.js 要求

必须实现：

```text
1. connect()
2. close()
3. subscribeEvents(deviceIds)
4. onMessage(type, callback)
5. sendPing()
6. reconnect()
7. 重连后重新订阅
8. 重连后调用 unpulled 补拉
```

连接地址：

```text
/ws/v1/client?token=access_token
```

### 14.5 页面功能

主页：

```text
1. 建立 WebSocket。
2. 获取已绑定设备列表。
3. 订阅所有设备。
4. 调用 events/unpulled。
5. 显示实时新事件。
```

事件页：

```text
1. 条件查询事件。
2. 分页。
3. 查看详情。
4. 标记已读。
5. 刷新 URL 播放语音。
```

设备页：

```text
1. 设备列表。
2. 搜索。
3. 绑定设备。
4. 修改备注。
5. 解绑。
```

状态页：

```text
1. 设备总数。
2. 在线设备数。
3. 今日事件数。
4. 未读事件数。
5. 初版可以使用前端计算或预留接口。
```

个人页：

```text
1. 获取个人信息。
2. 修改个人信息。
3. 上传头像。
4. 退出登录。
```

### 14.6 阶段 10 自检

```text
1. 页面中是否没有散落 wx.request。
2. token 是否统一管理。
3. 401 是否能 refresh。
4. WebSocket 是否单连接。
5. 多设备订阅是否正常。
6. UTF-8 是否正常显示中文。
```

---

## 15. 阶段 11：配置后台 Web

### 15.1 目标

实现一个轻量配置后台入口，不追求复杂业务后台。

### 15.2 页面

```text
登录页
配置总览页
缓存配置页
存储配置页
MQTT配置页
系统信息页
```

### 15.3 接口

```text
GET /backend/v1/config
PUT /backend/v1/config
GET /backend/v1/system/info
```

### 15.4 安全要求

```text
1. 只允许 ADMIN。
2. 不显示明文数据库密码。
3. 不显示明文 MinIO secretKey。
4. 不显示 JWT secret。
5. 敏感字段只脱敏展示。
```

### 15.5 配置更新说明

初版可以：

```text
1. 读取 application.yml 显示配置。
2. 修改部分非敏感运行时配置，可存 Redis 或数据库。
3. 对无法热更新的配置，提示需要重启服务。
```

不要强行实现复杂动态配置中心。

### 15.6 阶段 11 自检

```text
1. ADMIN 权限是否限制。
2. 敏感字段是否脱敏。
3. 配置项是否和 application.yml 对齐。
4. 前端是否能清楚查看 TTL、MQTT、MinIO、对象 key 表达式。
```

---

## 16. 阶段 12：集成测试与文档

### 16.1 后端自检命令

Codex 应尝试运行：

```bash
cd campus-server
mvn clean package
```

如无法运行，也必须说明原因。

### 16.2 小程序自检

检查：

```text
1. app.json 是否配置所有页面。
2. tabBar 是否正确。
3. 所有 services 文件是否可引用。
4. 页面中文是否正常。
```

### 16.3 后台 Web 自检

Codex 应尝试：

```bash
cd campus-admin-web
npm install
npm run build
```

如无法运行，也必须说明原因。

### 16.4 文档生成

最终必须生成：

```text
docs/README.md
docs/数据库设计.md
docs/HTTP接口文档.md
docs/MQTT协议文档.md
docs/Redis缓存设计.md
docs/MinIO文件存储设计.md
docs/WebSocket设计.md
docs/部署说明.md
```

---

## 17. Codex 分任务提示词模板

### 17.1 生成后端基础设施

```text
请根据 AGENTS.md 和 docs 中的说明，只实现 campus-server 的通用基础设施层：统一返回、全局异常、配置属性、JWT 骨架、Redis/MinIO/MQTT/WebSocket 配置骨架。

要求：
1. 不实现具体业务接口。
2. 不改数据库字段。
3. JSON 必须 snake_case。
4. 所有类写中文注释。
5. 完成后运行 mvn clean package，并汇报结果。
```

### 17.2 生成数据库与实体

```text
请根据 docs/校园防霸凌软件系统企业级开发说明书.md 的数据库章节，实现 campus.sql、Entity、Mapper、枚举。

要求：
1. 保持 device/user/user_device_bind/event/device_config_wifi 五张表。
2. 不擅自新增大量表。
3. 保持字段尽量不变。
4. 注释写清楚内部主键和对外 ID 的区别。
5. 完成后检查 SQL 和 Entity 是否一致。
```

### 17.3 生成认证与用户模块

```text
请实现 Auth 和 User 模块，包括微信登录、刷新 token、登出、获取个人信息、修改个人信息、头像上传。

要求：
1. 登录只接收 wx.login 的 code。
2. 后端保存 openid_hash，不保存 openid 明文。
3. 头像走后端中转上传到 MinIO。
4. JSON 字段使用 snake_case。
5. Controller 不写复杂业务逻辑。
```

### 17.4 生成设备模块

```text
请实现 Device 模块，包括设备列表、设备搜索、绑定设备、修改设备备注、解绑设备。

要求：
1. 查询只返回当前用户绑定设备。
2. 绑定码从 Redis device:bind_code:{device_id} 获取。
3. 修改的是 user_device_bind 中的 device_name/location/note，不是 device 表。
4. online_status 从 Redis 读取。
5. 所有设备操作必须做权限校验。
```

### 17.5 生成事件模块

```text
请实现 Event 模块，包括 unpulled 补拉、search 查询、read 已读、refresh-url 生成临时访问链接。

要求：
1. 查询范围只能是当前用户绑定设备。
2. unpulled 查询 push_status=PENDING，返回后标记为 PUSHED。
3. read 只修改 read_status。
4. refresh-url 根据 file_key 生成 MinIO 临时 URL。
5. file_url 默认可以为空字符串。
```

### 17.6 生成 MQTT 模块

```text
请实现 MQTT 模块基础链路：Receiver、Router、DedupService、Queue、Handler、Publisher、TopicBuilder。

要求：
1. 不擅自修改 topic。
2. topic 和 payload 以文档为准。
3. 使用 mqtt_msg_id 做 Redis 去重。
4. 数据库唯一约束兜底。
5. 实现 alarm/post、alarm/confirm、status/online、wifi/set/reply、wifi/list 的处理。
6. 密码、secret 不输出日志。
```

### 17.7 生成 WebSocket 模块

```text
请实现 WebSocket 模块，使用单连接设计：/ws/v1/client。

要求：
1. 一个用户一个客户端连接。
2. 连接后通过 SUBSCRIBE_EVENTS 订阅多个 device_id。
3. 后端必须校验这些 device_id 是否属于当前用户。
4. 同一连接推送 NEW_EVENT 和 DEVICE_STATUS_CHANGE。
5. 支持 PING/PONG。
6. 不再使用 /ws/v1/events/{device_id}/new 和 /ws/v1/devices/status 作为主设计。
```

### 17.8 生成小程序

```text
请实现 campus-miniprogram 的基础可运行版本。

要求：
1. 五个 tab 页面：事件、设备、主页、状态、个人。
2. 所有 HTTP 请求走 services/request.js。
3. 所有 API 封装到 services/*-api.js。
4. WebSocket 走 services/websocket.js，连接 /ws/v1/client。
5. 支持 token 存储、refresh、退出登录。
6. UI 简约，中文无乱码。
```

### 17.9 生成后台 Web

```text
请实现 campus-admin-web 的基础配置后台。

要求：
1. Vue 3 + Vite + Element Plus。
2. 页面包括配置总览、缓存配置、存储配置、MQTT配置、系统信息。
3. 只使用 /backend/v1 前缀接口。
4. 敏感字段脱敏显示。
5. 不实现复杂业务后台，只做配置入口。
```

---

## 18. Codex 自检清单

Codex 每次完成任务后，必须回答以下问题：

```text
1. 本次修改了哪些文件？
2. 是否遵守 JSON snake_case？
3. 是否有硬编码的 Redis Key / MQTT Topic / MinIO Key？
4. 是否有 Controller 写复杂业务？
5. 是否有明文保存或返回敏感信息？
6. 是否做了用户设备权限校验？
7. 是否运行了构建命令？结果是什么？
8. 是否有未完成项？
9. 是否有需要人工确认的地方？
```

---

## 19. 人工审查重点

你在审查 Codex 生成结果时，重点看这些地方。

### 19.1 最容易出错的地方

```text
1. device.id 和 device.device_id 混用。
2. user.id 和 user.user_id 混用。
3. event.id 和 event.event_id 混用。
4. JSON 被写成 camelCase。
5. WebSocket 又变回一个设备一个连接。
6. MQTT topic 被 Codex 改名。
7. WiFi 密码被存库或回传。
8. file_url 被长期存数据库。
9. Controller 中业务逻辑过重。
10. 小程序页面里直接 wx.request。
```

### 19.2 审查优先级

```text
第一优先级：安全和权限
第二优先级：协议稳定
第三优先级：数据一致性
第四优先级：分层结构
第五优先级：UI 美观
```

---

## 20. 最终验收标准

### 20.1 后端验收

必须满足：

```text
1. Spring Boot 项目可启动。
2. MySQL 表结构可创建。
3. Redis 配置可连接。
4. MinIO 配置可连接或有清晰占位。
5. MQTT 模块有完整 topic 分发结构。
6. WebSocket 使用 /ws/v1/client 单连接。
7. 登录、用户、设备、事件接口完整。
8. 权限校验完整。
9. 全局异常完整。
10. 日志不泄露敏感信息。
```

### 20.2 小程序验收

必须满足：

```text
1. 五个页面存在。
2. tabBar 正常。
3. 登录流程存在。
4. request.js 统一封装。
5. websocket.js 统一封装。
6. 事件列表、设备列表、个人信息页面能调用接口。
7. 中文无乱码。
```

### 20.3 后台 Web 验收

必须满足：

```text
1. 可启动。
2. 有配置查看页面。
3. 有缓存配置展示。
4. 有 MinIO/MQTT 配置展示。
5. 敏感字段脱敏。
6. ADMIN 权限预留或实现。
```

### 20.4 文档验收

必须满足：

```text
1. README 清楚说明如何启动。
2. SQL 文件存在。
3. HTTP 接口文档存在。
4. MQTT 协议文档存在。
5. Redis Key 文档存在。
6. MinIO 文档存在。
7. WebSocket 文档是单连接设计。
```

---

## 21. 建议开发节奏

不要一次性让 Codex 完成全部。建议每次只做一块。

推荐节奏：

```text
第 1 次：项目骨架 + AGENTS.md + README
第 2 次：后端基础设施
第 3 次：数据库 + Entity + Mapper
第 4 次：Auth + User
第 5 次：Device
第 6 次：Event + Storage
第 7 次：MQTT
第 8 次：WebSocket
第 9 次：小程序框架
第 10 次：小程序业务页面
第 11 次：配置后台 Web
第 12 次：联调、修 bug、补文档
```

每次提交前都让 Codex：

```text
1. 先说明计划。
2. 再修改文件。
3. 最后列出自检结果。
```

---

## 22. 最终结论

本项目不是简单 CRUD，而是一个包含：

```text
微信登录
设备绑定
MQTT 接入
消息去重
事件入库
MinIO 文件上传
WebSocket 实时推送
HTTP 补拉
Redis 缓存
微信小程序
配置后台 Web
```

的完整物联网软件系统。

Codex 构建时必须把它当成企业级项目处理：

```text
分层清晰
配置集中
协议稳定
权限严格
缓存可控
文件可控
日志可查
异常统一
文档完整
方便维护
方便二次开发
```

如果实现过程中遇到冲突，优先级如下：

```text
1. 安全与权限
2. MQTT 协议稳定
3. 数据库字段稳定
4. HTTP 接口稳定
5. 项目分层清晰
6. 实现简单可靠
7. 后期扩展方便
```
