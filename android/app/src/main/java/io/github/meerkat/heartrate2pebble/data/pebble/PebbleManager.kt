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
package io.github.meerkat.heartrate2pebble.data.pebble

import android.content.Context
import android.util.Log
import io.github.meerkat.heartrate2pebble.util.Constants
import io.rebble.pebblekit2.client.DefaultPebbleSender
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import io.rebble.pebblekit2.common.model.TransmissionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PebbleManager(context: Context) {

    companion object {
        private const val TAG = "heartrate2pebble.Pebble"
    }

    private val pebbleSender = DefaultPebbleSender(context)
    private var pebbleAppReady = false

    private val _pebbleConnected = MutableStateFlow(false)
    val pebbleConnected: StateFlow<Boolean> = _pebbleConnected.asStateFlow()

    /** @return true if the app was successfully started/stopped on at least one watch */
    suspend fun toggleApp(enable: Boolean): Boolean {
        if (enable) {
            Log.i(TAG, "Opening app on Pebble: uuid=${Constants.PEBBLE_APP_UUID}")
            val launchResult = pebbleSender.startAppOnTheWatch(Constants.PEBBLE_APP_UUID)
            Log.i(TAG, "startAppOnTheWatch result: $launchResult")
            pebbleAppReady = true
            val success = launchResult != null &&
                    launchResult.values.any { it == TransmissionResult.Success }
            return success
        } else {
            pebbleAppReady = false
            Log.i(TAG, "Closing app on Pebble: uuid=${Constants.PEBBLE_APP_UUID}")
            val launchResult = pebbleSender.stopAppOnTheWatch(Constants.PEBBLE_APP_UUID)
            Log.i(TAG, "stopAppOnTheWatch result: $launchResult")
            _pebbleConnected.value = false
            return true
        }
    }

    suspend fun sendBpm(bpm: Int) {
        if (!pebbleAppReady) {
            Log.d(TAG, "sendBpm: skipping, app not ready yet")
            return
        }
        try {
            val result = pebbleSender.sendDataToPebble(
                Constants.PEBBLE_APP_UUID,
                mapOf(Constants.PEBBLE_KEY_HEART_RATE to PebbleDictionaryItem.Int32(bpm))
            )
            if (result == null) {
                Log.w(TAG, "sendDataToPebble returned null -- no watches connected")
                _pebbleConnected.value = false
                return
            }
            val success = result.values.any { it == TransmissionResult.Success }
            Log.d(TAG, "sendBpm value success=$success")
            _pebbleConnected.value = success
        } catch (e: Exception) {
            Log.e(TAG, "sendBpm failed for bpm", e)
        }
    }
}
