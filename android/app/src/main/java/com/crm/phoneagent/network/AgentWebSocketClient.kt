package com.crm.phoneagent.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.random.Random

/**
 * Owns the single WebSocket connection to the CRM, with capped exponential backoff so a
 * server restart or dead network never turns into a hot retry loop (Android will also throttle
 * apps that hammer the network in the background, so this is not just etiquette). One
 * connection attempt is in flight at a time; callers only ever see onOpen/onMessage/onClosed
 * through the listener, never raw socket lifecycle races.
 */
class AgentWebSocketClient(
    private val wsUrl: String,
    private val deviceToken: String,
    private val listener: Listener,
) {
    interface Listener {
        fun onConnected()
        fun onMessage(raw: String)
        fun onDisconnected(reason: String)
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket: no read timeout, PING/PONG governs liveness
        .pingInterval(30, TimeUnit.SECONDS)     // protocol-level WebSocket ping (in addition to our own HEARTBEAT)
        .build()

    private val scope = CoroutineScope(SupervisorJob())
    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var stopped = false
    private var attempt = 0

    fun start() {
        stopped = false
        connectNow()
    }

    fun send(text: String): Boolean = socket?.send(text) ?: false

    fun stop() {
        stopped = true
        reconnectJob?.cancel()
        socket?.close(1000, "Client stopping")
        socket = null
        scope.cancel()
    }

    private fun connectNow() {
        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Authorization", "Bearer $deviceToken")
            .build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                attempt = 0
                listener.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                listener.onMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener.onDisconnected("Closed by server: $reason")
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure: ${t.message}")
                listener.onDisconnected(t.message ?: "Connection failed")
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (stopped) return
        reconnectJob?.cancel()
        attempt += 1
        val delayMs = nextBackoffDelayMs(attempt) + Random.nextLong(0, 1000)
        reconnectJob = scope.launch {
            delay(delayMs)
            if (isActive && !stopped) connectNow()
        }
    }

    private companion object {
        const val TAG = "AgentWebSocket"
    }
}

/**
 * Pure backoff schedule (jitter added by the caller): 2s, 4s, 8s, 16s, 30s, 30s, 30s... - capped
 * so a long outage never turns into minutes-long silence, but a flaky network never turns into a
 * hot retry loop either. Extracted as a top-level function so it is testable on the plain JVM
 * with no Android framework/emulator involved.
 */
fun nextBackoffDelayMs(attempt: Int): Long {
    val capped = attempt.coerceIn(1, 6)
    return min(1000L * (1L shl capped), 30_000L)
}
