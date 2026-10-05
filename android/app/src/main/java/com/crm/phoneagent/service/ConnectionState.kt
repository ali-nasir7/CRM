package com.crm.phoneagent.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant

enum class Phase { DISCONNECTED, CONNECTING, ONLINE, RECONNECTING }

data class AgentState(
    val phase: Phase = Phase.DISCONNECTED,
    val lastConnectedAt: Instant? = null,
    val lastError: String? = null,
)

data class ActivityEntry(val message: String, val at: Instant = Instant.now())

/**
 * Process-wide, observed by both the foreground Service (which owns the socket/telephony) and
 * the Compose UI (which only ever reads it) - the UI never talks to the socket directly, so
 * there is exactly one thing driving connection state.
 */
object AgentStatus {
    private val _state = MutableStateFlow(AgentState())
    val state: StateFlow<AgentState> = _state

    private val _activity = MutableStateFlow<List<ActivityEntry>>(emptyList())
    val activity: StateFlow<List<ActivityEntry>> = _activity

    fun update(phase: Phase, error: String? = null) {
        _state.update {
            it.copy(
                phase = phase,
                lastConnectedAt = if (phase == Phase.ONLINE) Instant.now() else it.lastConnectedAt,
                lastError = error,
            )
        }
    }

    fun log(message: String) {
        _activity.update { (it + ActivityEntry(message)).takeLast(MAX_ENTRIES) }
    }

    private const val MAX_ENTRIES = 20
}
