package com.example.data.network

import android.util.Log
import com.example.data.model.HeartbeatResponse
import com.example.data.model.PairRequest
import com.example.data.model.PairResponse
import com.example.data.model.ScanEvent
import com.example.data.model.ServerIdentifyResponse
import com.example.data.model.SubmitBarcodeResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TechDeskApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
) {
    companion object {
        private const val TAG = "TechDeskApiClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private fun extractErrorMessage(bodyString: String, statusCode: Int): String {
        return try {
            val json = JSONObject(bodyString)
            when {
                json.has("error") && !json.isNull("error") -> json.getString("error")
                json.has("message") && !json.isNull("message") -> json.getString("message")
                else -> "HTTP $statusCode"
            }
        } catch (_: Exception) {
            if (bodyString.isNotBlank()) bodyString.take(120) else "HTTP $statusCode"
        }
    }

    /**
     * Identify: GET /api/usb-scanner/identify
     * Validates that the target host runs TechDesk DZ and has matching installation reference.
     */
    suspend fun identify(host: String, port: Int, expectedRef: String? = null): Result<ServerIdentifyResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!IpValidator.isLocalOrPrivateAddress(host)) {
                    throw SecurityException("Host '$host' is not a local or private USB network address")
                }

                val url = "http://$host:$port/api/usb-scanner/identify"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val errMsg = extractErrorMessage(bodyString, response.code)
                        throw IllegalStateException("Identify failed: $errMsg")
                    }

                    val json = JSONObject(bodyString)
                    val appName = json.optString("appName")
                    val ready = json.optBoolean("ready", false)
                    val installationReference = json.optString("installationReference")
                    val protocolVersion = json.optString("protocolVersion", "1.0.0")
                    val serverTime = if (json.has("serverTime")) json.optLong("serverTime") else null

                    val result = ServerIdentifyResponse(
                        appName = appName,
                        protocolVersion = protocolVersion,
                        installationReference = installationReference,
                        ready = ready,
                        serverTime = serverTime
                    )

                    if (appName != "TechDesk DZ") {
                        throw IllegalStateException("Target server is not TechDesk DZ (reported: '$appName')")
                    }
                    if (!ready) {
                        throw IllegalStateException("TechDesk DZ is not ready for pairing")
                    }
                    if (expectedRef != null && !expectedRef.equals(installationReference, ignoreCase = false)) {
                        throw IllegalStateException("Installation reference mismatch: expected '$expectedRef' but got '$installationReference'")
                    }

                    result
                }
            }
        }

    /**
     * Fast Identify for discovery probes: short connection/read timeouts and no retries.
     */
    suspend fun identifyFast(host: String, port: Int, expectedRef: String? = null): Result<ServerIdentifyResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!IpValidator.isLocalOrPrivateAddress(host)) {
                    throw SecurityException("Host '$host' is not a local or private USB network address")
                }

                val fastClient = client.newBuilder()
                    .connectTimeout(1200, TimeUnit.MILLISECONDS)
                    .readTimeout(1500, TimeUnit.MILLISECONDS)
                    .writeTimeout(1500, TimeUnit.MILLISECONDS)
                    .build()

                val url = "http://$host:$port/api/usb-scanner/identify"
                val request = Request.Builder().url(url).get().build()

                fastClient.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Identify failed with HTTP ${response.code}")
                    }

                    val json = JSONObject(bodyString)
                    val appName = json.optString("appName")
                    val ready = json.optBoolean("ready", false)
                    val installationReference = json.optString("installationReference")
                    val protocolVersion = json.optString("protocolVersion", "1.0.0")
                    val serverTime = if (json.has("serverTime")) json.optLong("serverTime") else null

                    val result = ServerIdentifyResponse(
                        appName = appName,
                        protocolVersion = protocolVersion,
                        installationReference = installationReference,
                        ready = ready,
                        serverTime = serverTime
                    )

                    if (appName != "TechDesk DZ") {
                        throw IllegalStateException("Not TechDesk DZ")
                    }
                    if (!ready) {
                        throw IllegalStateException("Not ready")
                    }
                    if (expectedRef != null && !expectedRef.equals(installationReference, ignoreCase = false)) {
                        throw IllegalStateException("Ref mismatch")
                    }

                    result
                }
            }
        }


    /**
     * Pair: POST /api/usb-scanner/pair
     * Sends pairing credentials and receives permanent device token and deviceId.
     */
    suspend fun pair(host: String, port: Int, request: PairRequest): Result<PairResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!IpValidator.isLocalOrPrivateAddress(host)) {
                    throw SecurityException("Host '$host' is not a local or private USB network address")
                }

                val url = "http://$host:$port/api/usb-scanner/pair"
                val jsonPayload = JSONObject().apply {
                    put("installationReference", request.installationReference)
                    put("pairingCode", request.pairingCode)
                    put("deviceName", request.deviceName)
                    put("deviceModel", request.deviceModel)
                }.toString()

                val requestBody = jsonPayload.toRequestBody(JSON_MEDIA_TYPE)
                val httpRequest = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    val bodyString = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val errMsg = extractErrorMessage(bodyString, response.code)
                        throw IllegalStateException("Pairing failed: $errMsg")
                    }

                    val json = JSONObject(bodyString)
                    val pairResponse = PairResponse(
                        deviceId = json.optString("deviceId"),
                        deviceToken = json.optString("deviceToken"),
                        installationReference = json.optString("installationReference"),
                        protocolVersion = json.optString("protocolVersion", "1.0.0")
                    )

                    if (!pairResponse.isValid()) {
                        throw IllegalStateException("Invalid pair response from server")
                    }

                    pairResponse
                }
            }
        }

    /**
     * Heartbeat: POST /api/usb-scanner/heartbeat
     * Sends empty JSON with Bearer token, receives activeContext and serverTime.
     */
    suspend fun heartbeat(host: String, port: Int, deviceToken: String): Result<HeartbeatResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!IpValidator.isLocalOrPrivateAddress(host)) {
                    throw SecurityException("Host '$host' is not a local or private USB network address")
                }

                val url = "http://$host:$port/api/usb-scanner/heartbeat"
                val requestBody = "{}".toRequestBody(JSON_MEDIA_TYPE)
                val httpRequest = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $deviceToken")
                    .post(requestBody)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    val bodyString = response.body?.string().orEmpty()
                    if (response.code == 401 || response.code == 403) {
                        throw SecurityException("Device token expired or revoked by TechDesk DZ")
                    }
                    if (!response.isSuccessful) {
                        val errMsg = extractErrorMessage(bodyString, response.code)
                        throw IllegalStateException("Heartbeat failed: $errMsg")
                    }

                    val json = JSONObject(bodyString)
                    val activeContext = json.optString("activeContext", "auto")
                    val serverTime = if (json.has("serverTime")) json.optLong("serverTime") else null

                    HeartbeatResponse(
                        activeContext = activeContext,
                        serverTime = serverTime
                    )
                }
            }
        }

    /**
     * Submit Barcode: POST /api/usb-scanner/submit
     * Authenticated with Bearer token.
     * Handles actual response fields: success, accepted, duplicate, scanId, error/message.
     */
    suspend fun submitBarcode(
        host: String,
        port: Int,
        deviceToken: String,
        scanEvent: ScanEvent
    ): Result<SubmitBarcodeResponse> = withContext(Dispatchers.IO) {
        runCatching {
            if (!IpValidator.isLocalOrPrivateAddress(host)) {
                throw SecurityException("Host '$host' is not a local or private USB network address")
            }

            val url = "http://$host:$port/api/usb-scanner/submit"
            val jsonPayload = JSONObject().apply {
                put("scanId", scanEvent.scanId)
                put("barcode", scanEvent.barcode)
                put("format", scanEvent.format)
                put("timestamp", scanEvent.timestamp)
            }.toString()

            val requestBody = jsonPayload.toRequestBody(JSON_MEDIA_TYPE)
            val httpRequest = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $deviceToken")
                .post(requestBody)
                .build()

            client.newCall(httpRequest).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (response.code == 401 || response.code == 403) {
                    throw SecurityException("Device authorization rejected by TechDesk DZ")
                }
                if (!response.isSuccessful) {
                    val errMsg = extractErrorMessage(bodyString, response.code)
                    throw IllegalStateException("Submit failed: $errMsg")
                }

                val json = if (bodyString.isNotBlank()) JSONObject(bodyString) else JSONObject()
                val success = if (json.has("success")) json.optBoolean("success") else null
                val accepted = if (json.has("accepted")) json.optBoolean("accepted") else null
                val duplicate = if (json.has("duplicate")) json.optBoolean("duplicate") else false
                val scanId = json.optString("scanId", scanEvent.scanId)
                val error = if (json.has("error") && !json.isNull("error")) json.getString("error") else null
                val message = if (json.has("message") && !json.isNull("message")) json.getString("message") else null

                SubmitBarcodeResponse(
                    success = success,
                    accepted = accepted,
                    duplicate = duplicate,
                    scanId = scanId,
                    error = error,
                    message = message
                )
            }
        }
    }
}
