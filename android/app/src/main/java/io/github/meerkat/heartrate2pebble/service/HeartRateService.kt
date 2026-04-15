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
package io.github.meerkat.heartrate2pebble.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import io.github.meerkat.heartrate2pebble.data.ble.BleManager
import io.github.meerkat.heartrate2pebble.data.pebble.PebbleManager
import io.github.meerkat.heartrate2pebble.ui.MainActivity
import io.github.meerkat.heartrate2pebble.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import io.github.meerkat.heartrate2pebble.R

class HeartRateService : Service() {

    companion object {
        private const val TAG = "heartrate2pebble.Service"
        const val ACTION_STOP_SESSION = "io.github.meerkat.heartrate2pebble.STOP_SESSION"
        private const val NOTIFICATION_ID = 1
        private const val INACTIVITY_NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "heart_rate_session"
        private const val CHANNEL_NAME = "Heart Rate Session"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        // Shared managers — set before starting service, reused on Activity re-create
        var bleManager: BleManager? = null
        var pebbleManager: PebbleManager? = null

        // Pebble state — survives Activity re-creates
        var isPebbleEnabled = false

        fun start(context: Context) {
            val intent = Intent(context, HeartRateService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, HeartRateService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var connectTimeoutJob: Job? = null
    private var hrInactivityJob: Job? = null
    private var pebbleForwardJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        _isRunning.value = true
        Log.i(TAG, "Session started")
        startConnectTimeout()
        startHrInactivityMonitor()
        startPebbleForwarding()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SESSION) {
            Log.i(TAG, "Stop session requested via notification")
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "Session stopped")
        connectTimeoutJob?.cancel()
        hrInactivityJob?.cancel()
        pebbleForwardJob?.cancel()

        // Disconnect BLE and disable Pebble (covers notification stop + inactivity timeout)
        bleManager?.disconnect()
        if (isPebbleEnabled) {
            isPebbleEnabled = false
            pebbleManager?.let { pm ->
                CoroutineScope(Dispatchers.Main).launch { pm.toggleApp(false) }
            }
        }

        _isRunning.value = false
        serviceScope.cancel()
        super.onDestroy()
    }

    // ── Notification ──────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
        )
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, HeartRateService::class.java).apply {
            action = ACTION_STOP_SESSION
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Heart Rate Session")
            .setContentText("Monitoring heart rate")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openPending)
            .addAction(0, "Stop Session", stopPending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun sendInactivityNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Heartrate to Pebble")
            .setContentText("Session auto-paused due to inactivity")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        nm.notify(INACTIVITY_NOTIFICATION_ID, notification)
    }

    // ── Timeout logic ─────────────────────────────────────────────────────

    private fun startConnectTimeout() {
        val ble = bleManager ?: return
        connectTimeoutJob = serviceScope.launch {
            // If already connected, just monitor for disconnection
            if (ble.isConnected.value) return@launch

            // Wait up to 15 min for connection
            val startTime = System.currentTimeMillis()
            while (isActive) {
                delay(5_000) // check every 5 seconds
                if (ble.isConnected.value) {
                    Log.i(TAG, "Device connected, cancelling connect timeout")
                    return@launch
                }
                if (System.currentTimeMillis() - startTime >= Constants.CONNECT_TIMEOUT_MS) {
                    Log.i(TAG, "Connect timeout reached after ${Constants.CONNECT_TIMEOUT_MS}ms")
                    sendInactivityNotification()
                    stopSelf()
                    return@launch
                }
            }
        }
    }

    private fun startPebbleForwarding() {
        val ble = bleManager ?: return
        val pebble = pebbleManager ?: return
        pebbleForwardJob = serviceScope.launch {
            ble.heartRate.collect { bpmString ->
                val bpm = bpmString.toIntOrNull()
                if (isPebbleEnabled && bpm != null) {
                    pebble.sendBpm(bpm)
                }
            }
        }
    }

    private fun startHrInactivityMonitor() {
        val ble = bleManager ?: return
        hrInactivityJob = serviceScope.launch {
            var lastHr = ble.heartRate.value
            var lastMeaningfulChangeTime = System.currentTimeMillis()

            // Collect HR changes in a child coroutine
            val collectorJob = launch {
                ble.heartRate.collect { hr ->
                    if (hr != "--" && hr != "0" && hr != lastHr) {
                        lastHr = hr
                        lastMeaningfulChangeTime = System.currentTimeMillis()
                    }
                }
            }

            // Periodically check if timeout exceeded
            while (isActive) {
                delay(60_000) // check every minute
                val elapsed = System.currentTimeMillis() - lastMeaningfulChangeTime
                if (elapsed >= Constants.HR_INACTIVITY_TIMEOUT_MS) {
                    Log.i(TAG, "HR inactivity timeout reached after ${elapsed}ms")
                    collectorJob.cancel()
                    sendInactivityNotification()
                    stopSelf()
                    return@launch
                }
            }
        }
    }
}
