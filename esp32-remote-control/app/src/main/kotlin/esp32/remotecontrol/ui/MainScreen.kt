package esp32.remotecontrol.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import esp32.remotecontrol.ControlViewModel
import esp32.remotecontrol.model.ConnectionState

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: ControlViewModel = viewModel()) {
    val connectionState by vm.connectionState.collectAsState()
    val scanResults by vm.scanResults.collectAsState()
    val bleControls by vm.ble.controls.collectAsState()
    val controls by vm.controls.collectAsState()

    // Keep local control list in sync with BLE-pushed list
    LaunchedEffect(bleControls) {
        vm.syncControls(bleControls)
    }

    var showScanDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    when (val s = connectionState) {
                        is ConnectionState.Connected ->
                            Text(s.device.name ?: s.device.address)
                        is ConnectionState.Connecting ->
                            Text("Connecting…")
                        is ConnectionState.Scanning ->
                            Text("Scanning…")
                        is ConnectionState.Error ->
                            Text("Error: ${s.message}")
                        is ConnectionState.Disconnected ->
                            Text("ESP32 Remote Control")
                    }
                },
                actions = {
                    when (connectionState) {
                        is ConnectionState.Connected -> {
                            TextButton(onClick = { vm.disconnect() }) {
                                Text("Disconnect")
                            }
                        }
                        is ConnectionState.Scanning -> {
                            TextButton(onClick = {
                                vm.stopScan()
                                showScanDialog = false
                            }) {
                                Text("Stop")
                            }
                        }
                        is ConnectionState.Disconnected, is ConnectionState.Error -> {
                            Button(
                                onClick = {
                                    vm.startScan()
                                    showScanDialog = true
                                },
                                modifier = Modifier.padding(end = 8.dp),
                            ) {
                                Text("Connect")
                            }
                        }
                        else -> {}
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val s = connectionState) {
                is ConnectionState.Connecting -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ConnectionState.Connected -> {
                    if (controls.isEmpty()) {
                        Text(
                            "Waiting for controls…",
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            items(controls, key = { it.id }) { control ->
                                ControlCard(control) { newValue ->
                                    vm.onControlChanged(control.id, newValue)
                                }
                            }
                        }
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "No device connected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (s is ConnectionState.Error) {
                            Text(
                                s.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showScanDialog) {
        ScanDialog(
            devices = scanResults,
            isScanning = connectionState is ConnectionState.Scanning,
            onSelect = { device ->
                showScanDialog = false
                vm.connect(device)
            },
            onDismiss = {
                vm.stopScan()
                showScanDialog = false
            },
        )
    }
}
