package com.crm.phoneagent.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Result of a successful pairing/complete call. The raw token is handed back exactly once -
 *  the backend only ever stores its SHA-256 hash from this point on. */
data class PairingResult(val deviceId: String, val deviceToken: String, val userDisplayName: String)

sealed class ApiError(message: String) : Exception(message) {
    class Invalid(message: String) : ApiError(message)
    class Network(message: String) : ApiError(message)
    class Server(val status: Int, message: String) : ApiError(message)
}

/** Talks to the small slice of the CRM REST API the phone needs before it has a WebSocket:
 *  exactly one endpoint, /calling/pairing/complete - everything else happens over the socket. */
class ApiClient(private val apiBaseUrl: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun completePairing(
        code: String,
        installationId: String,
        deviceName: String,
        appVersion: String,
        deviceModel: String,
    ): PairingResult = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("code", code)
            .put("installationId", installationId)
            .put("deviceName", deviceName)
            .put("platform", "ANDROID")
            .put("appVersion", appVersion)
            .put("deviceModel", deviceModel)
            .toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$apiBaseUrl/calling/pairing/complete")
            .post(body)
            .build()

        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw ApiError.Network(e.message ?: "Could not reach the CRM server")
        }
        response.use {
            val text = it.body?.string().orEmpty()
            if (it.code == 400) {
                val message = runCatching { JSONObject(text).optString("message") }.getOrNull()
                throw ApiError.Invalid(message?.ifBlank { null } ?: "Invalid or expired code")
            }
            if (!it.isSuccessful) {
                throw ApiError.Server(it.code, "Server error (HTTP ${it.code}). Try again shortly.")
            }
            val json = JSONObject(text)
            PairingResult(
                deviceId = json.getString("deviceId"),
                deviceToken = json.getString("deviceToken"),
                userDisplayName = json.optString("userDisplayName").ifBlank { "your CRM account" },
            )
        }
    }
}
