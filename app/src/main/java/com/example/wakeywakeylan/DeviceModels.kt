package com.example.wakeywakeylan

data class SavedDevice(
    val id: String,
    val name: String,
    val ipAddress: String,
    val macAddress: String,
    val broadcastAddress: String
)

data class DiscoveredDevice(
    val ipAddress: String,
    val macAddress: String,
    val broadcastAddress: String
)

sealed class HomeListItem {
    data class Header(val title: String) : HomeListItem()
    data class DiscoveredRow(val device: DiscoveredDevice) : HomeListItem()
    data class SavedRow(val device: SavedDevice) : HomeListItem()
}