package com.crm.modules.calling.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Live WebSocket sessions for connected Phone Agent devices, keyed by CallingDevice id.
 * This is the backend's single authoritative source for "is this device online right now" -
 * lastSeenAt/status in Postgres are a persisted echo of what happens here, refreshed on every
 * connect/disconnect/heartbeat.
 *
 * Single-instance in-memory registry: correct for one backend process. A multi-instance
 * deployment would need to make this shared (e.g. Redis pub/sub fan-out of CALL_REQUEST to
 * whichever instance holds the session) - see the Known Limitations section of the final report.
 */
@Slf4j
@Component
public class AgentSessionRegistry {

    private final Map<UUID, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Boolean>> pendingTests = new ConcurrentHashMap<>();

    public void register(UUID deviceId, WebSocketSession session) {
        WebSocketSession previous = sessions.put(deviceId, session);
        if (previous != null && previous.isOpen() && !previous.getId().equals(session.getId())) {
            closeQuietly(previous, CloseStatus.POLICY_VIOLATION.withReason("Replaced by a new connection"));
        }
    }

    public void unregister(UUID deviceId, WebSocketSession session) {
        sessions.computeIfPresent(deviceId, (id, current) -> current.getId().equals(session.getId()) ? null : current);
    }

    public boolean isOnline(UUID deviceId) {
        WebSocketSession s = sessions.get(deviceId);
        return s != null && s.isOpen();
    }

    /** @return false if the device has no live session (caller should treat this as PHONE_OFFLINE). */
    public boolean send(UUID deviceId, String json) {
        WebSocketSession s = sessions.get(deviceId);
        if (s == null || !s.isOpen()) return false;
        try {
            synchronized (s) { // TextWebSocketHandler sessions are not safe for concurrent writes
                s.sendMessage(new TextMessage(json));
            }
            return true;
        } catch (IOException e) {
            log.warn("Failed to send to device {}: {}", deviceId, e.getMessage());
            return false;
        }
    }

    /**
     * "Test Connection" button: a genuine round trip, not a cached status flag. Sends PING and
     * waits (up to 5s) for the matching PONG the handler routes back here - if the device is
     * physically unreachable (killed process, dead network) this honestly resolves false instead
     * of reporting stale ONLINE just because the TCP socket has not noticed yet.
     */
    public CompletableFuture<Boolean> testConnection(UUID deviceId) {
        if (!isOnline(deviceId)) return CompletableFuture.completedFuture(false);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        pendingTests.put(deviceId, future);
        if (!send(deviceId, "{\"type\":\"PING\"}")) {
            pendingTests.remove(deviceId);
            return CompletableFuture.completedFuture(false);
        }
        return future.orTimeout(5, TimeUnit.SECONDS).exceptionally(ex -> false)
            .whenComplete((r, ex) -> pendingTests.remove(deviceId));
    }

    public void completePendingTest(UUID deviceId, boolean ok) {
        CompletableFuture<Boolean> f = pendingTests.remove(deviceId);
        if (f != null) f.complete(ok);
    }

    /** Used when an admin/owner revokes a device: disconnect it immediately, do not wait for TTL. */
    public void disconnect(UUID deviceId, String reason) {
        WebSocketSession s = sessions.remove(deviceId);
        if (s != null) closeQuietly(s, CloseStatus.NORMAL.withReason(reason));
    }

    private void closeQuietly(WebSocketSession s, CloseStatus status) {
        try { s.close(status); } catch (IOException ignored) { /* already gone */ }
    }
}
