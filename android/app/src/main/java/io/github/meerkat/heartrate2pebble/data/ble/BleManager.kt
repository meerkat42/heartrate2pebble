/*
 * Copyright (C) 2026 meerkat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.meerkat.heartrate2pebble.data.ble

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.ContextCompat
import io.github.meerkat.heartrate2pebble.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class BleManager(private val context: Context) {

    companion object {
        private const val TAG = "heartrate2pebble.BLE"

        /** Parses BPM from a Heart Rate Measurement characteristic value. */
        internal fun parseHeartRate(data: ByteArray): Int {
            val flags = data[0].toInt()
            return if (flags and 0x01 == 0) {
                data[1].toInt() and 0xFF
            } else {
                (data[1].toInt() and 0xFF) or
                        ((data[2].toInt() and 0xFF) shl 8)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner get() = adapter?.bluetoothLeScanner

    // Heart rate state
    private val _heartRate = MutableStateFlow("--")
    val heartRate: StateFlow<String> = _heartRate.asStateFlow()

    // Scanning state
    val discoveredDevices = mutableStateListOf<BleDeviceItem>()
    private val foundDevices = mutableListOf<BluetoothDevice>()
    private val batteryGatts = mutableListOf<BluetoothGatt>()

    private val _scanStatus = MutableStateFlow("Scanning for devices...")
    val scanStatus: StateFlow<String> = _scanStatus.asStateFlow()

    // Connection state
    private var gatt: BluetoothGatt? = null
    private var heartRateTimeoutRunnable: Runnable? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // Callbacks for ViewModel
    var onDevicePaired: ((address: String, name: String) -> Unit)? = null
    var onDeviceDisconnected: (() -> Unit)? = null

    val isAdapterEnabled: Boolean get() = adapter?.isEnabled == true

    fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    // ── BLE Scanning ──────────────────────────────────────────────────────

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        foundDevices.clear()
        discoveredDevices.clear()
        _scanStatus.value = "Scanning for devices..."

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(Constants.HR_SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        Log.i(TAG, "BLE scan started (HR service filter, timeout=${Constants.SCAN_PERIOD_MS}ms)")
        bleScanner?.startScan(listOf(filter), settings, scanCallback)

        handler.postDelayed({
            bleScanner?.stopScan(scanCallback)
            Log.i(TAG, "BLE scan stopped -- found ${foundDevices.size} device(s)")
            _scanStatus.value = if (foundDevices.isEmpty()) "No devices found."
            else "${foundDevices.size} device(s) found."
        }, Constants.SCAN_PERIOD_MS)
    }

    fun stopScan() {
        try {
            bleScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) { }
    }

    fun cancelScan() {
        stopScan()
        batteryGatts.forEach {
            try { it.disconnect() } catch (_: SecurityException) { }
        }
        batteryGatts.clear()
    }

    private val scanCallback = object : ScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (foundDevices.none { it.address == device.address }) {
                foundDevices.add(device)
                val name = device.name ?: device.address
                Log.i(TAG, "Found device: name=$name address=${device.address} rssi=${result.rssi}")
                handler.post {
                    discoveredDevices.add(BleDeviceItem(device, name))
                }
                readBatteryLevel(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with error code $errorCode")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun readBatteryLevel(device: BluetoothDevice) {
        val batteryGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    batteryGatts.remove(gatt)
                    gatt.close()
                }
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val battery = gatt
                    .getService(Constants.BATTERY_SERVICE_UUID)
                    ?.getCharacteristic(Constants.BATTERY_LEVEL_UUID)

                if (battery != null) {
                    gatt.readCharacteristic(battery)
                } else {
                    updateDeviceLabel(device, "\uD83D\uDD0B?")
                    gatt.disconnect()
                }
            }

            private fun handleBatteryRead(gatt: BluetoothGatt, uuid: UUID, value: ByteArray, status: Int) {
                if (uuid == Constants.BATTERY_LEVEL_UUID && status == BluetoothGatt.GATT_SUCCESS) {
                    val level = value[0].toInt() and 0xFF
                    val icon = if (level >= 25) "\uD83D\uDD0B" else "\uD83E\uDEAB"
                    updateDeviceLabel(device, "$icon $level%")
                }
                gatt.disconnect()
            }

            // API 33+
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                handleBatteryRead(gatt, characteristic.uuid, value, status)
            }

            // Pre-API 33
            @Deprecated("Deprecated in Java")
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                @Suppress("DEPRECATION")
                handleBatteryRead(gatt, characteristic.uuid, characteristic.value, status)
            }
        })
        batteryGatts.add(batteryGatt)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun updateDeviceLabel(device: BluetoothDevice, batteryText: String) {
        val index = foundDevices.indexOfFirst { it.address == device.address }
        if (index == -1) return
        val name = device.name ?: device.address
        handler.post {
            if (index < discoveredDevices.size) {
                discoveredDevices[index] = BleDeviceItem(device, name, batteryText)
            }
        }
    }

    // ── GATT connection ───────────────────────────────────────────────────

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectToDevice(device: BluetoothDevice) {
        Log.i(TAG, "Connecting to device: name=${device.name} address=${device.address}")
        gatt?.close()
        gatt = device.connectGatt(context, false, gattCallback)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectToAddress(address: String) {
        val device = adapter?.getRemoteDevice(address) ?: return
        connectToDevice(device)
    }

    fun disconnect() {
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: SecurityException) { }
        gatt = null
        _isConnected.value = false
        resetHeartRate()
    }

    fun close() {
        stopScan()
        try {
            gatt?.close()
        } catch (_: SecurityException) { }
        gatt = null
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val stateStr = when (newState) {
                BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
                BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
                else -> "state=$newState"
            }
            Log.i(TAG, "GATT connection state changed: $stateStr (status=$status) device=${gatt.device.address}")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> gatt.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _isConnected.value = false
                    handler.post {
                        resetHeartRate()
                        onDeviceDisconnected?.invoke()
                    }
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "onServicesDiscovered failed: status=$status")
                return
            }

            val characteristic = gatt
                .getService(Constants.HR_SERVICE_UUID)
                ?.getCharacteristic(Constants.HR_MEASUREMENT_UUID) ?: run {
                Log.e(TAG, "HR measurement characteristic not found on device=${gatt.device.address}")
                return
            }

            Log.i(TAG, "Services discovered, enabling HR notifications on device=${gatt.device.address}")
            gatt.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(Constants.CLIENT_CHAR_CONFIG)
            descriptor?.let {
                if (Build.VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(it, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(it)
                }
            }

            val name = gatt.device.name ?: gatt.device.address
            Log.i(TAG, "Paired with device: $name")
            _isConnected.value = true
            handler.post { onDevicePaired?.invoke(gatt.device.address, name) }
        }

        private fun handleHrChanged(uuid: UUID, value: ByteArray) {
            if (uuid != Constants.HR_MEASUREMENT_UUID) return
            val bpm = parseHeartRate(value)
            Log.d(TAG, "Heart rate reading found")
            _heartRate.value = bpm.toString()
            resetHeartRateTimeout()
        }

        // API 33+
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleHrChanged(characteristic.uuid, value)
        }

        // Pre-API 33
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            handleHrChanged(characteristic.uuid, characteristic.value)
        }
    }

    // ── Heart rate helpers ────────────────────────────────────────────────

    private fun resetHeartRate() {
        heartRateTimeoutRunnable?.let { handler.removeCallbacks(it) }
        _heartRate.value = "--"
    }

    private fun resetHeartRateTimeout() {
        heartRateTimeoutRunnable?.let { handler.removeCallbacks(it) }
        heartRateTimeoutRunnable = Runnable {
            _heartRate.value = "--"
        }
        handler.postDelayed(heartRateTimeoutRunnable!!, Constants.HR_TIMEOUT_MS)
    }
}
