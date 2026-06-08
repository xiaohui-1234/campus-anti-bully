# 环境配置示例

生产环境建议用外部 `application-prod.yml`、容器环境变量或部署平台密钥管理覆盖敏感配置。不要把真实密码、JWT secret、微信 appSecret、MinIO secretKey 提交到仓库。

```yaml
spring:
  datasource:
    url: jdbc:mysql://mysql:3306/campus?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: campus
    password: change-me
  data:
    redis:
      host: redis
      port: 6379
      password: change-me
      database: 0

campus:
  security:
    jwt:
      access-token-expire-seconds: 7200
      refresh-token-expire-seconds: 604800
      secret: change-me-to-a-long-random-secret
  wx:
    miniapp:
      app-id: wx-app-id
      app-secret: wx-app-secret
  mqtt:
    enabled: true
    broker-url: tcp://emqx:1883
    client-id: campus-server
    username: campus-server
    password: change-me
    qos: 1
    completion-timeout-ms: 5000
  minio:
    endpoint: http://minio:9000
    access-key: campus
    secret-key: change-me
    bucket: campus
    public-endpoint: https://oss.example.com
  cache:
    mqtt-dedup-ttl-seconds: 86400
    device-online-ttl-seconds: 90
    bind-code-ttl-seconds: 60
    access-url-ttl-seconds: 3600
    upload-url-ttl-seconds: 300
    jwt-blacklist-ttl-seconds: 7200
    unpulled-event-cache-ttl-seconds: 300
  storage:
    object-key-pattern: "{product_type}/audio/{device_id}/{yyyy}/{MM}/{dd}/{event_id}.{ext}"
    avatar-key-pattern: "avatar/{user_id}.{ext}"
    avatar-max-size-mb: 2
```
