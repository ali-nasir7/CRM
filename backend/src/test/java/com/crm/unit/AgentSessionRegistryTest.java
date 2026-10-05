package com.crm.unit;

import com.crm.modules.calling.ws.AgentSessionRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The registry is the backend's single source of truth for "is this device connected right
 * now" and the transport for the CALL_REQUEST/PING round trips - correctness here directly
 * gates whether a call can be dispatched twice or a dead session reports falsely ONLINE.
 */
class AgentSessionRegistryTest {

    private WebSocketSession session(String id, boolean open) {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.getId()).thenReturn(id);
        when(s.isOpen()).thenReturn(open);
        return s;
    }

    @Test
    void deviceIsOfflineUntilRegistered() {
        AgentSessionRegistry registry = new AgentSessionRegistry();
        UUID deviceId = UUID.randomUUID();
        assertThat(registry.isOnline(deviceId)).isFalse();

        registry.register(deviceId, session("s1", true));
        assertThat(registry.isOnline(deviceId)).isTrue();
    }

    @Test
    void newConnectionForSameDeviceClosesThePreviousOne() throws Exception {
        AgentSessionRegistry registry = new AgentSessionRegistry();
        UUID deviceId = UUID.randomUUID();
        WebSocketSession first = session("s1", true);
        WebSocketSession second = session("s2", true);

        registry.register(deviceId, first);
        registry.register(deviceId, second);

        verify(first).close(any(CloseStatus.class));
        assertThat(registry.isOnline(deviceId)).isTrue(); // second session still counts as online
    }

    @Test
    void unregisterOnlyRemovesTheMatchingSession() {
        AgentSessionRegistry registry = new AgentSessionRegistry();
        UUID deviceId = UUID.randomUUID();
        WebSocketSession first = session("s1", true);
        WebSocketSession second = session("s2", true);

        registry.register(deviceId, first);
        registry.register(deviceId, second); // replaces first
        registry.unregister(deviceId, first); // stale close event for the OLD session: no-op

        assertThat(registry.isOnline(deviceId)).isTrue();
    }

    @Test
    void sendReturnsFalseWhenDeviceOffline() {
        AgentSessionRegistry registry = new AgentSessionRegistry();
        assertThat(registry.send(UUID.randomUUID(), "{}")).isFalse();
    }

    @Test
    void sendDeliversExactlyOneMessage() throws Exception {
        AgentSessionRegistry registry = new AgentSessionRegistry();
        UUID deviceId = UUID.randomUUID();
        WebSocketSession s = session("s1", true);
        registry.register(deviceId, s);

        boolean sent = registry.send(deviceId, "{\"type\":\"CALL_REQUEST\"}");

        assertThat(sent).isTrue();
        verify(s, times(1)).sendMessage(new TextMessage("{\"type\":\"CALL_REQUEST\"}"));
    }

    @Test
    void testConnection_resolvesFalseImmediatelyWhenOffline() throws ExecutionException, InterruptedException, TimeoutException {
        AgentSessionRegistry registry = new AgentSessionRegistry();
        boolean result = registry.testConnection(UUID.randomUUID()).get(1, TimeUnit.SECONDS);
        assertThat(result).isFalse();
    }

    @Test
    void testConnection_resolvesTrueWhenPongArrives() throws Exception {
        AgentSessionRegistry registry = new AgentSessionRegistry();
        UUID deviceId = UUID.randomUUID();
        registry.register(deviceId, session("s1", true));

        var future = registry.testConnection(deviceId);
        registry.completePendingTest(deviceId, true); // simulates the handler routing an inbound PONG

        assertThat(future.get(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void disconnect_closesSessionAndMarksOffline() throws Exception {
        AgentSessionRegistry registry = new AgentSessionRegistry();
        UUID deviceId = UUID.randomUUID();
        WebSocketSession s = session("s1", true);
        registry.register(deviceId, s);

        registry.disconnect(deviceId, "Device revoked");

        assertThat(registry.isOnline(deviceId)).isFalse();
        verify(s).close(any(CloseStatus.class));
    }
}
