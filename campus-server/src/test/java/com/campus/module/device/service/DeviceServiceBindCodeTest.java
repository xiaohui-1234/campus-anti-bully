package com.campus.module.device.service;

import com.campus.common.constants.RedisKeys;
import com.campus.config.CampusProperties;
import com.campus.module.device.mapper.DeviceMapper;
import com.campus.module.device.mapper.UserDeviceBindMapper;
import com.campus.module.websocket.session.WebSocketSessionManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceServiceBindCodeTest {

    @Test
    void consumesMatchingBindCodeAtomically() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        DeviceService service = service(redis);
        when(redis.execute(any(RedisScript.class), eq(List.of(RedisKeys.deviceBindCode("dev001"))), eq("123456")))
                .thenReturn(1L);

        assertTrue(service.consumeBindCode("dev001", "123456"));
        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redis).execute(scriptCaptor.capture(), eq(List.of("device:bind_code:dev001")), eq("123456"));
        assertTrue(scriptCaptor.getValue().getScriptAsString().contains("redis.call('GET', KEYS[1]) == ARGV[1]"));
        assertTrue(scriptCaptor.getValue().getScriptAsString().contains("redis.call('DEL', KEYS[1])"));
    }

    @Test
    void rejectsMissingMismatchedOrAlreadyConsumedBindCode() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        DeviceService service = service(redis);
        when(redis.execute(any(RedisScript.class), eq(List.of(RedisKeys.deviceBindCode("dev001"))), eq("000000")))
                .thenReturn(0L);

        assertFalse(service.consumeBindCode("dev001", "000000"));
    }

    private DeviceService service(StringRedisTemplate redis) {
        return new DeviceService(
                mock(DeviceMapper.class),
                mock(UserDeviceBindMapper.class),
                redis,
                new CampusProperties(),
                mock(WebSocketSessionManager.class));
    }
}
