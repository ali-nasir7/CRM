package com.crm.modules.calling.ws;

import com.crm.modules.calling.service.CallingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.UUID;

/**
 * Server side of the CRM <-> Phone Agent control channel. One session per device (a new
 * connection for the same device replaces the old one - see AgentSessionRegistry.register).
 * Audio/voice never crosses this channel: it carries only small JSON control messages
 * (CALL_REQUEST out, CALL_STATE/HEARTBEAT in) - the SIM call itself happens on the phone's own
 * cellular radio.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentWebSocketHandler extends TextWebSocketHandler {

    private final AgentSessionRegistry registry;
    private final CallingService calling;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID deviceId = deviceId(session);
        registry.register(deviceId, session);
        calling.agentConnected(deviceId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        UUID deviceId = deviceId(session);
        JsonNode node;
        try {
            node = mapper.readTree(message.getPayload());
        } catch (Exception e) {
            log.warn("Malformed message from device {}: {}", deviceId, e.getMessage());
            return;
        }
        String type = node.path("type").asText("");
        switch (type) {
            case "HEARTBEAT" -> calling.agentHeartbeat(deviceId);
            case "PONG" -> {
                calling.agentHeartbeat(deviceId);
                registry.completePendingTest(deviceId, true);
            }
            case "HELLO" -> {
                calling.agentHeartbeat(deviceId);
                calling.updateAgentMeta(deviceId, node.path("appVersion").asText(null), node.path("deviceModel").asText(null));
            }
            case "CALL_STATE" -> {
                String callId = node.path("callId").asText(null);
                String state = node.path("state").asText(null);
                if (callId == null || state == null) {
                    log.warn("CALL_STATE missing callId/state from device {}", deviceId);
                    break;
                }
                try {
                    calling.applyBridgeState(callId, state);
                    log.info("CALL_STATE deviceId={} callId={} state={}", deviceId, callId, state);
                } catch (Exception e) {
                    log.warn("Failed to apply call state {} for {}: {}", state, callId, e.getMessage());
                }
            }
            default -> log.debug("Unhandled message type '{}' from device {}", type, deviceId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID deviceId = deviceId(session);
        registry.unregister(deviceId, session);
        calling.agentDisconnected(deviceId);
    }

    private UUID deviceId(WebSocketSession session) {
        return (UUID) session.getAttributes().get(AgentHandshakeInterceptor.ATTR_DEVICE_ID);
    }
}
