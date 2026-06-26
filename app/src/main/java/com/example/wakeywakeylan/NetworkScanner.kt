package com.example.wakeywakeylan

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object NetworkScanner {
    fun scan(): List<DiscoveredDevice> {
        val network = findActiveNetwork() ?: return emptyList()
        val reachableHosts = discoverReachableHosts(network.hosts, network.broadcastAddress)

        return reachableHosts.map { ip ->
            DiscoveredDevice(
                ipAddress = ip,
                macAddress = "",
                broadcastAddress = network.broadcastAddress
            )
        }
            .sortedBy { it.ipAddressAsLong() }
    }

    private fun discoverReachableHosts(hosts: List<String>, broadcastAddress: String): Set<String> {
        if (hosts.isEmpty()) {
            return emptySet()
        }

        val executor = Executors.newFixedThreadPool(SCAN_PARALLELISM)
        val found = Collections.synchronizedSet(mutableSetOf<String>())

        try {
            val tasks = hosts.map { host ->
                Callable {
                    if (touchHost(host, broadcastAddress)) {
                        found += host
                    }
                }
            }
            executor.invokeAll(tasks, SCAN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } finally {
            executor.shutdownNow()
        }

        return found
    }

    private fun touchHost(host: String, broadcastAddress: String): Boolean {
        for (port in TCP_PORTS) {
            if (canConnect(host, port)) {
                return true
            }
        }

        // UDP probes can nudge some devices to reply or populate neighbor tables,
        // but the scan no longer depends on reading /proc/net/arp.
        sendUdpProbe(host, PROBE_PORT)
        sendUdpProbe(broadcastAddress, SSDP_PORT)

        return isReachable(host)
    }

    private fun canConnect(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun isReachable(host: String): Boolean {
        return try {
            InetAddress.getByName(host).isReachable(REACHABLE_TIMEOUT_MS)
        } catch (_: Exception) {
            false
        }
    }

    private fun sendUdpProbe(target: String, port: Int) {
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                val payload = byteArrayOf(0)
                socket.send(DatagramPacket(payload, payload.size, InetAddress.getByName(target), port))
            }
        } catch (_: Exception) {
            // Best effort only.
        }
    }

    private fun findActiveNetwork(): ActiveNetwork? {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        for (networkInterface in interfaces) {
            if (!networkInterface.isUp || networkInterface.isLoopback || networkInterface.isVirtual) {
                continue
            }
            for (interfaceAddress in networkInterface.interfaceAddresses) {
                val address = interfaceAddress.address
                if (address is Inet4Address && !address.isLoopbackAddress && !address.isLinkLocalAddress) {
                    val hostAddress = address.hostAddress ?: continue
                    val prefixLength = interfaceAddress.networkPrefixLength.toInt().coerceIn(1, 32)
                    val mask = maskForPrefix(prefixLength)
                    val networkAddress = toInt(hostAddress) and mask
                    val broadcastAddress = toAddress(networkAddress or mask.inv())
                    val hosts = buildHostRange(networkAddress, prefixLength)
                    if (hosts.isNotEmpty()) {
                        return ActiveNetwork(broadcastAddress, hosts)
                    }
                }
            }
        }
        return null
    }

    private fun buildHostRange(networkAddress: Int, prefixLength: Int): List<String> {
        val hostBits = 32 - prefixLength
        if (hostBits <= 1) {
            return emptyList()
        }

        val firstHost = networkAddress + 1
        val lastHost = networkAddress + ((1 shl hostBits) - 2)
        val hosts = ArrayList<String>(lastHost - firstHost + 1)
        for (address in firstHost..lastHost) {
            hosts.add(toAddress(address))
        }
        return hosts
    }

    private fun toInt(address: String): Int {
        val bytes = InetAddress.getByName(address).address
        return ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)
    }

    private fun toAddress(value: Int): String {
        return listOf(
            (value ushr 24) and 0xFF,
            (value ushr 16) and 0xFF,
            (value ushr 8) and 0xFF,
            value and 0xFF
        ).joinToString(".")
    }

    private fun maskForPrefix(prefixLength: Int): Int {
        return if (prefixLength == 0) 0 else (-1 shl (32 - prefixLength))
    }

    private fun DiscoveredDevice.ipAddressAsLong(): Long {
        return ipAddress.split('.').fold(0L) { acc, octet ->
            (acc shl 8) + (octet.toLongOrNull() ?: 0L)
        }
    }

    private data class ActiveNetwork(
        val broadcastAddress: String,
        val hosts: List<String>
    )

    private const val CONNECT_TIMEOUT_MS = 250
    private const val REACHABLE_TIMEOUT_MS = 350
    private const val SCAN_TIMEOUT_MS = 12000L
    private const val SCAN_PARALLELISM = 24
    private const val PROBE_PORT = 9
    private const val SSDP_PORT = 1900
    private val TCP_PORTS = listOf(22, 23, 53, 80, 139, 443, 445, 554, 631, 8080, 8443, 9100)
}