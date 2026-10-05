package com.crm.phoneagent.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.crm.phoneagent.ui.AgentPermissions

private data class PermissionInfo(
    val permission: String,
    val icon: ImageVector,
    val title: String,
    val rationale: String,
)

@Composable
fun PermissionsScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    val infos = remember {
        buildList {
            add(PermissionInfo(Manifest.permission.CALL_PHONE, Icons.Filled.Phone, "Make phone calls",
                "Required. This is what lets the CRM place a call on this phone's SIM when someone clicks Call."))
            add(PermissionInfo(Manifest.permission.READ_PHONE_STATE, Icons.Filled.PhoneAndroid, "Read call state",
                "Recommended. Lets the app tell the CRM when a call actually connects or ends, instead of only that it was dialed."))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(PermissionInfo(Manifest.permission.POST_NOTIFICATIONS, Icons.Filled.NotificationsActive, "Show a status notification",
                    "Recommended. Android requires a visible notification to keep the connection alive in the background."))
            }
        }
    }

    var grantedMap by remember {
        mutableStateOf(infos.associate { it.permission to AgentPermissions.isGranted(context, it.permission) })
    }
    var batteryExempt by remember { mutableStateOf(AgentPermissions.isIgnoringBatteryOptimizations(context)) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        grantedMap = grantedMap + result
    }
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        batteryExempt = AgentPermissions.isIgnoringBatteryOptimizations(context)
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Permissions", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "CRM Phone Agent only asks for what it needs to place and report calls.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(infos) { info ->
                PermissionRow(info, granted = grantedMap[info.permission] == true)
            }
            item {
                BatteryRow(batteryExempt) {
                    batteryLauncher.launch(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        val ungranted = infos.filter { grantedMap[it.permission] != true }
        if (ungranted.isNotEmpty()) {
            Button(
                onClick = { launcher.launch(ungranted.map { it.permission }.toTypedArray()) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Grant permissions", style = MaterialTheme.typography.titleMedium) }
        } else {
            OutlinedButton(
                onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open app settings") }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onContinue,
            enabled = grantedMap[Manifest.permission.CALL_PHONE] == true,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Continue", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun PermissionRow(info: PermissionInfo, granted: Boolean) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(info.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(info.title, style = MaterialTheme.typography.titleMedium)
                Text(info.rationale, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (granted) {
                Icon(Icons.Filled.Check, contentDescription = "Granted", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun BatteryRow(exempt: Boolean, onRequest: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.BatteryChargingFull, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text("Skip battery optimization", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Optional but recommended. Some phones aggressively kill background connections to save battery.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (exempt) {
                Icon(Icons.Filled.Check, contentDescription = "Granted", tint = MaterialTheme.colorScheme.primary)
            } else {
                OutlinedButton(onClick = onRequest) { Text("Allow") }
            }
        }
    }
}
