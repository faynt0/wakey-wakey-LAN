package com.example.wakeywakeylan

import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.InputMethodManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var store: DeviceStore
    private lateinit var adapter: HomeAdapter
    private lateinit var scanButton: Button
    private lateinit var addButton: Button
    private lateinit var refreshShortcutsButton: Button
    private lateinit var statusText: TextView
    private val mainHandler = Handler(Looper.getMainLooper())
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private var scanVersion = 0
    private var discoveredDevices: List<DiscoveredDevice> = emptyList()
    private var savedDevices: List<SavedDevice> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = DeviceStore(this)
        adapter = HomeAdapter(
            onWake = { wakeDevice(it) },
            onEdit = { device -> openDeviceDialog(existing = device, discovered = null) },
            onSaveDiscovered = { discovered -> openDeviceDialog(existing = null, discovered = discovered) }
        )

        scanButton = findViewById(R.id.button_scan)
        addButton = findViewById(R.id.button_add)
        refreshShortcutsButton = findViewById(R.id.button_refresh_shortcuts)
        statusText = findViewById(R.id.text_status)
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_devices)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        scanButton.setOnClickListener { scanForDevices() }
        addButton.setOnClickListener { openManualDeviceDialog() }
        refreshShortcutsButton.setOnClickListener { publishShortcuts(savedDevices) }

        loadDevices()
    }

    override fun onResume() {
        super.onResume()
        loadDevices()
    }

    private fun loadDevices() {
        savedDevices = store.loadDevices()
        renderDevices()
        if (savedDevices.isEmpty() && discoveredDevices.isEmpty()) {
            statusText.text = getString(R.string.status_no_devices)
        } else {
            statusText.text = getString(R.string.status_ready)
        }
    }

    private fun renderDevices() {
        val visibleSavedDevices = if (savedDevices.size > 1) {
            savedDevices.filterNot { isDefaultDevice(it) }
        } else {
            savedDevices
        }

        val items = buildList {
            add(HomeListItem.Header(getString(R.string.discovered_devices)))
            addAll(discoveredDevices.map { HomeListItem.DiscoveredRow(it) })
            add(HomeListItem.Header(getString(R.string.saved_devices)))
            addAll(visibleSavedDevices.map { HomeListItem.SavedRow(it) })
        }
        adapter.submit(items)
    }

    private fun scanForDevices() {
        statusText.text = getString(R.string.status_scanning)
        scanButton.isEnabled = false
        val currentScan = ++scanVersion

        backgroundExecutor.execute {
            val results = NetworkScanner.scan()
            mainHandler.post {
                if (currentScan != scanVersion) {
                    return@post
                }
                discoveredDevices = results.filterNot { discovered ->
                    savedDevices.any { it.macAddress.equals(discovered.macAddress, ignoreCase = true) }
                }
                scanButton.isEnabled = true
                renderDevices()
                statusText.text = if (discoveredDevices.isEmpty()) {
                    getString(R.string.status_no_devices)
                } else {
                    "${discoveredDevices.size} device(s) discovered."
                }
            }
        }
    }

    private fun openManualDeviceDialog() {
        openDeviceDialog(existing = null, discovered = null)
    }

    private fun openDeviceDialog(existing: SavedDevice?, discovered: DiscoveredDevice?) {
        val content = LayoutInflater.from(this).inflate(R.layout.dialog_device, null, false)
        val nameField = content.findViewById<TextInputEditText>(R.id.input_name)
        val ipField = content.findViewById<TextInputEditText>(R.id.input_ip)
        val macField = content.findViewById<TextInputEditText>(R.id.input_mac)
        val broadcastField = content.findViewById<TextInputEditText>(R.id.input_broadcast)

        nameField?.setText(existing?.name ?: discovered?.ipAddress.orEmpty())
        ipField?.setText(existing?.ipAddress ?: discovered?.ipAddress.orEmpty())
        macField?.setText(existing?.macAddress ?: discovered?.macAddress.orEmpty())
        broadcastField?.setText(existing?.broadcastAddress ?: discovered?.broadcastAddress.orEmpty())

        MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) getString(R.string.manual_device_title) else getString(R.string.save_device_title))
            .setMessage(getString(R.string.manual_device_message))
            .setView(content)
            .setPositiveButton(R.string.dialog_save, null)
            .setNegativeButton(R.string.dialog_cancel, null)
            .apply {
                if (existing != null) {
                    setNeutralButton(R.string.remove, null)
                }
            }
            .create()
            .apply {
                setCanceledOnTouchOutside(false)
                setOnShowListener {
                    val saveButton = getButton(android.content.DialogInterface.BUTTON_POSITIVE)
                    val cancelButton = getButton(android.content.DialogInterface.BUTTON_NEGATIVE)
                    val deleteButton = getButton(android.content.DialogInterface.BUTTON_NEUTRAL)

                    cancelButton?.setOnClickListener { dismiss() }
                    deleteButton?.setOnClickListener {
                        if (existing != null) {
                            removeDevice(existing)
                        }
                        dismiss()
                    }
                    saveButton.setOnClickListener {
                        val name = nameField?.text?.toString().orEmpty().trim()
                        val ipAddress = ipField?.text?.toString().orEmpty().trim()
                        val macAddress = normalizeMac(macField?.text?.toString().orEmpty())
                        val broadcastAddress = broadcastField?.text?.toString().orEmpty().trim().ifBlank { "255.255.255.255" }

                        when {
                            name.isBlank() -> toastValidation(getString(R.string.error_name_required))
                            macAddress == null -> toastValidation(getString(R.string.error_invalid_mac))
                            ipAddress.isNotBlank() && !isValidIpAddress(ipAddress) -> toastValidation(getString(R.string.error_invalid_ip))
                            else -> {
                                val updated = savedDevices.toMutableList()
                                val device = SavedDevice(
                                    id = existing?.id ?: UUID.randomUUID().toString(),
                                    name = name,
                                    ipAddress = ipAddress,
                                    macAddress = macAddress,
                                    broadcastAddress = broadcastAddress
                                )
                                updated.removeAll { it.id == device.id || it.macAddress.equals(device.macAddress, ignoreCase = true) }
                                updated.add(device)
                                store.saveDevices(updated)
                                savedDevices = store.loadDevices()
                                discoveredDevices = discoveredDevices.filterNot { it.macAddress.equals(device.macAddress, ignoreCase = true) }
                                publishShortcuts(savedDevices)
                                renderDevices()
                                statusText.text = getString(R.string.status_saved)
                                dismiss()
                            }
                        }
                    }

                    nameField?.isFocusableInTouchMode = true
                    nameField?.isFocusable = true
                    nameField?.post {
                        nameField.requestFocus()
                        nameField.setSelection(nameField.text?.length ?: 0)
                        val imm = getSystemService(InputMethodManager::class.java)
                        imm?.showSoftInput(nameField, InputMethodManager.SHOW_IMPLICIT)
                    }
                    window?.setLayout((resources.displayMetrics.widthPixels * 0.88).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
                    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                }
            }
            .show()
    }

    private fun toastValidation(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun wakeDevice(device: SavedDevice) {
        statusText.text = getString(R.string.status_scanning)
        backgroundExecutor.execute {
            val result = WakeOnLanManager.wake(device.macAddress, device.broadcastAddress)
            mainHandler.post {
                statusText.text = when (result) {
                    WakeResult.Sent -> getString(R.string.status_woke)
                    is WakeResult.InvalidMac -> "${getString(R.string.error_invalid_mac)} ${result.reason}"
                    is WakeResult.SendFailed -> "${getString(R.string.error_wake_failed)} ${result.reason}"
                }
                Toast.makeText(this, statusText.text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun removeDevice(device: SavedDevice) {
        val updated = savedDevices.filterNot { it.id == device.id }
        store.saveDevices(updated)
        savedDevices = store.loadDevices()
        publishShortcuts(savedDevices)
        renderDevices()
        statusText.text = getString(R.string.status_deleted)
    }

    private fun publishShortcuts(devices: List<SavedDevice>) {
        ShortcutPublisher.publish(this, devices)
        Toast.makeText(this, getString(R.string.shortcut_refresh_done), Toast.LENGTH_SHORT).show()
    }

    private fun normalizeMac(value: String): String? {
        val cleaned = value.trim().replace('-', ':').uppercase()
        val parts = cleaned.split(':')
        if (parts.size != 6 || parts.any { it.length != 2 || it.any { char -> !char.isDigit() && char !in 'A'..'F' } }) {
            return null
        }
        return parts.joinToString(":")
    }

    private fun isValidIpAddress(value: String): Boolean {
        val parts = value.split('.')
        if (parts.size != 4) {
            return false
        }
        return parts.all { part ->
            val number = part.toIntOrNull() ?: return false
            number in 0..255
        }
    }

    private fun isDefaultDevice(device: SavedDevice): Boolean {
        return device.name == "Notbook" &&
            device.ipAddress == "192.168.0.20" &&
            device.macAddress.equals("10:65:30:ED:87:69", ignoreCase = true)
    }
}

private class HomeAdapter(
    private val onWake: (SavedDevice) -> Unit,
    private val onEdit: (SavedDevice) -> Unit,
    private val onSaveDiscovered: (DiscoveredDevice) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val items = mutableListOf<HomeListItem>()

    fun submit(newItems: List<HomeListItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is HomeListItem.Header -> TYPE_HEADER
        is HomeListItem.DiscoveredRow, is HomeListItem.SavedRow -> TYPE_DEVICE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(inflater.inflate(R.layout.item_section_header, parent, false))
        } else {
            DeviceHolder(inflater.inflate(R.layout.item_device_row, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is HomeListItem.Header -> (holder as HeaderHolder).bind(item)
            is HomeListItem.DiscoveredRow -> (holder as DeviceHolder).bindDiscovered(item, onSaveDiscovered, onWake)
            is HomeListItem.SavedRow -> (holder as DeviceHolder).bindSaved(item, onWake, onEdit)
        }
    }

    override fun getItemCount(): Int = items.size

    private class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleView: TextView = view.findViewById(R.id.text_section_title)
        fun bind(item: HomeListItem.Header) {
            titleView.text = item.title
        }
    }

    private class DeviceHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nameView: TextView = view.findViewById(R.id.text_device_name)
        private val detailView: TextView = view.findViewById(R.id.text_device_detail)

        fun bindDiscovered(
            item: HomeListItem.DiscoveredRow,
            onSave: (DiscoveredDevice) -> Unit,
            onWake: (SavedDevice) -> Unit
        ) {
            val device = item.device
            nameView.text = device.ipAddress
            detailView.text = listOfNotNull(
                device.macAddress.takeIf { it.isNotBlank() }?.let { "MAC $it" },
                "Broadcast ${device.broadcastAddress}"
            ).joinToString("  •  ")
            itemView.setOnClickListener {
                if (device.macAddress.isBlank()) {
                    onSave(device)
                } else {
                    onWake(
                        SavedDevice(
                            id = device.macAddress,
                            name = device.ipAddress,
                            ipAddress = device.ipAddress,
                            macAddress = device.macAddress,
                            broadcastAddress = device.broadcastAddress
                        )
                    )
                }
            }
            itemView.setOnLongClickListener {
                onSave(device)
                true
            }
        }

        fun bindSaved(
            item: HomeListItem.SavedRow,
            onWake: (SavedDevice) -> Unit,
            onEdit: (SavedDevice) -> Unit
        ) {
            val device = item.device
            nameView.text = device.name
            detailView.text = listOfNotNull(
                device.ipAddress.takeIf { it.isNotBlank() }?.let { "IP $it" },
                "MAC ${device.macAddress}",
                device.broadcastAddress.takeIf { it.isNotBlank() }?.let { "Broadcast $it" }
            ).joinToString("  •  ")
            itemView.setOnClickListener { onWake(device) }
            itemView.setOnLongClickListener {
                onEdit(device)
                true
            }
        }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_DEVICE = 1
    }
}