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
package io.github.meerkat.heartrate2pebble.util

import java.util.UUID

object Constants {
    // BLE UUIDs
    val HR_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    val HR_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    val CLIENT_CHAR_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    val BATTERY_LEVEL_UUID: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    // Pebble Constants
    val PEBBLE_APP_UUID: UUID = UUID.fromString("170fe807-7a48-4756-b5a1-671178e00f03")
    val PEBBLE_KEY_HEART_RATE = 1u

    // Scan Settings
    const val SCAN_PERIOD_MS = 15 * 60 * 1000L  // 15 minutes
    const val HR_TIMEOUT_MS = 3000L

    // Session timeouts
    const val CONNECT_TIMEOUT_MS = 15 * 60 * 1000L   // 15 minutes
    const val HR_INACTIVITY_TIMEOUT_MS = 30 * 60 * 1000L  // 30 minutes
}