package com.campus.module.websocket.handler;

import com.campus.module.websocket.session.WebSocketSessionManager;
import com.campus.security.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketSessionCleanupTest {

    private final List<ExecutorService> executors = new ArrayList<>();

    @AfterEach
    void shutDownExecutors() {
        executors.forEach(ExecutorService::shutdownNow);
    }

    @Test
    void closedSessionIsPrunedFromSubscriptionIndexes() {
        WebSocketSessionManager manager = new WebSocketSessionManager();
        WebSocketSession session = session("session-1");
        when(session.isOpen()).thenReturn(true, true, false);

        manager.add(session, loginUser());
        manager.subscribe(session.getId(), List.of("device-1"));

        assertTrue(manager.sessionsByDevice("device-1").isEmpty());
        assertTrue(manager.sessionsByDevice("device-1").isEmpty());
        verify(session, times(3)).isOpen();
    }

    @Test
    void pongKeepsQuietOpenSessionAlive() throws Exception {
        WebSocketSessionManager manager = new WebSocketSessionManager();
        WebSocketSession session = session("session-2");
        when(session.isOpen()).thenReturn(true);
        manager.add(session, loginUser());
        manager.subscribe(session.getId(), List.of("device-1"));

        manager.probeSessions();
        manager.markAlive(session);
        manager.probeSessions();

        verify(session, times(2)).sendMessage(any(PingMessage.class));
        assertFalse(manager.sessionsByDevice("device-1").isEmpty());
    }

    @Test
    void missedPongClosesAndRemovesSession() throws Exception {
        WebSocketSessionManager manager = new WebSocketSessionManager();
        WebSocketSession session = session("session-3");
        when(session.isOpen()).thenReturn(true);
        manager.add(session, loginUser());
        manager.subscribe(session.getId(), List.of("device-1"));

        manager.probeSessions();
        manager.probeSessions();

        assertTrue(manager.sessionsByDevice("device-1").isEmpty());
        verify(session).close(CloseStatus.SERVER_ERROR);
        verify(session).sendMessage(any(PingMessage.class));
    }

    @Test
    void failedProbeClosesAndRemovesSession() throws Exception {
        WebSocketSessionManager manager = new WebSocketSessionManager();
        WebSocketSession session = session("session-4");
        when(session.isOpen()).thenReturn(true);
        doThrow(new IOException("connection reset")).when(session).sendMessage(any(PingMessage.class));
        manager.add(session, loginUser());
        manager.subscribe(session.getId(), List.of("device-1"));

        manager.probeSessions();

        assertTrue(manager.sessionsByDevice("device-1").isEmpty());
        verify(session).close(CloseStatus.SERVER_ERROR);
    }

    @Test
    void failedPushClosesAndRemovesSession() throws Exception {
        WebSocketSessionManager manager = new WebSocketSessionManager();
        WebSocketSession session = session("session-5");
        when(session.isOpen()).thenReturn(true);
        doThrow(new IOException("connection reset")).when(session).sendMessage(any());
        manager.add(session, loginUser());
        manager.subscribe(session.getId(), List.of("device-1"));
        WebSocketPushService pushService = pushService(manager, 2_000);

        assertFalse(pushService.pushDeviceStatus("device-1", "ONLINE", null));
        assertFalse(pushService.pushDeviceStatus("device-1", "ONLINE", null));

        verify(session).close(CloseStatus.SERVER_ERROR);
        verify(session, times(1)).sendMessage(any());
    }

    @Test
    void subscribeReplacesPreviousDeviceSetIncludingEmptySet() {
        WebSocketSessionManager manager = new WebSocketSessionManager();
        WebSocketSession session = session("session-6");
        when(session.isOpen()).thenReturn(true);
        manager.add(session, loginUser());

        manager.subscribe(session.getId(), List.of("device-1", "device-2"));
        manager.subscribe(session.getId(), List.of("device-2"));

        assertTrue(manager.sessionsByDevice("device-1").isEmpty());
        assertFalse(manager.sessionsByDevice("device-2").isEmpty());

        manager.subscribe(session.getId(), List.of());

        assertTrue(manager.sessionsByDevice("device-2").isEmpty());
    }

    @Test
    void unbindClosesAllUserSessionsSubscribedToDevice() throws Exception {
        WebSocketSessionManager manager = new WebSocketSessionManager();
        WebSocketSession first = session("session-7");
        WebSocketSession second = session("session-8");
        when(first.isOpen()).thenReturn(true);
        when(second.isOpen()).thenReturn(true);
        manager.add(first, loginUser());
        manager.add(second, loginUser());
        manager.subscribe(first.getId(), List.of("device-1"));
        manager.subscribe(second.getId(), List.of("device-1"));

        manager.closeUserSessions("user-1");

        assertTrue(manager.sessionsByDevice("device-1").isEmpty());
        verify(first).close(CloseStatus.POLICY_VIOLATION);
        verify(second).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void blockedSocketWriteCannotBlockCallerIndefinitely() throws Exception {
        WebSocketSessionManager manager = new WebSocketSessionManager();
        WebSocketSession session = session("session-9");
        CountDownLatch release = new CountDownLatch(1);
        when(session.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            while (release.getCount() > 0) {
                try {
                    release.await(20, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ignored) {
                    // Simulate a socket write that does not react to interruption.
                }
            }
            return null;
        }).when(session).sendMessage(any());
        manager.add(session, loginUser());
        manager.subscribe(session.getId(), List.of("device-1"));
        WebSocketPushService pushService = pushService(manager, 50);

        try {
            assertTimeoutPreemptively(Duration.ofSeconds(1),
                    () -> assertFalse(pushService.pushDeviceStatus("device-1", "ONLINE", null)));
            assertTimeoutPreemptively(Duration.ofSeconds(1),
                    () -> assertFalse(pushService.pushDeviceStatus("device-1", "ONLINE", null)));
        } finally {
            release.countDown();
        }
    }

    private WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        return session;
    }

    private LoginUser loginUser() {
        return new LoginUser(1L, "user-1", "USER");
    }

    private WebSocketPushService pushService(WebSocketSessionManager manager, long timeoutMillis) {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        executors.add(executor);
        return new WebSocketPushService(manager, new ObjectMapper(), executor, timeoutMillis);
    }
}
