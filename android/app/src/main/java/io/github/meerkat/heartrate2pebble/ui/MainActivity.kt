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

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.meerkat.heartrate2pebble.data.ble.BleManager
import io.github.meerkat.heartrate2pebble.data.local.DeviceRepository
import io.github.meerkat.heartrate2pebble.data.pebble.PebbleManager
import io.github.meerkat.heartrate2pebble.service.HeartRateService
import io.github.meerkat.heartrate2pebble.ui.components.HeartRateScreen
import io.github.meerkat.heartrate2pebble.ui.theme.Heartrate2PebbleTheme

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            viewModel.startScan()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.notificationsDenied = !granted }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Reuse managers from active session, or create new ones
        val sessionActive = HeartRateService.Companion.isRunning.value
        val bleManager = if (sessionActive && HeartRateService.Companion.bleManager != null) {
            HeartRateService.Companion.bleManager!!
        } else {
            BleManager(applicationContext)
        }
        val pebbleManager = if (sessionActive && HeartRateService.Companion.pebbleManager != null) {
            HeartRateService.Companion.pebbleManager!!
        } else {
            PebbleManager(applicationContext)
        }
        val repository = DeviceRepository(applicationContext)

        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(applicationContext, bleManager, pebbleManager, repository) as T
            }
        }

        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        viewModel.batteryOptimized = !pm.isIgnoringBatteryOptimizations(packageName)

        // Request notification permission for foreground service on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            viewModel.notificationsDenied = !granted
        }

        viewModel.loadSavedDevice()

        setContent {
            Heartrate2PebbleTheme {
                HeartRateScreen(
                    viewModel = viewModel,
                    onPermissionRequest = { requestPermissionsAndScan() }
                )
            }
        }
    }

    private fun requestPermissionsAndScan() {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isEmpty()) {
            viewModel.startScan()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Only clean up BLE if no active session — the service keeps managers alive
        if (isFinishing && !HeartRateService.Companion.isRunning.value) {
            viewModel.cleanup()
        }
    }
}
