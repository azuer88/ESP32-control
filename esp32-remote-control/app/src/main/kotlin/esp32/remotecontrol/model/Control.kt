package esp32.remotecontrol.model

import android.bluetooth.BluetoothDevice

/**
 * A control exposed by the ESP32.
 *
 * ESP32 sends a JSON array on connect, e.g.:
 *   [
 *     {"id":"led",        "label":"Main LED",   "type":"switch", "value":0},
 *     {"id":"brightness", "label":"Brightness", "type":"slider", "value":50, "min":1, "max":100}
 *   ]
 */
data class Control(
    val id: String,
    val label: String,
    val type: Type,
    val value: Int,
    val min: Int = 0,
    val max: Int = 100,
) {
    enum class Type { SWITCH, SLIDER, DIGITAL_INPUT }
}

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Scanning : ConnectionState()
    data class Connecting(val device: BluetoothDevice) : ConnectionState()
    data class Connected(val device: BluetoothDevice) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
