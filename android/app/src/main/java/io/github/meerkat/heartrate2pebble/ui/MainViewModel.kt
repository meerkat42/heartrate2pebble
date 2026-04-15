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
package io.github.meerkat.heartrate2pebble.ui

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.meerkat.heartrate2pebble.data.ble.BleDeviceItem
import io.github.meerkat.heartrate2pebble.data.ble.BleManager
import io.github.meerkat.heartrate2pebble.data.local.DeviceRepository
import io.github.meerkat.heartrate2pebble.data.pebble.PebbleManager
import io.github.meerkat.heartrate2pebble.service.HeartRateService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val appContext: Context,
    private val bleManager: BleManager,
    private val pebbleManager: PebbleManager,
    private val repository: DeviceRepository
) : ViewModel() {

    companion object {
        private const val TAG = "heartrate2pebble"
    }

    // Forwarded state from managers
    val heartRate: StateFlow<String> = bleManager.heartRate
    val pebbleConnected: StateFlow<Boolean> = pebbleManager.pebbleConnected
    val scanStatus: StateFlow<String> = bleManager.scanStatus
    val discoveredDevices = bleManager.discoveredDevices

    // ViewModel-owned UI state
    var deviceName by mutableStateOf("")
        private set
    var isPaired by mutableStateOf(false)
        private set
    var isPebbleEnabled by mutableStateOf(false)
        private set
    var showPicker by mutableStateOf(false)
        private set

    // Terms dialog state
    var showTermsDialog by mutableStateOf(false)
        private set
    var termsAccepted by mutableStateOf(false)
        private set

    // Warning flags
    var batteryOptimized by mutableStateOf(false)
    var notificationsDenied by mutableStateOf(false)

    // One-shot snackbar messages
    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    // Session state (from service)
    val isSessionActive: StateFlow<Boolean> = HeartRateService.Companion.isRunning

    init {
        // Check terms acceptance on launch
        viewModelScope.launch {
            val accepted = repository.hasAcceptedTerms()
            termsAccepted = accepted
            showTermsDialog = !accepted
        }

        // Wire up BLE callbacks
        bleManager.onDevicePaired = { address, name ->
            deviceName = name
            isPaired = true
            viewModelScope.launch {
                repository.saveDevice(address, name)
                Log.i(TAG, "Saved device: name=$name address=$address")
            }
        }
        bleManager.onDeviceDisconnected = {
            deviceName = ""
            isPaired = false
        }

        // If reopening during an active session, restore UI state from live managers
        if (HeartRateService.Companion.isRunning.value) {
            isPebbleEnabled = HeartRateService.Companion.isPebbleEnabled
            viewModelScope.launch {
                val saved = repository.getSavedDevice()
                if (saved != null) {
                    deviceName = saved.second
                    isPaired = bleManager.isConnected.value
                }
            }
        }

        // Sync UI when session stops externally (notification button, inactivity timeout)
        viewModelScope.launch {
            HeartRateService.Companion.isRunning.collect { running ->
                if (!running && isPebbleEnabled) {
                    isPebbleEnabled = false
                }
            }
        }
    }

    fun startScan() {
        showPicker = true
        bleManager.startScan()
    }

    fun onDeviceSelected(item: BleDeviceItem) {
        bleManager.stopScan()
        bleManager.cancelScan()
        showPicker = false
        // Save device but don't connect — connection happens on Start Session
        deviceName = item.name
        isPaired = true
        viewModelScope.launch {
            repository.saveDevice(item.device.address, item.name)
            Log.i(TAG, "Saved device: name=${item.name} address=${item.device.address}")
        }
    }

    fun closePicker() {
        bleManager.cancelScan()
        showPicker = false
    }

    fun unpair() {
        if (HeartRateService.Companion.isRunning.value) {
            HeartRateService.Companion.stop(appContext)
        }
        bleManager.disconnect()
        if (isPebbleEnabled) {
            isPebbleEnabled = false
            HeartRateService.Companion.isPebbleEnabled = false
            viewModelScope.launch { pebbleManager.toggleApp(false) }
        }
        deviceName = ""
        isPaired = false
        viewModelScope.launch {
            repository.clearDevice()
            Log.i(TAG, "Cleared saved device")
        }
    }

    fun togglePebble(enabled: Boolean) {
        isPebbleEnabled = enabled
        HeartRateService.Companion.isPebbleEnabled = enabled
        Log.i(TAG, "Send-to-Pebble toggled: enabled=$enabled")
        viewModelScope.launch {
            val success = pebbleManager.toggleApp(enabled)
            if (enabled && !success) {
                _snackbarMessage.tryEmit(
                    "If the app does not open on the watch, please ensure the watch is connected in the Pebble app."
                )
            }
        }
    }

    fun loadSavedDevice() {
        viewModelScope.launch {
            val (_, name) = repository.getSavedDevice() ?: return@launch
            deviceName = name
            isPaired = true
        }
    }

    fun startSession() {
        HeartRateService.Companion.bleManager = bleManager
        HeartRateService.Companion.pebbleManager = pebbleManager
        HeartRateService.Companion.start(appContext)
        Log.i(TAG, "Session started")

        // Connect to saved device when session starts
        viewModelScope.launch {
            val (address, name) = repository.getSavedDevice() ?: return@launch
            if (!bleManager.isAdapterEnabled) return@launch
            if (!bleManager.hasConnectPermission()) return@launch

            Log.i(TAG, "Connecting to saved device: name=$name address=$address")
            deviceName = name
            isPaired = true
            bleManager.connectToAddress(address)
        }
    }

    fun stopSession() {
        HeartRateService.Companion.stop(appContext)
        bleManager.disconnect()
        if (isPebbleEnabled) {
            isPebbleEnabled = false
            HeartRateService.Companion.isPebbleEnabled = false
            viewModelScope.launch { pebbleManager.toggleApp(false) }
        }
        Log.i(TAG, "Session stopped")
    }

    fun acceptTerms() {
        viewModelScope.launch {
            repository.setTermsAccepted()
            termsAccepted = true
            showTermsDialog = false
        }
    }

    fun declineTerms() {
        showTermsDialog = false
        termsAccepted = false
    }

    fun cleanup() {
        if (!HeartRateService.Companion.isRunning.value) {
            bleManager.close()
        }
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }
}
