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

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.meerkat.heartrate2pebble.ui.MainViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartRateScreen(
    viewModel: MainViewModel,
    onPermissionRequest: () -> Unit
) {
    val currentHeartRate by viewModel.heartRate.collectAsState()
    val currentPebbleConnected by viewModel.pebbleConnected.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collectLatest { message ->
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text("Heartrate to Pebble", fontWeight = FontWeight.Bold)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Text("\u22EE", fontSize = 20.sp)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("About") },
                            onClick = {
                                showMenu = false
                                showAbout = true
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
        ) {
            // Top row: Pair/Unpair + device name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { onPermissionRequest() },
                    enabled = viewModel.termsAccepted
                ) {
                    Text("Pair")
                }
                if (viewModel.isPaired) {
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.unpair() },
                        enabled = viewModel.termsAccepted
                    ) {
                        Text("Unpair")
                    }
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    text = viewModel.deviceName,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Session row
            val isSessionActive by viewModel.isSessionActive.collectAsState()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { viewModel.startSession() },
                    enabled = viewModel.termsAccepted && !isSessionActive && viewModel.isPaired
                ) {
                    Text("Start Session")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { viewModel.stopSession() },
                    enabled = isSessionActive
                ) {
                    Text("Stop Session")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Pebble row
            Row(verticalAlignment = Alignment.CenterVertically) {
                val pebbleEnabled = viewModel.isPaired && isSessionActive
                Checkbox(
                    checked = viewModel.isPebbleEnabled && pebbleEnabled,
                    onCheckedChange = { viewModel.togglePebble(it) },
                    enabled = viewModel.termsAccepted && pebbleEnabled
                )
                val pebbleRowEnabled = viewModel.termsAccepted && pebbleEnabled
                Text(
                    "Send to Pebble",
                    color = if (pebbleRowEnabled) Color.Unspecified else Color(0xFF888888)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (currentPebbleConnected) "Connected" else "Disconnected",
                    fontSize = 12.sp,
                    color = if (currentPebbleConnected) Color(0xFF4CAF50) else Color(0xFF888888)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Heart rate display
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "BPM",
                    fontSize = 14.sp,
                    color = Color.LightGray
                )
                Text(
                    text = currentHeartRate,
                    fontSize = 110.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primaryContainer
                )
            }

            // Warning messages
            val warnings = buildList {
                if (viewModel.notificationsDenied) {
                    add("Please enable notifications to ensure the data communication works properly when the app is in the background.")
                }
                if (viewModel.batteryOptimized) {
                    add("Battery optimization is enabled for this app. " +
                            "Please set to 'Unrestricted' usage in Settings > Apps > Heartrate2Pebble > Battery " +
                            "> Manage battery usage to ensure the data communication works properly when the app is in the background. " +
                            "Please also ensure the Pebble app is in unrestricted mode.")
                }
            }
            if (warnings.isNotEmpty()) {
                Text(
                    text = warnings.joinToString("\n\n"),
                    fontSize = 10.sp,
                    color = Color(0xFF888888),
                    textAlign = TextAlign.Center,
                    lineHeight = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }
        }
    }

    // About dialog
    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }

    // Terms dialog (first install only)
    if (viewModel.showTermsDialog) {
        TermsDialog(
            onAccept = { viewModel.acceptTerms() },
            onDecline = { viewModel.declineTerms() }
        )
    }

    // Device picker dialog
    if (viewModel.showPicker) {
        val currentScanStatus by viewModel.scanStatus.collectAsState()
        DevicePickerDialog(
            scanStatus = currentScanStatus,
            devices = viewModel.discoveredDevices.toList(),
            onDeviceSelected = { viewModel.onDeviceSelected(it) },
            onDismiss = { viewModel.closePicker() }
        )
    }
}
