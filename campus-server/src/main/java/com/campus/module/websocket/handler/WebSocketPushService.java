package com.campus.module.websocket.handler;

import com.campus.module.event.vo.EventVO;
import com.campus.module.websocket.session.WebSocketSessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class WebSocketPushService {

    private static final long PUSH_TIMEOUT_MILLIS = 2_000L;

    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final ExecutorService pushExecutor;
    private final long pushTimeoutMillis;
    private final Set<String> sessionsSending = ConcurrentHashMap.newKeySet();
    private final Set<String> sessionsActivelySending = ConcurrentHashMap.newKeySet();

    @Autowired
    public WebSocketPushService(WebSocketSessionManager sessionManager,
                                ObjectMapper objectMapper,
                                @Qualifier("webSocketPushExecutor") ExecutorService pushExecutor) {
        this(sessionManager, objectMapper, pushExecutor, PUSH_TIMEOUT_MILLIS);
    }

    WebSocketPushService(WebSocketSessionManager sessionManager,
                         ObjectMapper objectMapper,
                         ExecutorService pushExecutor,
                         long pushTimeoutMillis) {
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
        this.pushExecutor = pushExecutor;
        this.pushTimeoutMillis = pushTimeoutMillis;
    }

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
        TextMessage message;
        try {
            message = new TextMessage(objectMapper.writeValueAsString(payload));
        } catch (Exception ex) {
            log.error("WebSocket payload serialization failed, device_id={}", deviceId, ex);
            return false;
        }

        CompletionService<Boolean> completionService = new ExecutorCompletionService<>(pushExecutor);
        List<PendingPush> tasks = new ArrayList<>();
        for (WebSocketSession session : sessionManager.sessionsByDevice(deviceId)) {
            if (!sessionsSending.add(session.getId())) {
                continue;
            }
            try {
                Future<Boolean> future = completionService.submit(() -> send(deviceId, session, message));
                tasks.add(new PendingPush(future, session.getId()));
            } catch (RejectedExecutionException ex) {
                sessionsSending.remove(session.getId());
                log.warn("WebSocket push executor full, skipping push, device_id={}, session_id={}",
                        deviceId, session.getId());
            }
        }

        boolean pushed = false;
        int completed = 0;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(pushTimeoutMillis);
        try {
            while (completed < tasks.size()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    break;
                }
                Future<Boolean> completedTask = completionService.poll(remaining, TimeUnit.NANOSECONDS);
                if (completedTask == null) {
                    break;
                }
                completed++;
                pushed |= Boolean.TRUE.equals(completedTask.get());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.debug("WebSocket push result collection failed, device_id={}", deviceId, ex);
        } finally {
            for (PendingPush task : tasks) {
                if (!task.future().isDone() && task.future().cancel(true)
                        && !sessionsActivelySending.contains(task.sessionId())) {
                    sessionsSending.remove(task.sessionId());
                }
            }
        }
        return pushed;
    }

    private boolean send(String deviceId, WebSocketSession session, TextMessage message) {
        sessionsActivelySending.add(session.getId());
        try {
            synchronized (session) {
                if (Thread.currentThread().isInterrupted() || !session.isOpen()) {
                    return false;
                }
                session.sendMessage(message);
                return true;
            }
        } catch (Exception ex) {
            log.warn("WebSocket push failed, closing session, device_id={}, session_id={}",
                    deviceId, session.getId());
            sessionManager.closeAndRemove(session, CloseStatus.SERVER_ERROR);
            return false;
        } finally {
            sessionsActivelySending.remove(session.getId());
            sessionsSending.remove(session.getId());
        }
    }

    private record PendingPush(Future<Boolean> future, String sessionId) {
    }
}
