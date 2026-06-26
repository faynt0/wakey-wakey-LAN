package com.example.wakeywakeylan

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class DeviceStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadDevices(): MutableList<SavedDevice> {
        val raw = preferences.getString(KEY_DEVICES, "[]") ?: "[]"
        val array = JSONArray(raw)
        val devices = mutableListOf<SavedDevice>()
        for (index in 0 until array.length()) {
            val json = array.getJSONObject(index)
            devices += SavedDevice(
                id = json.optString("id"),
                name = json.optString("name"),
                ipAddress = json.optString("ipAddress"),
                macAddress = json.optString("macAddress"),
                broadcastAddress = json.optString("broadcastAddress")
            )
        }

        if (devices.isEmpty()) {
            devices.add(defaultDevice())
            saveDevices(devices)
        }

        return devices
    }

    fun saveDevices(devices: List<SavedDevice>) {
        val array = JSONArray()
        devices.forEach { device ->
            array.put(
                JSONObject()
                    .put("id", device.id)
                    .put("name", device.name)
                    .put("ipAddress", device.ipAddress)
                    .put("macAddress", device.macAddress)
                    .put("broadcastAddress", device.broadcastAddress)
            )
        }
        preferences.edit().putString(KEY_DEVICES, array.toString()).apply()
    }

    private fun defaultDevice(): SavedDevice {
        return SavedDevice(
            id = DEFAULT_DEVICE_ID,
            name = DEFAULT_DEVICE_NAME,
            ipAddress = DEFAULT_DEVICE_IP,
            macAddress = DEFAULT_DEVICE_MAC,
            broadcastAddress = DEFAULT_DEVICE_BROADCAST
        )
    }

    private fun isDefaultDevice(device: SavedDevice): Boolean {
        return device.id == DEFAULT_DEVICE_ID ||
            (device.name == DEFAULT_DEVICE_NAME &&
                device.ipAddress == DEFAULT_DEVICE_IP &&
                device.macAddress.equals(DEFAULT_DEVICE_MAC, ignoreCase = true))
    }

    companion object {
        private const val PREFS_NAME = "wakeywakeylan.devices"
        private const val KEY_DEVICES = "devices"
        private const val DEFAULT_DEVICE_ID = "default-notbook"
        private const val DEFAULT_DEVICE_NAME = "Notbook"
        private const val DEFAULT_DEVICE_IP = "192.168.0.20"
        private const val DEFAULT_DEVICE_MAC = "10:65:30:ED:87:69"
        private const val DEFAULT_DEVICE_BROADCAST = "192.168.0.255"
    }
}