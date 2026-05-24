package com.campus.module.mqtt.dedup;

import com.campus.common.constants.RedisKeys;
import com.campus.config.CampusProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MqttDedupService {

    private final StringRedisTemplate stringRedisTemplate;
    private final CampusProperties properties;

    public boolean firstSeen(String deviceId, String mqttMsgId) {
        try {
            Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(
                    RedisKeys.mqttDedup(deviceId, mqttMsgId),
                    "DONE",
                    properties.getCache().getMqttDedupTtlSeconds(),
                    TimeUnit.SECONDS
            );
            if (Boolean.FALSE.equals(ok)) {
                log.info("MQTT dedup hit, device_id={}, mqtt_msg_id={}", deviceId, mqttMsgId);
            }
            return Boolean.TRUE.equals(ok);
        } catch (Exception ex) {
            log.warn("Redis dedup unavailable, falling back to database unique keys, device_id={}", deviceId);
            return true;
        }
    }
}
