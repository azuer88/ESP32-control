# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Deploy to Device

```bash
MPREMOTE=/home/lou/.virtualenvs/micropython/bin/mpremote

# Copy a single file
$MPREMOTE connect /dev/ttyUSB0 fs cp <file> :<file>

# Deploy all app files and restart
for f in main.py ble_server.py controls.py device_config.json; do
  $MPREMOTE connect /dev/ttyUSB0 fs cp $f :$f
done
$MPREMOTE connect /dev/ttyUSB0 reset

# Run a one-off expression on the device
$MPREMOTE connect /dev/ttyUSB0 exec "import ujson; print(ujson.dumps({'ok':1}))"

# List files on device
$MPREMOTE connect /dev/ttyUSB0 fs ls :
```

If mpremote fails with "device in use", a picocom session is likely open — close it first. Don't `pkill mpremote` without checking with the user.

The device firmware is **MicroPython v1.26.1**. Pyright warnings about `bluetooth`, `machine`, and `asyncio.sleep_ms` are expected — these are MicroPython-only APIs.

## Architecture

```
main.py  ──creates──▶  Controls  ──passed to──▶  BleServer
            └─creates──▶  poll_inputs task (50ms loop)
                              └─on change──▶  BleServer.push_controls_all()
```

**`controls.py`** loads `device_config.json` at boot and initialises GPIO drivers. Three driver types:
- `switch` → `machine.Pin` (output), supports `active_low`
- `slider` → `machine.PWM`, duty mapped `0–max` → `0–1023`
- `digital_input` → `machine.Pin(IN, PULL_UP)`, polled every 50ms via `poll_inputs()`

`apply(id, value)` is called by `BleServer` on incoming commands and updates both the in-memory value and the GPIO driver. `get_list()` serialises current state for BLE notification.

**`ble_server.py`** is a GATT server with two characteristics. The IRQ handler runs on the MicroPython main thread and can safely call `asyncio.create_task()`. Connection flow:
1. App connects → `_IRQ_CENTRAL_CONNECT` → re-advertise is deferred until disconnect
2. App writes CCCD (handled transparently by MicroPython) → app sends `{"cmd":"list"}`
3. `_IRQ_GATTS_WRITE` → `_handle_command` → `_push_controls(conn_handle)` (async, no delay needed)
4. For input changes: `push_controls_all()` notifies all connected handles

## device_config.json

Controls are defined here — no code changes needed for new controls:

```json
[
  {"id":"led",   "label":"Built-in LED", "type":"switch",        "value":0, "pin":2, "active_low":true},
  {"id":"pwm",   "label":"PWM Output",   "type":"slider",        "value":0, "pin":4, "min":0, "max":100},
  {"id":"boot",  "label":"Boot Button",  "type":"digital_input", "value":0, "pin":0, "active_low":true}
]
```

Valid types: `switch`, `slider`, `digital_input`. Sliders require `min`/`max`. `active_low` is optional (default false).

## BLE Protocol

| | UUID |
|---|---|
| Service | `12345678-0000-1000-8000-00805f9b34fb` |
| Controls (notify) | `12345678-0001-1000-8000-00805f9b34fb` |
| Command (write) | `12345678-0002-1000-8000-00805f9b34fb` |

App → ESP32: `{"id":"<id>","value":<int>}` or `{"cmd":"list"}`
ESP32 → App: JSON array — same schema as `device_config.json` but without `pin`/`active_low`.

## Existing device files (preserved in `backup/`)

`boot.py` connects to WiFi and starts WebREPL on every boot using credentials from `config.json`. Do not overwrite `config.json` — it holds WiFi credentials. The companion Android app lives in `../esp32-remote-control`.
