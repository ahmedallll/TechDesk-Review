package com.example.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

class BarcodeAnalyzer(
    private val isQrModeOnly: Boolean = false,
    private val onBarcodeDetected: (barcode: String, format: String) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
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

    // Deduplication cooldown
    private var lastScannedBarcode: String? = null
    private var lastScannedTimestamp: Long = 0L
    private val cooldownMs = 1500L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
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
                .addOnFailureListener {
                    // Ignored during continuous stream
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}
