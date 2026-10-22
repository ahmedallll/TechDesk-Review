package com.example

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.example.scanner.BarcodeAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

@RunWith(AndroidJUnit4::class)
class BarcodeDecodingTest {

    private fun generateBarcodeBitmap(contents: String, format: BarcodeFormat, width: Int = 400, height: Int = 150): Bitmap {
        val bitMatrix: BitMatrix = MultiFormatWriter().encode(contents, format, width, height)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bmp
    }

    private suspend fun decodeBitmapWithMlKit(bitmap: Bitmap): List<Barcode> = suspendCancellableCoroutine { continuation ->
        val scanner = BarcodeScanning.getClient(BarcodeAnalyzer.createScannerOptions(isQrModeOnly = false))
        val image = InputImage.fromBitmap(bitmap, 0)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (continuation.isActive) continuation.resume(barcodes)
            }
            .addOnFailureListener {
                if (continuation.isActive) continuation.resume(emptyList())
            }
            .addOnCompleteListener {
                scanner.close()
            }
    }

    @Test
    fun testDecodeEan13() = kotlinx.coroutines.runBlocking {
        val rawValue = "4006381333931" // Valid 13-digit EAN-13 sample
        val bitmap = generateBarcodeBitmap(rawValue, BarcodeFormat.EAN_13)
        val decoded = decodeBitmapWithMlKit(bitmap)

        assertTrue("Should decode EAN-13 barcode", decoded.isNotEmpty())
        val first = decoded.first()
        assertEquals(Barcode.FORMAT_EAN_13, first.format)
        assertNotNull(first.rawValue)
        assertEquals(rawValue, first.rawValue)
    }

    @Test
    fun testDecodeUpcA() = kotlinx.coroutines.runBlocking {
        // Common valid UPC-A sample. Some ML Kit/device versions expose UPC-A as EAN-13 with a leading zero.
        val rawValue = "036000291452"
        val bitmap = generateBarcodeBitmap(rawValue, BarcodeFormat.UPC_A, width = 520, height = 180)
        val decoded = decodeBitmapWithMlKit(bitmap)

        assertTrue("Should decode UPC-A barcode", decoded.isNotEmpty())
        val result = decoded.first()
        assertTrue(result.format == Barcode.FORMAT_UPC_A || result.format == Barcode.FORMAT_EAN_13)
        val decodedValue = result.rawValue.orEmpty()
        assertTrue(
            "Should preserve the UPC-A digits (including optional EAN-13 leading zero), got '$decodedValue'",
            decodedValue == rawValue || decodedValue == "0$rawValue"
        )
    }

    @Test
    fun testDecodeUpcE() = kotlinx.coroutines.runBlocking {
        val rawValue = "01234565"
        val bitmap = generateBarcodeBitmap(rawValue, BarcodeFormat.UPC_E, width = 420, height = 180)
        val decoded = decodeBitmapWithMlKit(bitmap)

        assertTrue("Should decode UPC-E barcode", decoded.isNotEmpty())
        val result = decoded.first()
        assertEquals(Barcode.FORMAT_UPC_E, result.format)
        assertEquals(rawValue, result.rawValue)
    }

    @Test
    fun testDecodeCode128() = kotlinx.coroutines.runBlocking {
        val rawValue = "TDZ-S4MSX7"
        val bitmap = generateBarcodeBitmap(rawValue, BarcodeFormat.CODE_128)
        val decoded = decodeBitmapWithMlKit(bitmap)

        assertTrue("Should decode Code 128 barcode", decoded.isNotEmpty())
        val first = decoded.first()
        assertEquals(Barcode.FORMAT_CODE_128, first.format)
        assertEquals(rawValue, first.rawValue)
    }

    @Test
    fun testDecodeEan8() = kotlinx.coroutines.runBlocking {
        val rawValue = "12345670"
        val bitmap = generateBarcodeBitmap(rawValue, BarcodeFormat.EAN_8)
        val decoded = decodeBitmapWithMlKit(bitmap)

        assertTrue("Should decode EAN-8 barcode", decoded.isNotEmpty())
        val first = decoded.first()
        assertEquals(Barcode.FORMAT_EAN_8, first.format)
        assertNotNull(first.rawValue)
    }
}
