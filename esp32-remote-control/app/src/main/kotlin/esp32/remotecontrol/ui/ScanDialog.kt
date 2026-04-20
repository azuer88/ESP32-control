package esp32.remotecontrol.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@SuppressLint("MissingPermission")
@Composable
fun ScanDialog(
    devices: List<BluetoothDevice>,
    isScanning: Boolean,
    onSelect: (BluetoothDevice) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isScanning) "Scanning…" else "Select Device") },
        text = {
            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isScanning) {
                        CircularProgressIndicator()
                    } else {
                        Text("No devices found.")
                    }
                }
            } else {
                LazyColumn {
                    items(devices) { device ->
                        ListItem(
                            headlineContent = {
                                Text(device.name ?: "Unknown")
                            },
                            supportingContent = { Text(device.address) },
                            modifier = Modifier.clickable { onSelect(device) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
