package com.campus.module.websocket.session;

import com.campus.security.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WebSocketSessionManager {

    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionUsers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> userSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> sessionDevices = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> deviceSessions = new ConcurrentHashMap<>();

    public void add(WebSocketSession session, LoginUser loginUser) {
        sessions.put(session.getId(), session);
        sessionUsers.put(session.getId(), loginUser.getUserId());
        userSessions.computeIfAbsent(loginUser.getUserId(), key -> ConcurrentHashMap.newKeySet()).add(session.getId());
        log.info("WebSocket connected, user_id={}, session_id={}", loginUser.getUserId(), session.getId());
    }

    public void remove(WebSocketSession session) {
        String sessionId = session.getId();
        String userId = sessionUsers.remove(sessionId);
        sessions.remove(sessionId);
        if (userId != null) {
            Set<String> ids = userSessions.getOrDefault(userId, Collections.emptySet());
            ids.remove(sessionId);
            if (ids.isEmpty()) {
                userSessions.remove(userId);
            }
        }
        Set<String> deviceIds = sessionDevices.remove(sessionId);
        if (deviceIds != null) {
            for (String deviceId : deviceIds) {
                Set<String> ids = deviceSessions.getOrDefault(deviceId, Collections.emptySet());
                ids.remove(sessionId);
                if (ids.isEmpty()) {
                    deviceSessions.remove(deviceId);
                }
            }
        }
        log.info("WebSocket closed, user_id={}, session_id={}", userId, sessionId);
    }

    public void subscribe(String sessionId, List<String> deviceIds) {
        Set<String> devices = sessionDevices.computeIfAbsent(sessionId, key -> ConcurrentHashMap.newKeySet());
        for (String deviceId : deviceIds) {
            devices.add(deviceId);
            deviceSessions.computeIfAbsent(deviceId, key -> ConcurrentHashMap.newKeySet()).add(sessionId);
        }
    }

    public void unsubscribe(String sessionId, List<String> deviceIds) {
        Set<String> devices = sessionDevices.getOrDefault(sessionId, Collections.emptySet());
        for (String deviceId : deviceIds) {
            devices.remove(deviceId);
            Set<String> ids = deviceSessions.getOrDefault(deviceId, Collections.emptySet());
            ids.remove(sessionId);
            if (ids.isEmpty()) {
                deviceSessions.remove(deviceId);
            }
        }
    }

    public List<WebSocketSession> sessionsByDevice(String deviceId) {
        Set<String> ids = deviceSessions.getOrDefault(deviceId, Collections.emptySet());
        List<WebSocketSession> result = new ArrayList<>();
        for (String id : ids) {
            WebSocketSession session = sessions.get(id);
            if (session != null && session.isOpen()) {
                result.add(session);
            }
        }
        return result;
    }
}
