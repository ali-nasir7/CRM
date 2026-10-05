package com.crm.phoneagent.telephony

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.crm.phoneagent.network.CallState
import kotlinx.coroutines.CoroutineScope

/**
 * Places the real cellular call. This is the ONLY thing in the app that touches the dialer, and
 * it does so through the platform's own native telephony intent - never adb, never an
 * Accessibility Service pretending to tap a Call button, never a simulated UI gesture.
 *
 * ACTION_CALL (not ACTION_DIAL) genuinely places the call by itself, the instant CALL_PHONE is
 * granted - nobody has to tap anything in a dialer UI. That is both the point of a phone agent
 * app and the thing that makes CALL_PHONE a sensitive, explicitly-explained permission on the
 * Permissions screen.
 */
class CallExecutor(
    private val context: Context,
    scope: CoroutineScope,
    private val idempotency: CallIdempotencyStore,
    /** Sends a CALL_STATE frame back to the CRM over the live WebSocket. */
    private val sendState: (callId: String, state: CallState) -> Unit,
) {
    private val stateReporter = CallStateReporter(context, scope, ::reportState)
    private var activeCallId: String? = null

    fun hasCallPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    /** @return true if a NEW call was dispatched; false if it was ignored (duplicate/busy) or
     *  failed outright (permission missing, no dialer to handle the intent). Either way the
     *  caller has already been told the resulting state via sendState. */
    fun execute(callId: String, phoneNumber: String): Boolean {
        if (idempotency.alreadyProcessed(callId)) {
            Log.i(TAG, "Ignoring duplicate CALL_REQUEST for $callId (already processed)")
            return false // no state re-sent: the backend already has whatever the first attempt reported
        }
        if (activeCallId != null) {
            Log.w(TAG, "Rejecting $callId: device already has call $activeCallId in progress")
            idempotency.markProcessed(callId)
            reportState(callId, CallState.FAILED)
            return false
        }
        if (!hasCallPermission()) {
            Log.w(TAG, "CALL_PHONE not granted - cannot dial $callId")
            idempotency.markProcessed(callId)
            reportState(callId, CallState.FAILED)
            return false
        }

        idempotency.markProcessed(callId)
        activeCallId = callId
        return try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(phoneNumber)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Dual-SIM: if the rep has a default calling SIM configured at the OS level, attach it
            // so ACTION_CALL dials immediately instead of Android popping its own SIM-chooser
            // dialog (which would violate "no manual tap"). No default configured = the OS still
            // shows its picker; there is no API to silently guess the right SIM for a business call.
            defaultOutgoingPhoneAccount()?.let { intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it) }
            context.startActivity(intent)
            reportState(callId, CallState.RINGING) // dispatched to the radio; not yet confirmed answered
            stateReporter.track(callId)
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Dial blocked by the OS despite granted permission: ${e.message}")
            reportState(callId, CallState.FAILED)
            false
        } catch (e: android.content.ActivityNotFoundException) {
            Log.e(TAG, "No activity can handle ACTION_CALL on this device: ${e.message}")
            reportState(callId, CallState.FAILED)
            false
        }
    }

    /** Single funnel for every outgoing CALL_STATE: keeps "is a call currently active" bookkeeping
     *  and the actual wire send from ever drifting apart. */
    private fun reportState(callId: String, state: CallState) {
        if (activeCallId == callId && isTerminal(state)) activeCallId = null
        sendState(callId, state)
    }

    private fun isTerminal(state: CallState) = state != CallState.RINGING && state != CallState.CONNECTED

    private fun defaultOutgoingPhoneAccount() = try {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        telecomManager?.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)
    } catch (e: SecurityException) {
        null // no READ_PHONE_STATE (shouldn't happen - hasCallPermission gated us in) or restricted OEM build
    }

    private companion object {
        const val TAG = "CallExecutor"
    }
}
