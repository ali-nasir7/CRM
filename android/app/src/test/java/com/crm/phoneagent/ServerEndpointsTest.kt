package com.crm.phoneagent

import com.crm.phoneagent.network.ServerEndpoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerEndpointsTest {
    @Test fun acceptsConfiguredLanDevelopmentEndpoints() {
        val endpoints = ServerEndpoints.parse("http://192.168.100.4:5173/api/v1/", "ws://192.168.100.4:5173/")
        assertEquals("http://192.168.100.4:5173/api/v1", endpoints.apiBaseUrl)
        assertEquals("ws://192.168.100.4:5173", endpoints.wsBaseUrl)
    }

    @Test fun rejectsMixedSecuritySchemes() {
        assertThrows(IllegalArgumentException::class.java) { ServerEndpoints.parse("https://crm.example.com/api/v1", "ws://crm.example.com") }
    }

    @Test fun rejectsWebSocketPathBecauseTheAgentOwnsIt() {
        assertThrows(IllegalArgumentException::class.java) { ServerEndpoints.parse("http://192.168.100.4:5173/api/v1", "ws://192.168.100.4:5173/ws/agent") }
    }
}