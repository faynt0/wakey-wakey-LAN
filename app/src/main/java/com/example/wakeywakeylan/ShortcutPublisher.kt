package com.example.wakeywakeylan

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon

object ShortcutPublisher {
    fun publish(context: Context, devices: List<SavedDevice>) {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
        val shortcuts = devices.take(MAX_SHORTCUTS).map { device ->
            ShortcutInfo.Builder(context, device.id)
                .setShortLabel(device.name.take(MAX_LABEL_LENGTH))
                .setLongLabel(device.name)
                .setIntent(
                    Intent(context, WakeDeviceActivity::class.java)
                        .setAction(WakeDeviceActivity.ACTION_WAKE_DEVICE)
                        .putExtra(WakeDeviceActivity.EXTRA_DEVICE_ID, device.id)
                )
                .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                .build()
        }
        shortcutManager.setDynamicShortcuts(shortcuts)
    }

    private const val MAX_SHORTCUTS = 15
    private const val MAX_LABEL_LENGTH = 10
}