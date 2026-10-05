package com.crm.phoneagent.network

import org.json.JSONObject

/**
 * The full control-channel protocol shared with the backend (see
 * com.crm.modules.calling.ws.AgentWebSocketHandler). Plain JSON, no framework: org.json ships
 * with Android, so this needs no extra dependency or Kotlin compiler plugin.
 */
sealed interface InboundMessage {
    data class CallRequest(val callId: String, val phoneNumber: String, val customerName: String?) : InboundMessage
    data object Ping : InboundMessage
    data object Revoked : InboundMessage
    data class Unknown(val type: String) : InboundMessage

    companion object {
        fun parse(raw: String): InboundMessage {
            val json = JSONObject(raw)
            return when (val type = json.optString("type")) {
                "CALL_REQUEST" -> CallRequest(
                    callId = json.getString("callId"),
                    phoneNumber = json.getString("phoneNumber"),
                    customerName = json.optString("customerName").ifBlank { null },
                )
                "PING" -> Ping
                "REVOKED" -> Revoked
                else -> Unknown(type)
            }
        }
    }
}

object OutboundMessage {
    fun hello(appVersion: String, deviceModel: String): String =
        JSONObject().put("type", "HELLO").put("appVersion", appVersion).put("deviceModel", deviceModel).toString()

    fun heartbeat(): String = JSONObject().put("type", "HEARTBEAT").toString()

    fun pong(): String = JSONObject().put("type", "PONG").toString()

    fun callState(callId: String, state: CallState): String =
        JSONObject().put("type", "CALL_STATE").put("callId", callId).put("state", state.wireName).toString()
}

/** Mirrors CallingService.applyBridgeState's accepted vocabulary exactly - sending anything
 *  else is a protocol bug, not just a display difference. */
enum class CallState(val wireName: String) {
    RINGING("RINGING"),
    CONNECTED("CONNECTED"),
    ENDED("ENDED"),
    NO_ANSWER("NO_ANSWER"),
    BUSY("BUSY"),
    FAILED("FAILED"),
}
