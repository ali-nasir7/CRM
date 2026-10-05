package com.crm.phoneagent.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crm.phoneagent.BuildConfig
import com.crm.phoneagent.PhoneAgentApplication
import com.crm.phoneagent.network.ApiClient
import com.crm.phoneagent.network.ApiError
import com.crm.phoneagent.network.ServerEndpoints
import kotlinx.coroutines.launch

private sealed interface PairUiState {
    data object Idle : PairUiState
    data object Validating : PairUiState
    data class Invalid(val message: String) : PairUiState
    data class NetworkError(val message: String) : PairUiState
}

@Composable
fun PairDeviceScreen(onBack: () -> Unit, onPaired: () -> Unit) {
    val context = LocalContext.current
    val store = (context.applicationContext as PhoneAgentApplication).deviceStore
    val scope = rememberCoroutineScope()

    var code by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<PairUiState>(PairUiState.Idle) }

    fun submit() {
        if (code.length != 6) return
        state = PairUiState.Validating
        scope.launch {
            try {
                val endpoints = ServerEndpoints.parse(
                    store.apiBaseUrl(BuildConfig.DEFAULT_API_BASE_URL),
                    store.wsBaseUrl(BuildConfig.DEFAULT_WS_BASE_URL),
                )
                val apiClient = ApiClient(endpoints.apiBaseUrl)
                val result = apiClient.completePairing(
                    code = code,
                    installationId = store.installationId(),
                    deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                    appVersion = BuildConfig.VERSION_NAME,
                    deviceModel = Build.MODEL ?: "Android device",
                )
                store.savePairing(result.deviceId, result.deviceToken, "${Build.MANUFACTURER} ${Build.MODEL}".trim(), result.userDisplayName)
                onPaired()
            } catch (e: ApiError.Invalid) {
                state = PairUiState.Invalid(e.message ?: "Invalid or expired code")
            } catch (e: ApiError.Network) {
                state = PairUiState.NetworkError(e.message ?: "Could not reach the CRM server")
            } catch (e: ApiError.Server) {
                state = PairUiState.NetworkError(e.message ?: "Server error")
            } catch (e: IllegalArgumentException) {
                state = PairUiState.NetworkError(e.message ?: "Invalid CRM server configuration")
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
        Spacer(Modifier.height(8.dp))
        Text("Connect your phone", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Enter the pairing code shown in your CRM (Calls › Pair Phone Agent).",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = code,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) { code = it; if (state !is PairUiState.Validating) state = PairUiState.Idle } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = state is PairUiState.Invalid,
            enabled = state !is PairUiState.Validating,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            textStyle = MaterialTheme.typography.headlineMedium.copy(
                textAlign = TextAlign.Center, letterSpacing = 10.sp, fontFamily = FontFamily.Monospace,
            ),
            placeholder = { Text("······", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        )

        Spacer(Modifier.height(12.dp))
        when (val s = state) {
            is PairUiState.Invalid -> ErrorLine(s.message)
            is PairUiState.NetworkError -> ErrorLine(s.message)
            else -> {}
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = ::submit,
            enabled = code.length == 6 && state !is PairUiState.Validating,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (state is PairUiState.Validating) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Connect Device", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ErrorLine(message: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.height(18.dp))
            Spacer(Modifier.height(0.dp))
            Text("  $message", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
