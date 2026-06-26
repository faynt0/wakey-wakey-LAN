package com.example.wakeywakeylan

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.UnknownHostException

object WakeOnLanManager {
    fun wake(macAddress: String, broadcastAddress: String): WakeResult {
        Log.d(TAG, "wake() mac=$macAddress broadcast=$broadcastAddress")

        val macBytes = parseMac(macAddress)
            ?: return WakeResult.InvalidMac("MAC must be 6 pairs like XX:XX:XX:XX:XX:XX")

        val packetBytes = ByteArray(6 + 16 * macBytes.size)
        repeat(6) { packetBytes[it] = 0xFF.toByte() }
        for (index in 6 until packetBytes.size) {
            packetBytes[index] = macBytes[(index - 6) % macBytes.size]
        }

        val targets = buildList {
            val preferred = broadcastAddress.trim().ifBlank { DEFAULT_BROADCAST }
            add(preferred)
            add(DEFAULT_BROADCAST)
            add("255.255.255.255")
        }.distinct()

        Log.d(TAG, "wake() packetBytes=${packetBytes.size} targets=$targets")

        val failures = mutableListOf<String>()
        for (target in targets) {
            when (val result = sendPacket(packetBytes, target)) {
                is PacketResult.Sent -> {
                    Log.d(TAG, "wake() success target=$target")
                    return WakeResult.Sent
                }
                is PacketResult.Failed -> {
                    failures += "$target: ${result.reason}"
                    Log.w(TAG, "wake() failed target=$target reason=${result.reason}", result.error)
                }
            }
        }

        return WakeResult.SendFailed("No target accepted the packet: ${failures.joinToString(" | ")}")
    }

    private fun parseMac(value: String): ByteArray? {
        val normalized = value.replace('-', ':').trim()
        val parts = normalized.split(':')
        if (parts.size != 6) {
            Log.w(TAG, "parseMac() rejected=$value reason=wrong part count")
            return null
        }
        return try {
            ByteArray(6) { index ->
                val part = parts[index].trim()
                if (part.length != 2) {
                    throw IllegalArgumentException("Invalid MAC part length")
                }
                part.toInt(16).toByte()
            }
        } catch (exception: Exception) {
            Log.w(TAG, "parseMac() rejected=$value reason=${exception.message}", exception)
            null
        }
    }

    private fun sendPacket(packetBytes: ByteArray, target: String): PacketResult {
        return try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                val address = InetAddress.getByName(target)
                val packet = DatagramPacket(packetBytes, packetBytes.size, address, PORT)
                socket.send(packet)
            }
            PacketResult.Sent
        } catch (exception: UnknownHostException) {
            PacketResult.Failed("invalid target", exception)
        } catch (exception: Exception) {
            PacketResult.Failed(exception.message ?: exception.javaClass.simpleName, exception)
        }
    }

    private const val PORT = 9
    private const val DEFAULT_BROADCAST = "255.255.255.255"
    private const val TAG = "WOL"
}

sealed class WakeResult {
    data object Sent : WakeResult()
    data class InvalidMac(val reason: String) : WakeResult()
    data class SendFailed(val reason: String) : WakeResult()
}

private sealed class PacketResult {
    data object Sent : PacketResult()
    data class Failed(val reason: String, val error: Throwable) : PacketResult()
}