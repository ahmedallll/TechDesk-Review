package com.example.data.model

/**
 * Identify endpoint response from GET /api/usb-scanner/identify
 * Schema: appName, protocolVersion ("1.0.0"), installationReference, ready, serverTime
 */
data class ServerIdentifyResponse(
    val appName: String?,
    val protocolVersion: String?,
    val installationReference: String?,
    val ready: Boolean?,
    val serverTime: Long? = null
) {
    fun isValidFor(expectedRef: String): Boolean {
        return appName == "TechDesk DZ" &&
                ready == true &&
                installationReference.equals(expectedRef, ignoreCase = false)
    }
}

/**
 * Pair endpoint request sent to POST /api/usb-scanner/pair
 */
data class PairRequest(
    val installationReference: String,
    val pairingCode: String,
    val deviceName: String,
    val deviceModel: String
)

/**
 * Pair endpoint response from POST /api/usb-scanner/pair
 * Schema: deviceId, deviceToken, installationReference, protocolVersion ("1.0.0")
 */
data class PairResponse(
    val deviceId: String?,
    val deviceToken: String?,
    val installationReference: String?,
    val protocolVersion: String?
) {
    fun isValid(): Boolean {
        return !deviceId.isNullOrBlank() &&
                !deviceToken.isNullOrBlank() &&
                !installationReference.isNullOrBlank()
    }
}

/**
 * Heartbeat response from POST /api/usb-scanner/heartbeat
 * Schema: activeContext ("pos", "products", "auto"), serverTime
 */
data class HeartbeatResponse(
    val activeContext: String?,
    val serverTime: Long? = null
)

/**
 * Barcode submission response from POST /api/usb-scanner/submit
 * Schema: success, accepted, duplicate, scanId, error/message
 */
data class SubmitBarcodeResponse(
    val success: Boolean?,
    val accepted: Boolean?,
    val duplicate: Boolean?,
    val scanId: String?,
    val error: String? = null,
    val message: String? = null
) {
    val isAccepted: Boolean
        get() = accepted == true || success == true

    val isDuplicate: Boolean
        get() = duplicate == true

    val statusDescription: String
        get() = when {
            isDuplicate -> "Duplicate scan (already accepted by server)"
            isAccepted -> "Accepted by TechDesk DZ"
            !error.isNullOrBlank() -> error
            !message.isNullOrBlank() -> message
            else -> "Not accepted"
        }
}
