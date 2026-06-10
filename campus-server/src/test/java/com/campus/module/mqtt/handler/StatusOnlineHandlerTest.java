package com.campus.module.mqtt.handler;

import com.campus.common.constants.RedisKeys;
import com.campus.common.enums.DeviceOnlineStatus;
import com.campus.config.CampusProperties;
import com.campus.module.device.entity.Device;
import com.campus.module.device.mapper.DeviceMapper;
import com.campus.module.device.service.DeviceService;
import com.campus.module.mqtt.dto.MqttMessageEnvelope;
import com.campus.module.websocket.handler.WebSocketPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatusOnlineHandlerTest {

    @Mock
    private DeviceService deviceService;
    @Mock
    private DeviceMapper deviceMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private WebSocketPushService webSocketPushService;

    private StatusOnlineHandler handler;
    private Device device;

    @BeforeEach
    void setUp() {
        CampusProperties properties = new CampusProperties();
        properties.getCache().setDeviceOnlineTtlSeconds(90);
        handler = new StatusOnlineHandler(
                new ObjectMapper(), deviceService, deviceMapper, stringRedisTemplate, properties, webSocketPushService);
        device = new Device();
        device.setId(1L);
        device.setDeviceId("device-1");
        when(deviceService.findByDeviceId("device-1")).thenReturn(device);
    }

    @Test
    void refreshesRedisButThrottlesDatabaseAndOnlyPushesStatusChanges() {
        long firstTimestamp = Instant.now().toEpochMilli();
        when(stringRedisTemplate.hasKey(RedisKeys.deviceOnline("device-1"))).thenReturn(false, true, true);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        handler.handle(envelope("online", firstTimestamp));
        handler.handle(envelope("online", firstTimestamp + 60_000));
        handler.handle(envelope("online", firstTimestamp + 300_000));

        verify(valueOperations, times(6)).set(anyString(), anyString(), eq(90L), any());
        verify(deviceMapper, times(2)).updateById(device);
        verify(webSocketPushService, times(1)).pushDeviceStatus(
                eq("device-1"), eq(DeviceOnlineStatus.ONLINE.name()), any(LocalDateTime.class));
    }

    @Test
    void onlyPushesOfflineWhenOnlineStateActuallyChanged() {
        device.setLastOnlineTime(LocalDateTime.now());
        when(stringRedisTemplate.hasKey(RedisKeys.deviceOnline("device-1"))).thenReturn(true, false);

        handler.handle(envelope("offline", Instant.now().toEpochMilli()));
        handler.handle(envelope("offline", Instant.now().toEpochMilli()));

        verify(stringRedisTemplate, times(2)).delete(RedisKeys.deviceOnline("device-1"));
        verify(webSocketPushService, times(1)).pushDeviceStatus(
                "device-1", DeviceOnlineStatus.OFFLINE.name(), device.getLastOnlineTime());
    }

    private MqttMessageEnvelope envelope(String status, long timestamp) {
        String payload = "{\"status\":\"" + status + "\",\"timestamp\":" + timestamp + "}";
        return new MqttMessageEnvelope(
                "campus/product/device/status/online", payload, "product", "device-1", "status/online", "message-1");
    }
}
