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
package io.github.meerkat.heartrate2pebble

import io.github.meerkat.heartrate2pebble.util.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ConstantsTest {

    @Test
    fun `HR service UUID is standard Bluetooth Heart Rate service`() {
        assertEquals(
            "0000180d-0000-1000-8000-00805f9b34fb",
            Constants.HR_SERVICE_UUID.toString()
        )
    }

    @Test
    fun `HR measurement UUID is standard Heart Rate Measurement characteristic`() {
        assertEquals(
            "00002a37-0000-1000-8000-00805f9b34fb",
            Constants.HR_MEASUREMENT_UUID.toString()
        )
    }

    @Test
    fun `battery service UUID is standard Battery Service`() {
        assertEquals(
            "0000180f-0000-1000-8000-00805f9b34fb",
            Constants.BATTERY_SERVICE_UUID.toString()
        )
    }

    @Test
    fun `Pebble app UUID is set`() {
        assertNotNull(Constants.PEBBLE_APP_UUID)
    }

    @Test
    fun `scan period is 15 minutes`() {
        assertEquals(15 * 60 * 1000L, Constants.SCAN_PERIOD_MS)
    }

    @Test
    fun `connect timeout is 15 minutes`() {
        assertEquals(15 * 60 * 1000L, Constants.CONNECT_TIMEOUT_MS)
    }

    @Test
    fun `HR inactivity timeout is 30 minutes`() {
        assertEquals(30 * 60 * 1000L, Constants.HR_INACTIVITY_TIMEOUT_MS)
    }

    @Test
    fun `HR display timeout is 3 seconds`() {
        assertEquals(3000L, Constants.HR_TIMEOUT_MS)
    }
}
