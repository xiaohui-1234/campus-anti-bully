# AGENTS.md

## 项目定位

校园防霸凌软件系统包含 Spring Boot 后端、微信小程序、Vue 配置后台、SQL 与交付文档。

## 最高优先级规则

1. JSON 字段保持 snake_case。
2. WebSocket 只能使用 `/ws/v1/client` 单连接，再由客户端订阅多个设备。
3. MQTT topic 必须保持文档定义，不得改名。
4. 不能明文保存或返回 openid、device_secret、wifi_password、JWT、MinIO secretKey。
5. 所有设备和事件接口必须校验当前用户是否绑定设备。
6. Controller 不写复杂业务逻辑。

## 后端技术栈

Java 17、Spring Boot 3、Spring Security、JWT、MyBatis-Plus、MySQL、Redis、MinIO、Eclipse Paho MQTT、Spring WebSocket。

## 前端技术栈

微信小程序原生 JavaScript；后台 Web 使用 Vue 3、Vite、Element Plus。

## 必读文档

根目录两份原始说明书，以及 `docs/` 下拆分后的接口、协议、存储和部署文档。
