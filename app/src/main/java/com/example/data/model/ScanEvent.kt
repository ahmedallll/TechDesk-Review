package com.example.data.model

import java.util.UUID

enum class ScanStatus {
    PENDING,
    DELIVERED,
    RETRYING,
    FAILED
}

/**
 * Scan event representing a single scanned barcode.
 * CRITICAL: Retries must keep the same scanId for server deduplication!
 */
data class ScanEvent(
    val scanId: String = UUID.randomUUID().toString(),
    val barcode: String,
    val format: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: ScanStatus = ScanStatus.PENDING,
    val serverMessage: String? = null,
    val retryCount: Int = 0,
    val isDuplicate: Boolean = false
)
