package esp32.remotecontrol

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import esp32.remotecontrol.model.ConnectionState
import esp32.remotecontrol.model.Control
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * BLE protocol:
 *   Service:               ESP32_SERVICE_UUID
 *   Controls characteristic (notify): ESP32 pushes JSON array of controls on demand.
 *   Command  characteristic (write):  App writes {"id":"<id>","value":<v>} to change a control,
 *                                     or {"cmd":"list"} to request the control list.
 *
 * Flow:
 *   connect → MTU 512 → discoverServices → enableNotifications (CCCD write)
 *   → onDescriptorWrite success → send {"cmd":"list"} → ESP32 notifies control list
 */
@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    companion object {
        val ESP32_SERVICE_UUID: UUID = UUID.fromString("12345678-0000-1000-8000-00805f9b34fb")
        val CONTROLS_CHAR_UUID: UUID = UUID.fromString("12345678-0001-1000-8000-00805f9b34fb")
        val COMMAND_CHAR_UUID: UUID = UUID.fromString("12345678-0002-1000-8000-00805f9b34fb")
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val SCAN_TIMEOUT_MS = 10_000L
    }

    private val adapter: BluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var gatt: BluetoothGatt? = null
    private var commandChar: BluetoothGattCharacteristic? = null
    private var controlsChar: BluetoothGattCharacteristic? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _controls = MutableStateFlow<List<Control>>(emptyList())
    val controls: StateFlow<List<Control>> = _controls

    private val _scanResults = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val scanResults: StateFlow<List<BluetoothDevice>> = _scanResults

    private val scanHandler = Handler(Looper.getMainLooper())
    private val seenDevices = mutableSetOf<String>()

    // ── Scanning ─────────────────────────────────────────────────────────────────

    fun startScan() {
        seenDevices.clear()
        _scanResults.value = emptyList()
        _connectionState.value = ConnectionState.Scanning
        adapter.bluetoothLeScanner.startScan(scanCallback)
        scanHandler.postDelayed(::stopScan, SCAN_TIMEOUT_MS)
    }

    fun stopScan() {
        adapter.bluetoothLeScanner.stopScan(scanCallback)
        if (_connectionState.value is ConnectionState.Scanning) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (device.address !in seenDevices) {
                seenDevices.add(device.address)
                _scanResults.value = _scanResults.value + device
            }
        }
    }

    // ── Connection ────────────────────────────────────────────────────────────────

    fun connect(device: BluetoothDevice) {
        stopScan()
        _connectionState.value = ConnectionState.Connecting(device)
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        gatt?.disconnect()
    }

    // ── Commands ──────────────────────────────────────────────────────────────────

    fun sendCommand(id: String, value: Int) {
        val payload = JSONObject().apply {
            put("id", id)
            put("value", value)
        }.toString().toByteArray(Charsets.UTF_8)
        writeCommand(payload)
    }

    private fun requestControlList() {
        writeCommand("""{"cmd":"list"}""".toByteArray(Charsets.UTF_8))
    }

    @Suppress("DEPRECATION")
    private fun writeCommand(payload: ByteArray) {
        val char = commandChar ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt?.writeCharacteristic(char, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            char.value = payload
            gatt?.writeCharacteristic(char)
        }
    }

    // ── GATT callback ─────────────────────────────────────────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = ConnectionState.Connected(gatt.device)
                    gatt.requestMtu(512)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.Disconnected
                    _controls.value = emptyList()
                    commandChar = null
                    controlsChar = null
                    this@BleManager.gatt?.close()
                    this@BleManager.gatt = null
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(ESP32_SERVICE_UUID) ?: run {
                _connectionState.value = ConnectionState.Error("Service not found on device")
                gatt.disconnect()
                return
            }
            commandChar = service.getCharacteristic(COMMAND_CHAR_UUID)
            controlsChar = service.getCharacteristic(CONTROLS_CHAR_UUID) ?: return
            enableNotifications(gatt, controlsChar!!)
        }

        @Suppress("DEPRECATION")
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (descriptor.characteristic.uuid == CONTROLS_CHAR_UUID
                && status == BluetoothGatt.GATT_SUCCESS
            ) {
                // Subscribed — now ask ESP32 to send the control list
                requestControlList()
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (characteristic.uuid == CONTROLS_CHAR_UUID) {
                parseControls(String(characteristic.value, Charsets.UTF_8))
            }
        }

        // Android 13+ variant
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid == CONTROLS_CHAR_UUID) {
                parseControls(String(value, Charsets.UTF_8))
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun enableNotifications(gatt: BluetoothGatt, char: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(char, true)
        val descriptor = char.getDescriptor(CCCD_UUID) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun parseControls(json: String) {
        runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val typeStr = obj.optString("type", "switch").uppercase()
                Control(
                    id = obj.getString("id"),
                    label = obj.getString("label"),
                    type = runCatching { Control.Type.valueOf(typeStr) }.getOrDefault(Control.Type.SWITCH),
                    value = obj.optInt("value", 0),
                    min = obj.optInt("min", 0),
                    max = obj.optInt("max", 100),
                )
            }
        }.onSuccess { controls ->
            _controls.value = controls
        }
    }
}
