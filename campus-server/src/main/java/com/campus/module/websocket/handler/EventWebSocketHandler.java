package com.campus.module.websocket.handler;

import com.campus.module.device.entity.Device;
import com.campus.module.device.service.DeviceService;
import com.campus.module.websocket.dto.WebSocketClientMessage;
import com.campus.module.websocket.session.WebSocketSessionManager;
import com.campus.security.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionManager sessionManager;
    private final DeviceService deviceService;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        LoginUser loginUser = loginUser(session);
        sessionManager.add(session, loginUser);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        sessionManager.markAlive(session);
        WebSocketClientMessage clientMessage = objectMapper.readValue(message.getPayload(), WebSocketClientMessage.class);
        if ("PING".equals(clientMessage.getType())) {
            send(session, Map.of("type", "PONG", "timestamp", clientMessage.getTimestamp()));
            return;
        }
        if ("SUBSCRIBE_EVENTS".equals(clientMessage.getType())) {
            handleSubscribe(session, clientMessage.getDeviceIds());
            return;
        }
        if ("UNSUBSCRIBE_EVENTS".equals(clientMessage.getType())) {
            List<String> deviceIds = clientMessage.getDeviceIds() == null ? List.of() : clientMessage.getDeviceIds();
            sessionManager.unsubscribe(session.getId(), deviceIds);
            send(session, Map.of("type", "UNSUBSCRIBE_EVENTS_ACK", "success", true, "data", Map.of("device_ids", deviceIds)));
        }
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        sessionManager.markAlive(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionManager.remove(session);
    }

    private void handleSubscribe(WebSocketSession session, List<String> requestedDeviceIds) throws Exception {
        LoginUser loginUser = loginUser(session);
        if (requestedDeviceIds == null || requestedDeviceIds.isEmpty()) {
            sessionManager.subscribe(session.getId(), List.of());
            send(session, Map.of("type", "SUBSCRIBE_EVENTS_ACK", "success", true, "data", Map.of("device_ids", List.of())));
            return;
        }
        List<String> allowed = new ArrayList<>();
        try {
            for (String deviceId : requestedDeviceIds) {
                Device device = deviceService.findByDeviceId(deviceId);
                deviceService.ensureUserBoundDevice(loginUser.getUserTableId(), device.getId());
                allowed.add(deviceId);
            }
        } catch (Exception ex) {
            send(session, Map.of("type", "SUBSCRIBE_EVENTS_ACK", "success", false, "message", "存在无权限订阅的设备"));
            return;
        }
        sessionManager.subscribe(session.getId(), allowed);
        send(session, Map.of("type", "SUBSCRIBE_EVENTS_ACK", "success", true, "data", Map.of("device_ids", allowed)));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WebSocket transport error, session_id={}", session.getId(), exception);
        sessionManager.remove(session);
    }

    private LoginUser loginUser(WebSocketSession session) {
        return (LoginUser) session.getAttributes().get(JwtHandshakeInterceptor.LOGIN_USER_ATTR);
    }

    private void send(WebSocketSession session, Object payload) throws Exception {
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            }
        }
    }
}
