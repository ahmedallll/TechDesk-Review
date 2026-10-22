package com.example.scanner

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

class BarcodeAnalyzer(
    private val isQrModeOnly: Boolean = false,
    private val onBarcodeDetected: (barcode: String, format: String) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "BarcodeAnalyzer"

        /** Shared options ensure instrumented tests exercise the exact production format configuration. */
        fun createScannerOptions(isQrModeOnly: Boolean = false): BarcodeScannerOptions {
            return if (isQrModeOnly) {
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
            } else {
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E,
                        Barcode.FORMAT_CODE_128
                    )
                    .build()
            }
        }
    }

    private val scannerOptions = createScannerOptions(isQrModeOnly)
    private val scanner = BarcodeScanning.getClient(scannerOptions)
    private val isClosed = AtomicBoolean(false)

    // Deduplication cooldown
    private var lastScannedBarcode: String? = null
    private var lastScannedTimestamp: Long = 0L
    private val cooldownMs = 1500L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (isClosed.get()) {
            try {
                imageProxy.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing frame when analyzer is already closed: ${e.message}")
            }
            return
        }

        var isFrameClosed = false
        fun safeCloseFrame() {
            if (!isFrameClosed) {
                isFrameClosed = true
                try {
                    imageProxy.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing imageProxy: ${e.message}")
                }
            }
        }

        try {
            val mediaImage = imageProxy.image
            if (mediaImage != null && !isClosed.get()) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        if (isClosed.get()) return@addOnSuccessListener
                        val now = System.currentTimeMillis()
                        for (barcode in barcodes) {
                            val rawValue = barcode.rawValue ?: continue
                            if (rawValue.isBlank()) continue

                            // Cooldown logic: allow new barcodes immediately; debounce exact same barcode
                            if (rawValue == lastScannedBarcode && (now - lastScannedTimestamp) < cooldownMs) {
                                continue
                            }

                            lastScannedBarcode = rawValue
                            lastScannedTimestamp = now
                            val formatStr = BarcodeFormatMapper.formatToString(barcode.format)
                            onBarcodeDetected(rawValue, formatStr)
                            break // process one barcode per frame
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Barcode frame analysis failed: ${e.message}", e)
                    }
                    .addOnCompleteListener {
                        safeCloseFrame()
                    }
            } else {
                safeCloseFrame()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during frame analysis: ${e.message}", e)
            safeCloseFrame()
        }
    }

    /**
     * Safely closes the underlying ML Kit scanner and stops processing new frames.
     */
    fun close() {
        if (isClosed.compareAndSet(false, true)) {
            try {
                scanner.close()
                Log.d(TAG, "ML Kit scanner closed successfully.")
            } catch (e: Exception) {
                Log.w(TAG, "Error closing BarcodeScanner: ${e.message}", e)
            }
        }
    }
}
