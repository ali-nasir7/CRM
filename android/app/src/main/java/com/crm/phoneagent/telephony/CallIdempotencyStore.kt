package com.crm.phoneagent.telephony

import android.content.Context

/**
 * Defense-in-depth against dialing the same callId twice - the backend already refuses to
 * dispatch a second CALL_REQUEST while a device has a call in progress, but a reconnect
 * replaying an unacknowledged message, or a future retry path, must not be able to place a
 * duplicate real phone call. Survives process death (plain, non-secret SharedPreferences -
 * call ids are random UUIDs, not sensitive).
 */
class CallIdempotencyStore(context: Context) {

    private val prefs = context.getSharedPreferences("phone_agent_call_ids", Context.MODE_PRIVATE)

    fun alreadyProcessed(callId: String): Boolean = seen().contains(callId)

    fun markProcessed(callId: String) {
        val updated = (seen() + callId).toList().takeLast(MAX_REMEMBERED)
        prefs.edit().putString(KEY, updated.joinToString(",")).apply()
    }

    private fun seen(): Set<String> =
        prefs.getString(KEY, null)?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    private companion object {
        const val KEY = "processed_call_ids"
        const val MAX_REMEMBERED = 50
    }
}
