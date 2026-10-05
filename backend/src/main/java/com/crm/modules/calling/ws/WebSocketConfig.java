package com.crm.modules.calling.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the single Phone Agent control-channel endpoint. Plain WebSocket (no STOMP/SockJS):
 * the protocol is a handful of small JSON message types, so a message broker would be pure
 * overhead. Origin checking is intentionally permissive here (setAllowedOriginPatterns("*")) -
 * unlike the browser CRM frontend, the Android client never sends a browser Origin header at
 * all, and the real authentication boundary is the device token checked in the handshake
 * interceptor, not the Origin header.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    public static final String AGENT_PATH = "/ws/agent";

    private final AgentWebSocketHandler agentWebSocketHandler;
    private final AgentHandshakeInterceptor handshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(agentWebSocketHandler, AGENT_PATH)
            .addInterceptors(handshakeInterceptor)
            .setAllowedOriginPatterns("*");
    }
}
