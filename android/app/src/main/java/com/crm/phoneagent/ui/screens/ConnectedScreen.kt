package com.crm.phoneagent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.crm.phoneagent.BuildConfig
import com.crm.phoneagent.PhoneAgentApplication
import com.crm.phoneagent.service.AgentStatus
import com.crm.phoneagent.service.Phase
import com.crm.phoneagent.service.PhoneAgentConnectionService
import com.crm.phoneagent.ui.components.ConfirmDisconnectDialog
import java.time.format.DateTimeFormatter

@Composable
fun ConnectedScreen(onDisconnected: () -> Unit) {
    val context = LocalContext.current
    val store = (context.applicationContext as PhoneAgentApplication).deviceStore
    val state by AgentStatus.state.collectAsState()
    val activity by AgentStatus.activity.collectAsState()
    var showDisconnectConfirm by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        StatusHeader(phase = state.phase)

        if (state.phase == Phase.RECONNECTING) {
            Spacer(Modifier.height(12.dp))
            ConnectionLostBanner(reason = state.lastError, onRetry = {
                PhoneAgentConnectionService.stop(context)
                PhoneAgentConnectionService.start(context)
            })
        }

        Spacer(Modifier.height(20.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                InfoRow("Account", store.userDisplayName() ?: "—")
                InfoRow("Device", store.deviceName() ?: "—")
                InfoRow("Status", if (state.phase == Phase.ONLINE) "Online" else "Offline")
                InfoRow("App version", BuildConfig.VERSION_NAME)
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    testing = true
                    PhoneAgentConnectionService.stop(context)
                    PhoneAgentConnectionService.start(context)
                },
                modifier = Modifier.weight(1f),
            ) {
                if (testing && state.phase != Phase.ONLINE) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.height(0.dp))
                Text(" Test Connection")
            }
            OutlinedButton(
                onClick = { showDisconnectConfirm = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Disconnect") }
        }

        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(0.dp))
            Text(" Recent activity", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        if (activity.isEmpty()) {
            Text("Nothing yet - this updates as calls come in.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(activity.asReversed()) { entry ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            DateTimeFormatter.ofPattern("HH:mm:ss").format(entry.at.atZone(java.time.ZoneId.systemDefault())),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(0.dp))
                        Text("  " + entry.message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    if (showDisconnectConfirm) {
        ConfirmDisconnectDialog(
            onConfirm = {
                showDisconnectConfirm = false
                PhoneAgentConnectionService.stop(context)
                store.clearPairing()
                onDisconnected()
            },
            onDismiss = { showDisconnectConfirm = false },
        )
    }
}

@Composable
private fun StatusHeader(phase: Phase) {
    val (color, label) = when (phase) {
        Phase.ONLINE -> MaterialTheme.colorScheme.primary to "Connected"
        Phase.CONNECTING -> MaterialTheme.colorScheme.onSurfaceVariant to "Connecting…"
        Phase.RECONNECTING -> MaterialTheme.colorScheme.error to "Reconnecting…"
        Phase.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant to "Disconnected"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (phase == Phase.ONLINE) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = color, modifier = Modifier.size(30.dp))
        } else {
            Box(Modifier.size(14.dp).background(color, CircleShape))
        }
        Spacer(Modifier.height(0.dp))
        Text("  $label", style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun ConnectionLostBanner(reason: String?, onRetry: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.WifiOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text("Connection lost", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                Text("Trying to reconnect automatically…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            OutlinedButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
