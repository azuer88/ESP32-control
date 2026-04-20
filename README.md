# ESP32 Bluetooth Remote Control

A two-part project for controlling and monitoring an ESP32 microcontroller over Bluetooth Low Energy (BLE):

- **`esp32-device-controller/`** — MicroPython firmware that runs on the ESP32, exposing GPIO pins as a BLE GATT server
- **`esp32-remote-control/`** — Android (Kotlin) companion app that connects over BLE and renders controls dynamically from the device's config

## How It Works

The ESP32 advertises a BLE service. The Android app connects, requests the control list, and renders switches and sliders. User interactions are sent as JSON commands; the ESP32 applies them to the appropriate GPIO pin. Digital inputs (buttons, sensors) are polled every 50 ms and pushed to the app automatically when they change.

## What You Can Attach

Controls are defined in `esp32-device-controller/device_config.json` — no firmware changes required.

### Controllable outputs

| Type | Config type | What to attach |
|---|---|---|
| On/off switch | `switch` | LED, relay module, transistor-switched load, buzzer, solenoid |
| PWM output | `slider` | LED brightness (via transistor), DC motor speed (via motor driver), servo position, fan speed |

### Readable inputs (sensors / buttons)

| Type | Config type | What to attach |
|---|---|---|
| Pushbutton / tactile switch | `digital_input` | Momentary button, reed switch, magnetic door sensor |
| PIR motion sensor | `digital_input` | HC-SR501 or similar (digital OUT pin) |
| Vibration / tilt sensor | `digital_input` | SW-420 or ball-tilt switch |
| Hall effect sensor | `digital_input` | A3144 or similar (digital output mode) |
| Optical endstop / limit switch | `digital_input` | Mechanical or optical limit switch |
| Float / water level sensor | `digital_input` | Float switch wired to a GPIO with pull-up |
| IR obstacle / line sensor | `digital_input` | TCRT5000-based module (digital OUT) |

> Sensors that output an **analog voltage** (e.g. temperature via NTC, light via LDR, soil moisture) are not yet supported — the current firmware only reads digital pins. Analog support would require adding an `analog_input` type using `machine.ADC`.

## BLE Protocol

| | UUID |
|---|---|
| Service | `12345678-0000-1000-8000-00805f9b34fb` |
| Controls characteristic (notify) | `12345678-0001-1000-8000-00805f9b34fb` |
| Command characteristic (write) | `12345678-0002-1000-8000-00805f9b34fb` |

**App → ESP32:** `{"id":"<id>","value":<int>}` or `{"cmd":"list"}`  
**ESP32 → App:** JSON array of control descriptors (id, label, type, value, min/max for sliders)

## Requirements

- ESP32 board running **MicroPython v1.26+**
- Android phone with BLE support
- [`mpremote`](https://docs.micropython.org/en/latest/reference/mpremote.html) for deploying firmware to the device

See each subproject's `CLAUDE.md` for development and deployment details.
