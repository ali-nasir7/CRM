package com.crm.modules.calling.ws;

import com.crm.modules.calling.domain.CallingDevice;
import com.crm.modules.calling.service.DevicePairingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Authenticates the Phone Agent's device token BEFORE the WebSocket upgrade completes - an
 * invalid or revoked token never reaches the handler at all. This is deliberately NOT a CRM user
 * JWT: a phone that is lost/reset/factory-wiped must not be able to keep dialing just because a
 * user token happened to still be valid, and a revoked device must be rejected here even if it
 * still knows a perfectly well-formed token.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_DEVICE_ID = "deviceId";
    public static final String ATTR_ORG_ID = "orgId";
    public static final String ATTR_USER_ID = "userId";

    private final DevicePairingService pairing;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request);
        CallingDevice device = pairing.authenticateDeviceToken(token);
        if (device == null) {
            log.warn("DEVICE_AUTH_FAILED remote={}", request.getRemoteAddress());
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(ATTR_DEVICE_ID, device.getId());
        attributes.put(ATTR_ORG_ID, device.getOrganizationId());
        attributes.put(ATTR_USER_ID, device.getUserId());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // nothing to do
    }

    private String extractToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst("Authorization");
        if (header != null && header.startsWith("Bearer ")) return header.substring(7).trim();
        String q = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("token");
        return q != null && !q.isBlank() ? q.trim() : null;
    }
}
