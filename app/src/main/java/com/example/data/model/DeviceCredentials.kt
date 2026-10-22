package com.example.data.model

/**
 * Persisted device pairing credentials backed by Android KeyStore.
 */
data class DeviceCredentials(
    val installationReference: String,
    val host: String,
    val port: Int,
    val deviceId: String,
    val deviceToken: String,
    val protocolVersion: String = "1.0.0",
    val deviceName: String = "Android Scanner"
)
