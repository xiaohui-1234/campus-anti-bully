package com.campus.module.mqtt.handler;

import com.campus.common.constants.RedisKeys;
import com.campus.common.enums.DeviceOnlineStatus;
import com.campus.config.CampusProperties;
import com.campus.module.device.entity.Device;
import com.campus.module.device.mapper.DeviceMapper;
import com.campus.module.device.service.DeviceService;
import com.campus.module.mqtt.dto.MqttMessageEnvelope;
import com.campus.module.mqtt.dto.StatusOnlinePayload;
import com.campus.module.websocket.handler.WebSocketPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatusOnlineHandler implements MqttMessageHandler {

    private static final Duration LAST_ONLINE_UPDATE_INTERVAL = Duration.ofMinutes(5);

    private final ObjectMapper objectMapper;
    private final DeviceService deviceService;
    private final DeviceMapper deviceMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final CampusProperties properties;
    private final WebSocketPushService webSocketPushService;

    @Override
    public boolean supports(String action) {
        return "status/online".equals(action);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(MqttMessageEnvelope envelope) {
        try {
            StatusOnlinePayload payload = objectMapper.readValue(envelope.getPayload(), StatusOnlinePayload.class);
            Device device = deviceService.findByDeviceId(envelope.getDeviceId());
            boolean wasOnline = Boolean.TRUE.equals(
                    stringRedisTemplate.hasKey(RedisKeys.deviceOnline(device.getDeviceId())));
            if ("online".equalsIgnoreCase(payload.getStatus())) {
                LocalDateTime lastOnline = toTime(payload.getTimestamp());
                stringRedisTemplate.opsForValue().set(
                        RedisKeys.deviceOnline(device.getDeviceId()),
                        "online",
                        properties.getCache().getDeviceOnlineTtlSeconds(),
                        TimeUnit.SECONDS);
                stringRedisTemplate.opsForValue().set(
                        RedisKeys.deviceLastHeartbeat(device.getDeviceId()),
                        String.valueOf(payload.getTimestamp()),
                        properties.getCache().getDeviceOnlineTtlSeconds(),
                        TimeUnit.SECONDS);
                if (shouldUpdateLastOnline(device.getLastOnlineTime(), lastOnline)) {
                    device.setLastOnlineTime(lastOnline);
                    deviceMapper.updateById(device);
                }
                if (!wasOnline) {
                    webSocketPushService.pushDeviceStatus(device.getDeviceId(), DeviceOnlineStatus.ONLINE.name(), lastOnline);
                }
            } else {
                stringRedisTemplate.delete(RedisKeys.deviceOnline(device.getDeviceId()));
                if (wasOnline) {
                    webSocketPushService.pushDeviceStatus(
                            device.getDeviceId(), DeviceOnlineStatus.OFFLINE.name(), device.getLastOnlineTime());
                }
            }
        } catch (Exception ex) {
            log.error("Handle status/online failed, mqtt_msg_id={}", envelope.getMqttMsgId(), ex);
            throw new IllegalStateException(ex);
        }
    }

    private LocalDateTime toTime(Long timestamp) {
        if (timestamp == null || timestamp <= 0) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("Asia/Shanghai"));
    }

    private boolean shouldUpdateLastOnline(LocalDateTime storedLastOnline, LocalDateTime currentLastOnline) {
        return storedLastOnline == null
                || !currentLastOnline.isBefore(storedLastOnline.plus(LAST_ONLINE_UPDATE_INTERVAL));
    }
}
