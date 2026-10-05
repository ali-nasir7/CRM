package com.crm.phoneagent.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.crm.phoneagent.BuildConfig
import com.crm.phoneagent.PhoneAgentApplication
import com.crm.phoneagent.network.ServerEndpoints

@Composable
fun WelcomeScreen(onPairDevice: () -> Unit) {
    val context = LocalContext.current
    val store = (context.applicationContext as PhoneAgentApplication).deviceStore
    var showServerSettings by remember { mutableStateOf(false) }
    var apiUrl by remember { mutableStateOf(store.apiBaseUrl(BuildConfig.DEFAULT_API_BASE_URL)) }
    var wsUrl by remember { mutableStateOf(store.wsBaseUrl(BuildConfig.DEFAULT_WS_BASE_URL)) }
    var serverError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(84.dp)) {
            Icon(
                Icons.Filled.Phone, contentDescription = null, tint = Color.White,
                modifier = Modifier.padding(22.dp).size(40.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text("CRM Phone Agent", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "Connect your phone to your CRM and use your SIM for CRM calls.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(36.dp))
        Button(
            onClick = {
                try {
                    val endpoints = ServerEndpoints.parse(apiUrl, wsUrl)
                    store.saveServerConfig(endpoints.apiBaseUrl, endpoints.wsBaseUrl)
                    serverError = null
                    onPairDevice()
                } catch (e: IllegalArgumentException) {
                    serverError = e.message ?: "Enter valid CRM server URLs"
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Text("Pair Device", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showServerSettings = !showServerSettings }) {
                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.height(0.dp))
                Text("  Server settings", style = MaterialTheme.typography.labelLarge)
            }
        }
        if (showServerSettings) {
            ServerSettingsFields(apiUrl, { apiUrl = it }, wsUrl, { wsUrl = it })
        }
        serverError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun ServerSettingsFields(
    apiUrl: String,
    onApiUrlChange: (String) -> Unit,
    wsUrl: String,
    onWsUrlChange: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        androidx.compose.material3.OutlinedTextField(
            value = apiUrl, onValueChange = onApiUrlChange,
            label = { Text("API base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.OutlinedTextField(
            value = wsUrl, onValueChange = onWsUrlChange,
            label = { Text("WebSocket base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Only change this for a self-hosted or staging CRM. Most reps should leave the default.",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
