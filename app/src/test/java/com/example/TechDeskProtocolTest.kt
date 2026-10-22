package com.example

import com.example.data.model.ActiveContext
import com.example.data.model.QrPayload
import com.example.data.model.ScanEvent
import com.example.data.model.ScanStatus
import com.example.data.model.ServerIdentifyResponse
import com.example.data.network.IpValidator
import com.example.data.network.InterfaceIpv4Address
import com.example.data.network.NetworkInterfaceInfo
import com.example.data.network.UsbInterfaceSelector
import com.example.data.network.SubnetCalculator
import com.example.scanner.BarcodeFormatMapper
import com.google.mlkit.vision.barcode.common.Barcode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class TechDeskProtocolTest {

    @Test
    fun testQrPayloadParsing_validFormat() {
        val rawJson = """{"ref":"TDZ-A7K29M","code":"849201","ip":"192.168.42.129","port":3000}"""
        val result = QrPayload.parseJson(rawJson)

        assertTrue(result.isSuccess)
        val payload = result.getOrThrow()
        assertEquals("TDZ-A7K29M", payload.ref)
        assertEquals("849201", payload.code)
        assertEquals("192.168.42.129", payload.ip)
        assertEquals(3000, payload.port)
    }

    @Test
    fun testQrPayloadParsing_defaultPort() {
        val rawJson = """{"ref":"TDZ-XYZ123","code":"123456","ip":"192.168.42.1"}"""
        val result = QrPayload.parseJson(rawJson)

        assertTrue(result.isSuccess)
        val payload = result.getOrThrow()
        assertEquals("TDZ-XYZ123", payload.ref)
        assertEquals(3000, payload.port)
    }

    @Test
    fun testQrPayloadParsing_invalidPayloads() {
        // Missing ref
        assertFalse(QrPayload.parseJson("""{"code":"849201","ip":"192.168.42.129","port":3000}""").isSuccess)
        // Missing code
        assertFalse(QrPayload.parseJson("""{"ref":"TDZ-A7K29M","ip":"192.168.42.129","port":3000}""").isSuccess)
        // Missing IP
        assertFalse(QrPayload.parseJson("""{"ref":"TDZ-A7K29M","code":"849201","port":3000}""").isSuccess)
        // Invalid port
        assertFalse(QrPayload.parseJson("""{"ref":"TDZ-A7K29M","code":"849201","ip":"192.168.42.129","port":-1}""").isSuccess)
        // Non-JSON string
        assertFalse(QrPayload.parseJson("NOT_A_JSON_PAYLOAD").isSuccess)
    }

    @Test
    fun testInstallationReferenceMatching() {
        val response = ServerIdentifyResponse(
            appName = "TechDesk DZ",
            ready = true,
            installationReference = "TDZ-S4MSX7",
            protocolVersion = "1.0.0"
        )

        // Matching ref
        assertTrue(response.isValidFor("TDZ-S4MSX7"))

        // Differing ref
        assertFalse(response.isValidFor("TDZ-OTHER99"))

        // Differing appName
        val wrongApp = response.copy(appName = "Other POS")
        assertFalse(wrongApp.isValidFor("TDZ-S4MSX7"))

        // Not ready
        val notReady = response.copy(ready = false)
        assertFalse(notReady.isValidFor("TDZ-S4MSX7"))
    }

    @Test
    fun testSubnetCalculator_cachingAndEnumeration() {
        // Given Android IP 192.168.107.218/24, Windows IP 192.168.107.113 should be in usable hosts
        val hosts = SubnetCalculator.getUsableHosts("192.168.107.218", 24, "192.168.107.218")
        assertTrue(hosts.contains("192.168.107.113"))
        assertFalse(hosts.contains("192.168.107.218")) // Own IP excluded
        assertFalse(hosts.contains("192.168.107.0"))   // Network address excluded
        assertFalse(hosts.contains("192.168.107.255")) // Broadcast address excluded
        assertEquals(253, hosts.size) // 254 usable minus 1 own IP
    }

    @Test
    fun testUsbInterfaceSelectionKeepsIpWithItsInterface() {
        val usb = NetworkInterfaceInfo(
            name = "rndis0",
            displayName = "Remote NDIS Compatible Device",
            ipAddresses = listOf("192.168.107.218"),
            isUsbTetheringCandidate = true,
            prefixLength = 24,
            ipv4AddressPrefixes = listOf(InterfaceIpv4Address("192.168.107.218", 24))
        )
        val cellular = NetworkInterfaceInfo(
            name = "rmnet_data0",
            displayName = "Cellular data",
            ipAddresses = listOf("10.161.62.97"),
            isUsbTetheringCandidate = false,
            prefixLength = 24,
            ipv4AddressPrefixes = listOf(InterfaceIpv4Address("10.161.62.97", 24))
        )

        val result = UsbInterfaceSelector.select(listOf(usb, cellular))
        assertTrue(result.isTetheringDetected)
        assertEquals("rndis0", result.candidateInterface)
        assertEquals("192.168.107.218", result.candidateIp)
        assertEquals(24, result.candidatePrefixLength)
        assertEquals("192.168.107.0/24", result.candidateSubnetCidr)
        assertNotEquals("10.161.62.97", result.candidateIp)
    }

    @Test
    fun testUsbInterfaceSelectionDoesNotDependOnEnumerationOrder() {
        val usb = NetworkInterfaceInfo(
            name = "rndis0",
            displayName = "Remote NDIS",
            ipAddresses = listOf("192.168.107.218"),
            isUsbTetheringCandidate = true,
            prefixLength = 24,
            ipv4AddressPrefixes = listOf(InterfaceIpv4Address("192.168.107.218", 24))
        )
        val cellular = NetworkInterfaceInfo(
            name = "rmnet_data0",
            displayName = "Cellular",
            ipAddresses = listOf("10.161.62.97"),
            isUsbTetheringCandidate = false,
            prefixLength = 24,
            ipv4AddressPrefixes = listOf(InterfaceIpv4Address("10.161.62.97", 24))
        )

        val result = UsbInterfaceSelector.select(listOf(cellular, usb))
        assertEquals("rndis0", result.candidateInterface)
        assertEquals("192.168.107.218", result.candidateIp)
        assertTrue(SubnetCalculator.getUsableHosts(result.candidateIp!!, result.candidatePrefixLength, result.candidateIp).contains("192.168.107.113"))
    }

    @Test
    fun testSubnetCalculatorValidatesAndReportsNetwork() {
        assertEquals("192.168.107.0/24", SubnetCalculator.networkCidr("192.168.107.218", 24))
        assertEquals("192.168.104.0/22", SubnetCalculator.networkCidr("192.168.107.218", 22))
        assertFalse(SubnetCalculator.isValidIpv4("10.161.62.999"))
        assertEquals(null, SubnetCalculator.networkCidr("not-an-ip", 24))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testSubnetCalculator_rejectsOversizedSubnet() {
        // /21 subnet (>1022 hosts) should be rejected
        SubnetCalculator.getUsableHosts("192.168.0.1", 21)
    }

    @Test
    fun testBarcodeFormatMapping() {
        assertEquals("EAN_13", BarcodeFormatMapper.formatToString(Barcode.FORMAT_EAN_13))
        assertEquals("EAN_8", BarcodeFormatMapper.formatToString(Barcode.FORMAT_EAN_8))
        assertEquals("UPC_A", BarcodeFormatMapper.formatToString(Barcode.FORMAT_UPC_A))
        assertEquals("UPC_E", BarcodeFormatMapper.formatToString(Barcode.FORMAT_UPC_E))
        assertEquals("CODE_128", BarcodeFormatMapper.formatToString(Barcode.FORMAT_CODE_128))
        assertEquals("QR_CODE", BarcodeFormatMapper.formatToString(Barcode.FORMAT_QR_CODE))
    }

    @Test
    fun testScanIdReuseOnRetry() {
        val originalScanId = UUID.randomUUID().toString()
        val originalEvent = ScanEvent(
            scanId = originalScanId,
            barcode = "6191234567890",
            format = "EAN_13",
            timestamp = System.currentTimeMillis(),
            status = ScanStatus.FAILED
        )

        // When creating a retry event, the scanId must remain EXACTLY identical
        val retryEvent = originalEvent.copy(
            status = ScanStatus.RETRYING,
            retryCount = originalEvent.retryCount + 1
        )

        assertEquals(originalScanId, retryEvent.scanId)
        assertEquals(originalEvent.barcode, retryEvent.barcode)
        assertEquals(1, retryEvent.retryCount)

        // Contrast with a new scan event which has a different scanId
        val nextEvent = ScanEvent(barcode = "6191234567890", format = "EAN_13")
        assertNotEquals(originalEvent.scanId, nextEvent.scanId)
    }

    @Test
    fun testBarcodeStringPreservesLeadingZeros() {
        val barcode = "0001234567890"
        val event = ScanEvent(
            barcode = barcode,
            format = "EAN_13"
        )

        assertEquals("0001234567890", event.barcode)
        assertTrue(event.barcode.startsWith("000"))
        assertEquals(13, event.barcode.length)
    }

    @Test
    fun testActiveContextParsing() {
        assertEquals(ActiveContext.POS, ActiveContext.fromString("pos"))
        assertEquals(ActiveContext.POS, ActiveContext.fromString("POS"))
        assertEquals(ActiveContext.PRODUCTS, ActiveContext.fromString("products"))
        assertEquals(ActiveContext.AUTO, ActiveContext.fromString("auto"))
        assertEquals(ActiveContext.UNKNOWN, ActiveContext.fromString("other"))
        assertEquals(ActiveContext.UNKNOWN, ActiveContext.fromString(null))
    }

    @Test
    fun testIpValidator() {
        // USB tethering & private networks
        assertTrue(IpValidator.isLocalOrPrivateAddress("192.168.107.113"))
        assertTrue(IpValidator.isLocalOrPrivateAddress("192.168.42.129"))
        assertTrue(IpValidator.isLocalOrPrivateAddress("192.168.1.1"))
        assertTrue(IpValidator.isLocalOrPrivateAddress("10.0.0.5"))
        assertTrue(IpValidator.isLocalOrPrivateAddress("172.16.0.1"))
        assertTrue(IpValidator.isLocalOrPrivateAddress("127.0.0.1"))
        assertTrue(IpValidator.isLocalOrPrivateAddress("localhost"))

        // Public external IP addresses should be rejected
        assertFalse(IpValidator.isLocalOrPrivateAddress("8.8.8.8"))
        assertFalse(IpValidator.isLocalOrPrivateAddress("1.1.1.1"))
        assertFalse(IpValidator.isLocalOrPrivateAddress("142.250.190.46"))
    }
}
