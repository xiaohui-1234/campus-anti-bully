# Campus Android

校园防霸凌系统 Android 客户端。该工程是微信小程序的原生 Android 移植版，功能对齐小程序，体验按移动端安全工具 App 设计。

## 接口配置

- HTTP: `https://campus.1314-520.xyz/api/v1/`
- WebSocket: `wss://campus.1314-520.xyz/ws/v1/client`

## 第一阶段范围

- 跨端账号登录、注册、刷新 token、退出登录
- 首页概览、设备列表、事件列表、个人中心
- 单 WebSocket 连接、设备订阅、心跳、重连骨架
- Android 通知通道、前台服务、WorkManager 兜底同步骨架
- JWT 加密存储和敏感日志约束

## 打开方式

使用 Android Studio 打开 `campus-android` 目录。当前仓库未提交 Gradle Wrapper，需要本机 Android Studio 或 Gradle 环境下载依赖后构建。

当前 Windows 中文路径下构建已在 `gradle.properties` 中启用：

```properties
android.overridePathCheck=true
```

本机如果没有自动识别 SDK，需要创建未入库的 `local.properties`：

```properties
sdk.dir=D:/Android/SDK
```

建议使用 Android Studio 自带 JBR/JDK 21 执行 Gradle。JDK 25 下 `assembleDebug` 可通过，但 Android Lint 可能报内部 `25` 错误。

验证命令：

```powershell
$env:JAVA_HOME='D:\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
gradle :app:assembleDebug
gradle :app:lintDebug
```

## 安全边界

Android 端不持有 MinIO 密钥，不直接连 MQTT，不绕过 Spring Boot 权限校验。所有设备、事件、音频访问权限以后端接口返回为准。
