package com.example.wakeywakeylan

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CreateShortcutActivity : Activity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var cancelButton: Button
    private val store by lazy { DeviceStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shortcut_picker)

        recyclerView = findViewById(R.id.recycler_shortcut_devices)
        emptyView = findViewById(R.id.text_empty_shortcuts)
        cancelButton = findViewById(R.id.button_cancel_shortcut)

        cancelButton.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        val devices = store.loadDevices()
        val pickerAdapter = ShortcutPickerAdapter { device ->
            finishWithShortcut(device)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = pickerAdapter
        pickerAdapter.submit(devices)

        emptyView.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (devices.isEmpty()) View.GONE else View.VISIBLE

        if (devices.isEmpty()) {
            Toast.makeText(this, R.string.shortcut_no_devices, Toast.LENGTH_SHORT).show()
        }
    }

    private fun finishWithShortcut(device: SavedDevice) {
        val shortcutIntent = Intent(this, WakeDeviceActivity::class.java).apply {
            action = WakeDeviceActivity.ACTION_WAKE_DEVICE
            putExtra(WakeDeviceActivity.EXTRA_DEVICE_ID, device.id)
        }

        val result = Intent().apply {
            putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
            putExtra(Intent.EXTRA_SHORTCUT_NAME, device.name)
            putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(this@CreateShortcutActivity, R.mipmap.ic_launcher))
        }

        setResult(RESULT_OK, result)
        finish()
    }
}

private class ShortcutPickerAdapter(
    private val onSelect: (SavedDevice) -> Unit
) : RecyclerView.Adapter<ShortcutPickerAdapter.ShortcutHolder>() {
    private val items = mutableListOf<SavedDevice>()

    fun submit(devices: List<SavedDevice>) {
        items.clear()
        items.addAll(devices)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ShortcutHolder {
        val view = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_shortcut_device, parent, false)
        return ShortcutHolder(view)
    }

    override fun onBindViewHolder(holder: ShortcutHolder, position: Int) {
        holder.bind(items[position], onSelect)
    }

    override fun getItemCount(): Int = items.size

    class ShortcutHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nameView: TextView = view.findViewById(R.id.text_shortcut_name)
        private val detailView: TextView = view.findViewById(R.id.text_shortcut_detail)

        fun bind(device: SavedDevice, onSelect: (SavedDevice) -> Unit) {
            nameView.text = device.name
            detailView.text = listOfNotNull(
                device.ipAddress.takeIf { it.isNotBlank() }?.let { "IP $it" },
                "MAC ${device.macAddress}"
            ).joinToString("  •  ")
            itemView.setOnClickListener { onSelect(device) }
            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = true
        }
    }
}