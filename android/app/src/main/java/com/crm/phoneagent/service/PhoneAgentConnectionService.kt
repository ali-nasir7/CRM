package com.crm.phoneagent.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.crm.phoneagent.BuildConfig
import com.crm.phoneagent.MainActivity
import com.crm.phoneagent.PhoneAgentApplication
import com.crm.phoneagent.R
import com.crm.phoneagent.network.AgentWebSocketClient
import com.crm.phoneagent.network.CallState
import com.crm.phoneagent.network.InboundMessage
import com.crm.phoneagent.network.OutboundMessage
import com.crm.phoneagent.telephony.CallExecutor
import com.crm.phoneagent.telephony.CallIdempotencyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The whole reason this app runs as a foreground service: WITHOUT one, Android suspends the
 * process's network access within seconds/minutes of the app leaving the foreground (Doze,
 * background execution limits), and the CRM's Call button would silently stop reaching the
 * phone. A foreground service with a visible notification is the honest, documented way to keep
 * a small persistent connection alive - this is exactly the same mechanism VoIP/messaging apps
 * use, not a workaround.
 */
class PhoneAgentConnectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob())
    private var socket: AgentWebSocketClient? = null
    private var callExecutor: CallExecutor? = null
    private var heartbeatJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            // ServiceCompat picks the right underlying platform call for the running OS version
            // (2-arg startForeground pre-Q, 3-arg with this type from Q on) - no manual SDK_INT
            // branch needed here.
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification("Connecting…"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } catch (e: Exception) {
            // The OS can legitimately refuse a foreground-service start (background start
            // restrictions on some OEMs, etc.) - surface it instead of crashing the process,
            // since a crash here would take the whole app down before the socket ever opens.
            Log.e(TAG, "startForeground refused: ${e.message}", e)
            AgentStatus.update(Phase.DISCONNECTED, error = "Could not start the connection service: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }
        connect()
        return START_STICKY
    }

    override fun onDestroy() {
        heartbeatJob?.cancel()
        socket?.stop()
        scope.cancel()
        AgentStatus.update(Phase.DISCONNECTED)
        super.onDestroy()
    }

    private fun connect() {
        val app = application as PhoneAgentApplication
        val store = app.deviceStore
        val token = store.deviceToken()
        if (token == null) {
            stopSelf() // not paired (any more) - nothing to connect for
            return
        }
        val wsUrl = store.wsBaseUrl(BuildConfig.DEFAULT_WS_BASE_URL) + "/ws/agent"

        val idempotency = CallIdempotencyStore(this)
        val client = AgentWebSocketClient(
            wsUrl = wsUrl,
            deviceToken = token,
            listener = object : AgentWebSocketClient.Listener {
                override fun onConnected() {
                    AgentStatus.update(Phase.ONLINE)
                    AgentStatus.log("Connected to CRM")
                    updateNotification("Online")
                    socket?.send(OutboundMessage.hello(BuildConfig.VERSION_NAME, Build.MODEL))
                    startHeartbeat()
                }

                override fun onMessage(raw: String) {
                    handleMessage(raw)
                }

                override fun onDisconnected(reason: String) {
                    heartbeatJob?.cancel()
                    AgentStatus.update(Phase.RECONNECTING, error = reason)
                    updateNotification("Reconnecting…")
                }
            },
        )
        socket = client
        callExecutor = CallExecutor(this, scope, idempotency) { callId, state ->
            socket?.send(OutboundMessage.callState(callId, state))
            AgentStatus.log(activityLabel(state))
        }
        AgentStatus.update(Phase.CONNECTING)
        client.start()
    }

    private fun handleMessage(raw: String) {
        when (val message = InboundMessage.parse(raw)) {
            is InboundMessage.CallRequest -> {
                AgentStatus.log("Call request received" + (message.customerName?.let { " ($it)" } ?: ""))
                callExecutor?.execute(message.callId, message.phoneNumber)
            }
            InboundMessage.Ping -> socket?.send(OutboundMessage.pong())
            InboundMessage.Revoked -> {
                AgentStatus.log("This device was disconnected from the CRM")
                (application as PhoneAgentApplication).deviceStore.clearPairing()
                stopSelf()
            }
            is InboundMessage.Unknown -> { /* forward-compatible: ignore message types this build predates */ }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(30_000)
                socket?.send(OutboundMessage.heartbeat())
            }
        }
    }

    private fun activityLabel(state: CallState) = when (state) {
        CallState.RINGING -> "Calling…"
        CallState.CONNECTED -> "Call connected"
        CallState.ENDED -> "Call ended"
        CallState.NO_ANSWER -> "Call not answered"
        CallState.BUSY -> "Line busy"
        CallState.FAILED -> "Call failed"
    }

    private fun buildNotification(statusText: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call) // no extra vector asset needed for a status-bar glyph
            .setOngoing(true)
            .setContentIntent(openApp)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    companion object {
        const val CHANNEL_ID = "phone_agent_connection"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "PhoneAgentConnection"

        fun start(context: Context) {
            val intent = Intent(context, PhoneAgentConnectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PhoneAgentConnectionService::class.java))
        }
    }
}
