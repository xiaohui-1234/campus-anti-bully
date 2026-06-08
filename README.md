# 校园欺凌实时监测系统（旧版）

本分支为校园欺凌实时监测系统的旧版实现，主要包含 STM32 物联网报警终端、微信小程序展示端以及配套工具资料。系统面向校园安全场景，通过按键报警、语音触发、事件上报、录音留存和小程序查看等功能，形成从现场报警到移动端查看的基础闭环。

## 项目概述

旧版系统以 STM32F103C8T6 主控为核心，配合 ESP8266 网络模块、语音识别模块、TF 卡存储和音频采集电路完成现场报警处理。设备触发报警后，通过 OneNET 物联网平台上报事件类型、事件描述、报警时间、报警等级等属性，并在本地录制 WAV 音频文件，上传至对象存储后回传音频访问地址。

微信小程序端负责用户登录、事件列表展示、报警录音回放和设备地点配置。小程序通过微信云开发保存用户信息与事件记录，并通过 OneNET 接口读取设备侧最新上报数据。

## 功能特性

- 按键报警：通过硬件按键触发报警事件，并上报至 OneNET。
- 语音报警：接收语音识别模块输出的命令，区分求救、着火、打架、抢劫等场景。
- 实时上报：通过 ESP8266 连接 WiFi，并使用 MQTT 向 OneNET 上报物模型属性。
- 音频留存：基于 ADC、DMA 和 FatFs 录制约 15 秒 WAV 音频并保存至 TF 卡。
- 音频上传：通过 ESP8266 以 HTTP 方式上传录音文件，并回传 `audio_url`。
- 小程序查看：在微信小程序中查看报警时间、设备地点、事件类型和事件描述。
- 录音回放：小程序支持播放报警事件对应的录音文件。
- 设备地点配置：小程序可向 OneNET 下发设备地点，设备端接收后回传确认。

## 系统架构

```text
语音模块 / 按键
      ↓
STM32F103C8T6 主控
      ↓
ESP8266 WiFi 模块
      ↓
OneNET MQTT 物模型上报
      ↓
微信小程序读取事件信息

报警触发后：
STM32 ADC + DMA 录音
      ↓
TF 卡保存 WAV 文件
      ↓
ESP8266 上传对象存储
      ↓
OneNET 上报 audio_url
      ↓
小程序播放录音
```

## 目录结构

```text
.
├── miniprogram-2/          # 微信小程序与云函数
│   ├── cloudfunctions/     # 微信云开发云函数
│   └── miniprogram/        # 小程序页面、组件、样式和资源
├── stm32主控源码/           # STM32 主控 Keil 工程源码
│   ├── User/               # 主程序、中断与系统配置
│   ├── Hardware/           # ESP8266、串口、LED、按键、TF 卡等驱动
│   ├── Alarm/              # 报警类型与上报逻辑
│   ├── Library/            # STM32 标准外设库
│   ├── System/             # 延时、定时器等基础模块
│   └── Project.uvprojx     # Keil MDK 工程文件
└── 工具/                   # ESP8266 固件、STM32 说明、对象存储工具资料
```

## 技术栈

### 嵌入式端

- STM32F103C8T6
- Keil MDK-ARM
- STM32F10x Standard Peripheral Library
- ESP8266 AT 指令
- OneNET MQTT 物模型
- ADC、DMA、TIM、USART、SPI
- FatFs 文件系统
- TF/SD 卡存储
- WAV 音频文件

### 小程序端

- 微信小程序原生框架
- 微信云开发
- 云函数登录
- 云数据库
- OneNET HTTP API
- `@vant/weapp`

## 关键模块说明

### STM32 主控端

`stm32主控源码/User/main.c` 是设备端主入口，负责硬件初始化、主循环调度、语音命令处理、录音控制、WAV 文件生成和 OSS 上传流程。

`stm32主控源码/Alarm/alarm.c` 封装报警类型与 OneNET 属性上报逻辑，包含按键报警、语音报警、报警等级和报警描述等业务处理。

`stm32主控源码/Hardware/esp8266.c` 与 `esp8266.h` 负责 ESP8266 初始化、WiFi 连接、MQTT 连接、NTP 时间获取、OneNET 订阅消息处理和 TCP/HTTP 数据发送。

### 微信小程序端

`miniprogram-2/miniprogram/pages/login` 实现微信登录资料填写、头像上传和云函数登录。

`miniprogram-2/miniprogram/pages/index` 实现报警事件读取、云数据库记录、事件列表展示和录音回放。

`miniprogram-2/miniprogram/pages/setting` 实现设备地点配置，并通过 OneNET 物模型接口下发设备属性。

`miniprogram-2/cloudfunctions/login` 实现基于 openid 的用户创建和用户资料更新。

## 运行说明

### 微信小程序

1. 使用微信开发者工具导入 `miniprogram-2`。
2. 开通并绑定微信云开发环境。
3. 部署 `cloudfunctions/login` 云函数。
4. 在云数据库中准备 `login`、`message` 等集合。
5. 根据实际 OneNET 产品、设备和鉴权信息，修改小程序中的 OneNET 请求配置。
6. 如需使用 Vant Weapp 组件，先在 `miniprogram-2` 下安装依赖，并在微信开发者工具中构建 npm。

### STM32 固件

1. 使用 Keil MDK 打开 `stm32主控源码/Project.uvprojx`。
2. 根据实际环境修改 WiFi、OneNET、对象存储等配置。
3. 确认 ESP8266、语音模块、TF 卡、音频输入、按键和调试串口接线正确。
4. 编译工程并烧录至 STM32F103C8T6。
5. 通过 USART1 调试串口查看 WiFi 连接、MQTT 连接、报警上报、录音保存和上传状态。

## 配置项说明

正式部署或演示前，应重点检查以下配置：

- WiFi 热点名称与密码：`stm32主控源码/Hardware/esp8266.h`
- OneNET 产品 ID、设备名、鉴权信息与 MQTT Topic：`stm32主控源码/Hardware/esp8266.h`、`stm32主控源码/Alarm/alarm.c`、`stm32主控源码/User/main.c`
- 对象存储 Host、端口与对象路径前缀：`stm32主控源码/User/main.c`
- 微信云开发环境 ID：`miniprogram-2/miniprogram/app.js`
- 小程序 OneNET 请求地址、产品 ID、设备名和鉴权信息：`miniprogram-2/miniprogram/pages/index/index.js`、`miniprogram-2/miniprogram/pages/setting/setting.js`

## 数据与接口

设备端主要上报以下 OneNET 物模型属性：

| 字段 | 说明 |
| --- | --- |
| `eventType` | 报警类型，例如按键报警或语音报警 |
| `desc` | 事件描述，例如求救、着火、打架等 |
| `alarm_level` | 报警等级 |
| `time` | 报警发生时间 |
| `deviceName` | 设备地点或设备名称 |
| `audio_url` | 报警录音访问地址 |

小程序端云数据库主要涉及：

| 集合 | 说明 |
| --- | --- |
| `login` | 保存用户 openid、昵称、头像等登录资料 |
| `message` | 保存报警事件、录音地址和创建时间 |

## 使用边界

本分支为旧版工程实现，适合用于项目展示、课程实践、原型验证和后续迭代参考。正式部署前，应补充统一配置管理、访问鉴权、隐私合规、异常重试、离线缓存、事件关联 ID 和对象存储安全策略。

报警录音、用户信息和设备地点均可能涉及隐私数据。实际使用时应确保采集、存储、访问和展示过程符合相关授权要求，并避免在公开仓库中提交生产环境密钥、Token、云存储凭据或真实用户数据。

