package com.example.data.network

import java.net.InetAddress

/**
 * Validates that candidate PC addresses belong to local private networks,
 * preventing arbitrary external or public internet requests.
 */
object IpValidator {

    fun isLocalOrPrivateAddress(host: String): Boolean {
        val trimmed = host.trim()
        if (trimmed == "localhost" || trimmed == "127.0.0.1") return true

        return try {
            val parts = trimmed.split(".")
            if (parts.size == 4) {
                val octets = parts.map { it.toInt() }
                val (o1, o2, o3, o4) = octets
                if (octets.any { it !in 0..255 }) return false

                // 192.168.x.x (Standard USB Tethering network)
                if (o1 == 192 && o2 == 168) return true
                // 10.x.x.x (Private network)
                if (o1 == 10) return true
                // 172.16.x.x - 172.31.x.x (Private network)
                if (o1 == 172 && o2 in 16..31) return true
                // 127.x.x.x (Loopback)
                if (o1 == 127) return true
                // 169.254.x.x (Link-local)
                if (o1 == 169 && o2 == 254) return true

                false
            } else {
                val address = InetAddress.getByName(trimmed)
                address.isSiteLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress
            }
        } catch (e: Exception) {
            false
        }
    }
}
