package esp32.remotecontrol

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import esp32.remotecontrol.model.Control
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class ControlViewModel(application: Application) : AndroidViewModel(application) {

    val ble = BleManager(application)

    val connectionState = ble.connectionState
    val scanResults = ble.scanResults

    /**
     * Local control values — updated optimistically on user interaction and overwritten
     * whenever the ESP32 pushes a fresh control list.
     */
    private val _controls = MutableStateFlow<List<Control>>(emptyList())
    val controls: StateFlow<List<Control>> = _controls

    init {
        // Mirror BLE control list into our local copy
        // (collect happens in the composable via LaunchedEffect so we just expose the flow)
        _controls // placeholder; composable collects ble.controls directly
    }

    fun startScan() = ble.startScan()
    fun stopScan() = ble.stopScan()
    fun connect(device: BluetoothDevice) = ble.connect(device)
    fun disconnect() = ble.disconnect()

    fun onControlChanged(id: String, value: Int) {
        val ctrl = _controls.value.find { it.id == id } ?: return
        if (ctrl.type == Control.Type.DIGITAL_INPUT) return
        _controls.update { list ->
            list.map { if (it.id == id) it.copy(value = value) else it }
        }
        ble.sendCommand(id, value)
    }

    fun syncControls(incoming: List<Control>) {
        _controls.value = incoming
    }
}
