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

import io.github.meerkat.heartrate2pebble.data.ble.BleManager
import org.junit.Assert.assertEquals
import org.junit.Test

class HeartRateParserTest {

    @Test
    fun `8-bit format parses single byte BPM`() {
        // flags=0x00 (8-bit), bpm=72
        val data = byteArrayOf(0x00, 72)
        assertEquals(72, BleManager.parseHeartRate(data))
    }

    @Test
    fun `8-bit format handles max value 255`() {
        val data = byteArrayOf(0x00, 0xFF.toByte())
        assertEquals(255, BleManager.parseHeartRate(data))
    }

    @Test
    fun `8-bit format handles zero BPM`() {
        val data = byteArrayOf(0x00, 0)
        assertEquals(0, BleManager.parseHeartRate(data))
    }

    @Test
    fun `16-bit format parses two byte BPM`() {
        // flags=0x01 (16-bit), bpm=300 -> low=0x2C, high=0x01
        val data = byteArrayOf(0x01, 0x2C, 0x01)
        assertEquals(300, BleManager.parseHeartRate(data))
    }

    @Test
    fun `16-bit format handles low byte only`() {
        // flags=0x01, bpm=100 -> low=0x64, high=0x00
        val data = byteArrayOf(0x01, 0x64, 0x00)
        assertEquals(100, BleManager.parseHeartRate(data))
    }

    @Test
    fun `16-bit format handles high byte values`() {
        // flags=0x01, bpm=512 -> low=0x00, high=0x02
        val data = byteArrayOf(0x01, 0x00, 0x02)
        assertEquals(512, BleManager.parseHeartRate(data))
    }

    @Test
    fun `flags with other bits set still reads 8-bit when bit 0 is clear`() {
        // flags=0x10 (bit 0 clear, other bits set), bpm=80
        val data = byteArrayOf(0x10, 80)
        assertEquals(80, BleManager.parseHeartRate(data))
    }

    @Test
    fun `flags with other bits set still reads 16-bit when bit 0 is set`() {
        // flags=0x11 (bit 0 set), bpm=260 -> low=0x04, high=0x01
        val data = byteArrayOf(0x11, 0x04, 0x01)
        assertEquals(260, BleManager.parseHeartRate(data))
    }
}
