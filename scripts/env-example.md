# 环境变量与配置示例

生产配置建议通过外部 `application-prod.yml` 或环境变量覆盖：

```yaml
spring:
  datasource:
    url: jdbc:mysql://mysql:3306/campus?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: campus
    password: change-me

campus:
  security:
    jwt:
      secret: change-me-to-a-long-random-secret
  wx:
    miniapp:
      app-id: wx-app-id
      app-secret: wx-app-secret
  minio:
    endpoint: http://minio:9000
    access-key: campus
    secret-key: change-me
  mqtt:
    enabled: true
    broker-url: tcp://emqx:1883
```
