package com.campus.module.websocket.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeviceStatusWebSocketHandler {

    private final WebSocketPushService webSocketPushService;

    public boolean push(String deviceId, String onlineStatus, Object lastOnlineTime) {
        return webSocketPushService.pushDeviceStatus(deviceId, onlineStatus, lastOnlineTime);
    }
}
