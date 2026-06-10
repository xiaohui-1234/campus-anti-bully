package com.campus.module.websocket.session;

import com.campus.security.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WebSocketSessionManager {

    private static final long SESSION_PROBE_INTERVAL_MILLIS = 60_000L;

    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionUsers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> userSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> sessionDevices = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> deviceSessions = new ConcurrentHashMap<>();
    private final Set<String> sessionsAwaitingPong = ConcurrentHashMap.newKeySet();

    public void add(WebSocketSession session, LoginUser loginUser) {
        synchronized (session) {
            sessions.put(session.getId(), session);
            sessionUsers.put(session.getId(), loginUser.getUserId());
            addToIndex(userSessions, loginUser.getUserId(), session.getId());
            if (!session.isOpen()) {
                remove(session.getId());
                return;
            }
            log.info("WebSocket connected, user_id={}, session_id={}", loginUser.getUserId(), session.getId());
        }
    }

    public void remove(WebSocketSession session) {
        synchronized (session) {
            remove(session.getId());
        }
    }

    public void closeAndRemove(WebSocketSession session, CloseStatus status) {
        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.close(status);
                }
            }
        } catch (Exception ex) {
            log.debug("WebSocket close failed, session_id={}", session.getId(), ex);
        } finally {
            remove(session);
        }
    }

    private void remove(String sessionId) {
        sessionsAwaitingPong.remove(sessionId);
        String userId = sessionUsers.remove(sessionId);
        WebSocketSession removed = sessions.remove(sessionId);
        if (userId != null) {
            removeFromIndex(userSessions, userId, sessionId);
        }
        Set<String> deviceIds = sessionDevices.remove(sessionId);
        if (deviceIds != null) {
            for (String deviceId : deviceIds) {
                removeFromIndex(deviceSessions, deviceId, sessionId);
            }
        }
        if (removed != null || userId != null || deviceIds != null) {
            log.info("WebSocket closed, user_id={}, session_id={}", userId, sessionId);
        }
    }

    public void subscribe(String sessionId, List<String> deviceIds) {
        WebSocketSession session = sessions.get(sessionId);
        if (session == null) {
            remove(sessionId);
            return;
        }

        synchronized (session) {
            if (sessions.get(sessionId) != session || !session.isOpen()) {
                remove(sessionId);
                return;
            }
            Set<String> requestedDeviceIds = new HashSet<>(deviceIds);
            Set<String> currentDeviceIds = new HashSet<>(
                    sessionDevices.getOrDefault(sessionId, Collections.emptySet()));
            for (String deviceId : currentDeviceIds) {
                if (!requestedDeviceIds.contains(deviceId)) {
                    removeFromIndex(sessionDevices, sessionId, deviceId);
                    removeFromIndex(deviceSessions, deviceId, sessionId);
                }
            }
            for (String deviceId : requestedDeviceIds) {
                addToIndex(sessionDevices, sessionId, deviceId);
                addToIndex(deviceSessions, deviceId, sessionId);
            }
            if (sessions.get(sessionId) != session || !session.isOpen()) {
                remove(sessionId);
            }
        }
    }

    public void unsubscribe(String sessionId, List<String> deviceIds) {
        WebSocketSession session = sessions.get(sessionId);
        if (session == null) {
            removeSubscriptions(sessionId, deviceIds);
            return;
        }
        synchronized (session) {
            removeSubscriptions(sessionId, deviceIds);
        }
    }

    public void closeUserSessions(String userId) {
        List<String> sessionIds = new ArrayList<>(userSessions.getOrDefault(userId, Collections.emptySet()));
        for (String sessionId : sessionIds) {
            WebSocketSession session = sessions.get(sessionId);
            if (session != null) {
                closeAndRemove(session, CloseStatus.POLICY_VIOLATION);
            } else {
                remove(sessionId);
            }
        }
    }

    private void removeSubscriptions(String sessionId, List<String> deviceIds) {
        for (String deviceId : deviceIds) {
            removeFromIndex(sessionDevices, sessionId, deviceId);
            removeFromIndex(deviceSessions, deviceId, sessionId);
        }
    }

    public List<WebSocketSession> sessionsByDevice(String deviceId) {
        Set<String> ids = deviceSessions.getOrDefault(deviceId, Collections.emptySet());
        List<WebSocketSession> result = new ArrayList<>();
        for (String id : ids) {
            WebSocketSession session = sessions.get(id);
            if (session != null && session.isOpen()) {
                result.add(session);
            } else if (session != null) {
                remove(session);
            } else {
                removeFromIndex(deviceSessions, deviceId, id);
                remove(id);
            }
        }
        return result;
    }

    public void markAlive(WebSocketSession session) {
        sessionsAwaitingPong.remove(session.getId());
    }

    @Scheduled(fixedDelay = SESSION_PROBE_INTERVAL_MILLIS, initialDelay = SESSION_PROBE_INTERVAL_MILLIS)
    public void probeSessions() {
        for (WebSocketSession session : sessions.values()) {
            try {
                synchronized (session) {
                    if (sessions.get(session.getId()) != session) {
                        sessionsAwaitingPong.remove(session.getId());
                        continue;
                    }
                    if (!session.isOpen()) {
                        remove(session.getId());
                        continue;
                    }
                    if (!sessionsAwaitingPong.add(session.getId())) {
                        log.warn("WebSocket pong timeout, closing session, session_id={}", session.getId());
                        closeAndRemove(session, CloseStatus.SERVER_ERROR);
                        continue;
                    }
                    session.sendMessage(new PingMessage());
                }
            } catch (Exception ex) {
                log.warn("WebSocket probe failed, closing session, session_id={}, reason={}",
                        session.getId(), ex.getMessage());
                log.debug("WebSocket probe failure detail, session_id={}", session.getId(), ex);
                closeAndRemove(session, CloseStatus.SERVER_ERROR);
            }
        }
    }

    private void addToIndex(ConcurrentHashMap<String, Set<String>> index, String key, String value) {
        index.compute(key, (ignored, values) -> {
            Set<String> result = values == null ? ConcurrentHashMap.newKeySet() : values;
            result.add(value);
            return result;
        });
    }

    private void removeFromIndex(ConcurrentHashMap<String, Set<String>> index, String key, String value) {
        index.computeIfPresent(key, (ignored, values) -> {
            values.remove(value);
            return values.isEmpty() ? null : values;
        });
    }
}
