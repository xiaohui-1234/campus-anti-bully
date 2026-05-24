package com.campus.module.websocket.handler;

import com.campus.module.event.vo.EventVO;
import com.campus.module.websocket.session.WebSocketSessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketPushService {

    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public boolean pushNewEvent(EventVO event) {
        return pushToDevice(event.getDeviceId(), Map.of("type", "NEW_EVENT", "data", event));
    }

    public boolean pushDeviceStatus(String deviceId, String onlineStatus, Object lastOnlineTime) {
        return pushToDevice(deviceId, Map.of(
                "type", "DEVICE_STATUS",
                "data", Map.of(
                        "device_id", deviceId,
                        "online_status", onlineStatus,
                        "last_online_time", lastOnlineTime == null ? "" : lastOnlineTime
                )
        ));
    }

    private boolean pushToDevice(String deviceId, Object payload) {
        boolean pushed = false;
        for (WebSocketSession session : sessionManager.sessionsByDevice(deviceId)) {
            try {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
                pushed = true;
            } catch (Exception ex) {
                log.warn("WebSocket push failed, device_id={}, session_id={}", deviceId, session.getId());
            }
        }
        return pushed;
    }
}
