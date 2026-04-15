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
package io.github.meerkat.heartrate2pebble.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.meerkat.heartrate2pebble.data.ble.BleDeviceItem

@Composable
fun DevicePickerDialog(
    scanStatus: String,
    devices: List<BleDeviceItem>,
    onDeviceSelected: (BleDeviceItem) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Heart Rate Monitor") },
        text = {
            Column {
                Text(scanStatus, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(min = 100.dp, max = 300.dp)) {
                    itemsIndexed(devices) { index, item ->
                        Text(
                            text = "${item.batteryLevel}  ${item.name}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDeviceSelected(item) }
                                .padding(12.dp),
                            fontSize = 16.sp
                        )
                        if (index < devices.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
