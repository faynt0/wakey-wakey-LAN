package com.example.wakeywakeylan

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.util.concurrent.Executors

class WakeDeviceActivity : Activity() {
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleWakeIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            handleWakeIntent(intent)
        }
    }

    private fun handleWakeIntent(intent: Intent) {
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
        val device = DeviceStore(this).loadDevices().firstOrNull { it.id == deviceId }
        if (device == null) {
            finish()
            return
        }

        backgroundExecutor.execute {
            val result = WakeOnLanManager.wake(device.macAddress, device.broadcastAddress)
            mainHandler.post {
                Toast.makeText(
                    this,
                    when (result) {
                        WakeResult.Sent -> getString(R.string.status_woke)
                        is WakeResult.InvalidMac -> "${getString(R.string.error_invalid_mac)} ${result.reason}"
                        is WakeResult.SendFailed -> "${getString(R.string.error_wake_failed)} ${result.reason}"
                    },
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    companion object {
        const val ACTION_WAKE_DEVICE = "com.example.wakeywakeylan.action.WAKE_DEVICE"
        const val EXTRA_DEVICE_ID = "extra_device_id"
    }
}