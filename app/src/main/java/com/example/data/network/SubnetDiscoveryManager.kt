package com.example.data.network

import android.util.Log
import com.example.data.model.ServerIdentifyResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.NetworkInterface

/** One IPv4 address and the prefix reported for that SAME address/interface address entry. */
data class InterfaceIpv4Address(
    val address: String,
    val prefixLength: Int
)

data class NetworkInterfaceInfo(
    val name: String,
    val displayName: String,
    val ipAddresses: List<String>,
    val isUsbTetheringCandidate: Boolean,
    val prefixLength: Int = 24,
    /** New field keeps each address tied to its own prefix. Existing constructor calls remain compatible. */
    val ipv4AddressPrefixes: List<InterfaceIpv4Address> = emptyList()
)

data class TetheringStatus(
    val isTetheringDetected: Boolean,
    val candidateInterface: String?,
    val candidateIp: String?,
    val candidatePrefixLength: Int = 24,
    val allInterfaces: List<NetworkInterfaceInfo>
) {
    /** The actual network CIDR, not the device's host IP. */
    val candidateSubnetCidr: String?
        get() = candidateIp?.let { SubnetCalculator.networkCidr(it, candidatePrefixLength) }
}

data class DiscoveredHost(
    val host: String,
    val port: Int,
    val identifyResponse: ServerIdentifyResponse
)

/** Pure, testable selection logic used by live interface discovery and unit tests. */
object UsbInterfaceSelector {
    private val excludedPrefixes = listOf(
        "rmnet", "pdp", "ccmni", "wwan", "radio", "dummy",
        "bt-pan", "pan", "bnep", "lo"
    )

    fun isUsbCandidate(name: String, displayName: String = ""): Boolean {
        val lowerName = name.lowercase()
        if (excludedPrefixes.any { lowerName.startsWith(it) }) return false
        return true
    }

    /**
     * Always takes the address and prefix from one candidate record. It never uses an IP
     * discovered while iterating some other adapter. Stable ranking makes selection independent
     * of OS interface enumeration order when a positively named Wi-Fi, Ethernet or USB interface is present.
     */
    fun select(interfaces: List<NetworkInterfaceInfo>): TetheringStatus {
        val selected = interfaces
            .asSequence()
            .filter { it.isUsbTetheringCandidate && isUsbCandidate(it.name, it.displayName) }
            .mapNotNull { info ->
                val address = info.ipv4AddressPrefixes.firstOrNull {
                    SubnetCalculator.isValidIpv4(it.address) && it.prefixLength in 1..32
                } ?: if (info.prefixLength in 1..32) {
                    info.ipAddresses.firstOrNull { SubnetCalculator.isValidIpv4(it) }
                        ?.let { InterfaceIpv4Address(it, info.prefixLength) }
                } else null
                address?.let { Triple(info, it.address, it.prefixLength) }
            }
            .sortedWith(
                compareBy<Triple<NetworkInterfaceInfo, String, Int>> { interfacePriority(it.first.name) }
                    .thenBy { it.first.name.lowercase() }
                    .thenBy { it.second }
            )
            .firstOrNull()

        return if (selected == null) {
            TetheringStatus(false, null, null, 24, interfaces)
        } else {
            TetheringStatus(
                isTetheringDetected = true,
                candidateInterface = selected.first.name,
                candidateIp = selected.second,
                candidatePrefixLength = selected.third,
                allInterfaces = interfaces
            )
        }
    }

    private fun interfacePriority(name: String): Int {
        val lower = name.lowercase()
        return when {
            lower.startsWith("wlan") || lower.startsWith("wifi") || lower.contains("wi-fi") -> 0
            lower.startsWith("eth") || lower.startsWith("en") || lower.contains("ethernet") -> 1
            lower.startsWith("rndis") || lower.contains("rndis") -> 2
            lower.contains("usb") -> 3
            lower.contains("ncm") || lower.startsWith("enx") -> 4
            else -> 5
        }
    }
}

object SubnetCalculator {
    /** Returns a 32-bit IPv4 value, or 0 when the input is not a valid IPv4 address. */
    fun ipToInt(ip: String): Long {
        val parts = ip.trim().split(".")
        if (parts.size != 4) return 0L
        var result = 0L
        for (part in parts) {
            if (part.isEmpty() || part.any { !it.isDigit() }) return 0L
            val octet = part.toIntOrNull() ?: return 0L
            if (octet !in 0..255) return 0L
            result = (result shl 8) or octet.toLong()
        }
        return result
    }

    fun isValidIpv4(ip: String): Boolean {
        val parts = ip.trim().split(".")
        if (parts.size != 4) return false
        return parts.all { part ->
            if (part.isEmpty() || part.any { !it.isDigit() }) {
                false
            } else {
                val octet = part.toIntOrNull()
                octet != null && octet in 0..255
            }
        }
    }

    fun intToIp(ip: Long): String {
        return "${(ip shr 24) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 8) and 0xFF}.${ip and 0xFF}"
    }

    fun networkCidr(ipStr: String, prefixLength: Int): String? {
        if (!isValidIpv4(ipStr) || prefixLength !in 0..32) return null
        val ipInt = ipToInt(ipStr)
        val mask = if (prefixLength == 0) 0L else (0xFFFFFFFFL shl (32 - prefixLength)) and 0xFFFFFFFFL
        return "${intToIp(ipInt and mask)}/$prefixLength"
    }

    fun getUsableHosts(ipStr: String, prefixLength: Int, ownIp: String? = null): List<String> {
        if (prefixLength < 22 || prefixLength > 30) {
            throw IllegalArgumentException("Subnet too large or invalid prefix length: /$prefixLength (supported range /22 through /30)")
        }
        if (!isValidIpv4(ipStr)) throw IllegalArgumentException("Invalid IPv4 address: $ipStr")
        if (ownIp != null && !isValidIpv4(ownIp)) throw IllegalArgumentException("Invalid own IPv4 address: $ownIp")

        val ipInt = ipToInt(ipStr)
        val mask = (0xFFFFFFFFL shl (32 - prefixLength)) and 0xFFFFFFFFL
        val network = ipInt and mask
        val broadcast = network or (mask.inv() and 0xFFFFFFFFL)
        val ownIpInt = if (ownIp != null) ipToInt(ownIp) else -1L
        val hosts = mutableListOf<String>()

        for (addr in (network + 1)..(broadcast - 1)) {
            if (addr == ownIpInt) continue
            hosts.add(intToIp(addr))
        }
        return hosts
    }
}

class SubnetDiscoveryManager(
    private val apiClient: TechDeskApiClient = TechDeskApiClient()
) {
    companion object {
        private const val TAG = "SubnetDiscoveryManager"
        const val DEFAULT_PORT = 3000
        val FALLBACK_PORTS = listOf(3001, 8080, 8000, 5000)
    }

    /** Inspect the OS interfaces, then select from complete per-interface records. */
    fun getTetheringDiagnostics(): TetheringStatus {
        val records = mutableListOf<NetworkInterfaceInfo>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
                ?: return TetheringStatus(false, null, null, 24, emptyList())

            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                try {
                    if (!intf.isUp || intf.isLoopback) continue
                    val addressEntries = intf.interfaceAddresses.mapNotNull { entry ->
                        val address = entry.address
                        if (address is Inet4Address && !address.isLoopbackAddress && !address.isAnyLocalAddress) {
                            val hostAddress = address.hostAddress ?: return@mapNotNull null
                            InterfaceIpv4Address(hostAddress, entry.networkPrefixLength.toInt())
                        } else null
                    }
                    if (addressEntries.isEmpty()) continue

                    val name = intf.name ?: ""
                    val displayName = intf.displayName ?: ""
                    val preferredAddress = addressEntries.firstOrNull { SubnetCalculator.isValidIpv4(it.address) }
                    records.add(
                        NetworkInterfaceInfo(
                            name = name,
                            displayName = displayName,
                            ipAddresses = addressEntries.map { it.address },
                            isUsbTetheringCandidate = UsbInterfaceSelector.isUsbCandidate(name, displayName),
                            prefixLength = preferredAddress?.prefixLength ?: 24,
                            ipv4AddressPrefixes = addressEntries
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping network interface ${intf.name}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading network interfaces", e)
        }

        return UsbInterfaceSelector.select(records)
    }

    /**
     * Discovers the matching TechDesk DZ server on the selected USB subnet. Probes are bounded
     * to batches of 32 and stop after the first batch containing a matching installation.
     */
    suspend fun discoverHostOnUsbSubnet(
        expectedRef: String,
        preferredPort: Int = DEFAULT_PORT,
        tetheringStatus: TetheringStatus? = null,
        onProgress: (message: String, progressFraction: Float) -> Unit = { _, _ -> }
    ): DiscoveredHost? = withContext(Dispatchers.IO) {
        val diag = tetheringStatus ?: getTetheringDiagnostics()
        val ownIp = diag.candidateIp
        if (!diag.isTetheringDetected || ownIp.isNullOrBlank()) {
            Log.w(TAG, "Cannot discover PC: no positively identified USB-tether interface")
            return@withContext null
        }

        val usableHosts = try {
            SubnetCalculator.getUsableHosts(ownIp, diag.candidatePrefixLength, ownIp)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot scan USB subnet ${diag.candidateSubnetCidr}: ${e.message}", e)
            onProgress("USB interface ${diag.candidateInterface} has unsupported subnet ${diag.candidateSubnetCidr}. Use manual PC IP setup.", 0f)
            return@withContext null
        }
        if (usableHosts.isEmpty()) {
            onProgress("No usable hosts on USB subnet ${diag.candidateSubnetCidr}.", 0f)
            return@withContext null
        }

        val sortedHosts = usableHosts.sortedWith(compareBy { host ->
            when {
                host.substringAfterLast('.').toIntOrNull() == 1 -> 0
                host.substringAfterLast('.').toIntOrNull() == 113 -> 1
                host.substringAfterLast('.').toIntOrNull() == 129 -> 2
                host.substringAfterLast('.').toIntOrNull() == 2 -> 3
                host.substringAfterLast('.').toIntOrNull() == 100 -> 4
                else -> 5
            }
        })

        withTimeoutOrNull(20000L) {
            val batchSize = 32
            var completed = 0
            onProgress("Scanning ${diag.candidateSubnetCidr} from ${diag.candidateInterface} on port $preferredPort…", 0.02f)

            for (batch in sortedHosts.chunked(batchSize)) {
                val results = coroutineScope {
                    batch.map { host ->
                        async {
                            runCatching {
                                val result = apiClient.identifyFast(host, preferredPort, expectedRef)
                                result.getOrNull()?.takeIf { it.isValidFor(expectedRef) }?.let {
                                    DiscoveredHost(host, preferredPort, it)
                                }
                            }.getOrNull()
                        }
                    }.awaitAll()
                }
                completed += batch.size
                val found = results.firstOrNull { it != null }
                onProgress("Scanning USB subnet ${diag.candidateSubnetCidr} ($completed/${sortedHosts.size})", completed.toFloat() / (sortedHosts.size * 1.0f))
                if (found != null) return@withTimeoutOrNull found
            }

            onProgress("Checking fallback ports on ${diag.candidateSubnetCidr}…", 0.9f)
            val fallbackHosts = sortedHosts.take(30)
            for (port in FALLBACK_PORTS.filter { it != preferredPort }) {
                for (batch in fallbackHosts.chunked(batchSize)) {
                    val results = coroutineScope {
                        batch.map { host ->
                            async {
                                runCatching {
                                    apiClient.identifyFast(host, port, expectedRef).getOrNull()
                                        ?.takeIf { it.isValidFor(expectedRef) }
                                        ?.let { DiscoveredHost(host, port, it) }
                                }.getOrNull()
                            }
                        }.awaitAll()
                    }
                    val found = results.firstOrNull { it != null }
                    if (found != null) return@withTimeoutOrNull found
                }
            }
            onProgress("No TechDesk DZ installation matching '$expectedRef' found on ${diag.candidateSubnetCidr}.", 1f)
            null
        }
    }
}
