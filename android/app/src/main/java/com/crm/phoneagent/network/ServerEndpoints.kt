package com.crm.phoneagent.network

import com.crm.phoneagent.BuildConfig
import java.net.URI

/** Validates the REST and control-channel endpoints as one server configuration. */
data class ServerEndpoints(val apiBaseUrl: String, val wsBaseUrl: String) {
    companion object {
        private const val API_PATH = "/api/v1"

        fun parse(apiInput: String, wsInput: String): ServerEndpoints {
            val api = parseUri(apiInput, "API base URL")
            val ws = parseUri(wsInput, "WebSocket base URL")
            require(api.scheme in setOf("http", "https")) { "API URL must start with http:// or https://" }
            require(ws.scheme in setOf("ws", "wss")) { "WebSocket URL must start with ws:// or wss://" }
            require(api.host != null && ws.host != null) { "Server URLs must include a host" }
            require(api.path.trimEnd('/') == API_PATH) { "API URL must end with $API_PATH" }
            require(ws.path.isNullOrBlank() || ws.path == "/") { "WebSocket URL must not include a path" }
            require(api.query == null && api.fragment == null && ws.query == null && ws.fragment == null) {
                "Server URLs cannot include a query or fragment"
            }
            require((api.scheme == "https") == (ws.scheme == "wss")) {
                "Use HTTPS with WSS, or HTTP with WS"
            }
            if (!BuildConfig.DEBUG) require(api.scheme == "https") { "Release builds require HTTPS and WSS" }
            return ServerEndpoints(api.toString().trimEnd('/'), ws.toString().trimEnd('/'))
        }

        private fun parseUri(value: String, label: String): URI = try {
            URI(value.trim())
        } catch (_: Exception) {
            throw IllegalArgumentException("$label is not a valid URL")
        }
    }
}