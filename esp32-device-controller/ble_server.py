"""
BLE GATT server implementing the esp32-remote-control protocol.

Service:               12345678-0000-1000-8000-00805f9b34fb
Controls (notify):     12345678-0001-1000-8000-00805f9b34fb
  ESP32 → App: JSON array of control descriptors, pushed 500 ms after connection.
Command  (write):      12345678-0002-1000-8000-00805f9b34fb
  App → ESP32: {"id":"<id>","value":<int>}
"""

import asyncio
import bluetooth
import json

_IRQ_CENTRAL_CONNECT    = 1
_IRQ_CENTRAL_DISCONNECT = 2
_IRQ_GATTS_WRITE        = 3

_FLAG_WRITE         = 0x0008
_FLAG_WRITE_NO_RESP = 0x0004
_FLAG_NOTIFY        = 0x0010

_SVC_UUID      = bluetooth.UUID("12345678-0000-1000-8000-00805f9b34fb")
_CONTROLS_UUID = bluetooth.UUID("12345678-0001-1000-8000-00805f9b34fb")
_COMMAND_UUID  = bluetooth.UUID("12345678-0002-1000-8000-00805f9b34fb")

_GATT_SERVICE = (
    _SVC_UUID,
    (
        (_CONTROLS_UUID, _FLAG_NOTIFY),
        (_COMMAND_UUID,  _FLAG_WRITE | _FLAG_WRITE_NO_RESP),
    ),
)


class BleServer:
    def __init__(self, controls, device_name="ESP32-Remote"):
        self._controls = controls
        self._name = device_name
        self._ble = bluetooth.BLE()
        self._connections = set()
        self._controls_handle = None
        self._command_handle = None

    def start(self):
        self._ble.active(True)
        self._ble.irq(self._irq)
        ((self._controls_handle, self._command_handle),) = \
            self._ble.gatts_register_services([_GATT_SERVICE])
        # Command characteristic buffer: up to 256 bytes
        self._ble.gatts_set_buffer(self._command_handle, 256)
        self._advertise()
        print('BLE ready — advertising as "{}"'.format(self._name))

    # ── IRQ ─────────────────────────────────────────────────────────────────────

    def _irq(self, event, data):
        if event == _IRQ_CENTRAL_CONNECT:
            conn_handle, addr_type, addr = data
            self._connections.add(conn_handle)
            print("Connected:", bytes(addr).hex())

        elif event == _IRQ_CENTRAL_DISCONNECT:
            conn_handle, addr_type, addr = data
            self._connections.discard(conn_handle)
            print("Disconnected:", bytes(addr).hex())
            self._advertise()

        elif event == _IRQ_GATTS_WRITE:
            conn_handle, value_handle = data
            if value_handle == self._command_handle:
                raw = self._ble.gatts_read(self._command_handle)
                self._handle_command(raw, conn_handle)

    # ── BLE operations ───────────────────────────────────────────────────────────

    def _advertise(self):
        name_b = self._name.encode()
        adv = bytearray([
            0x02, 0x01, 0x06,               # Flags: general discoverable, no BR/EDR
            len(name_b) + 1, 0x09,          # Complete Local Name
        ]) + name_b
        self._ble.gap_advertise(100_000, adv_data=adv)

    async def push_controls_all(self):
        """Push the current control list to all connected clients."""
        if not self._connections:
            return
        payload = json.dumps(self._controls.get_list()).encode()
        for conn_handle in self._connections:
            self._ble.gatts_notify(conn_handle, self._controls_handle, payload)
        print("Pushed update ({} bytes)".format(len(payload)))

    async def _push_controls(self, conn_handle):
        await asyncio.sleep_ms(500)
        if conn_handle not in self._connections:
            return
        payload = json.dumps(self._controls.get_list()).encode()
        self._ble.gatts_notify(conn_handle, self._controls_handle, payload)
        print("Sent control list ({} bytes)".format(len(payload)))

    def _handle_command(self, raw, conn_handle):
        try:
            cmd = json.loads(raw)
            if cmd.get("cmd") == "list":
                asyncio.create_task(self._push_controls(conn_handle))
                return
            self._controls.apply(cmd["id"], int(cmd["value"]))
            print("Command: {} = {}".format(cmd["id"], cmd["value"]))
        except Exception as e:
            print("Command parse error:", e)
