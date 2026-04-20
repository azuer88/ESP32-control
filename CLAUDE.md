# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Subprojects

| Directory | Language | Purpose |
|---|---|---|
| `esp32-device-controller/` | MicroPython | Firmware for the ESP32 — BLE GATT server + GPIO control |
| `esp32-remote-control/` | Kotlin / Jetpack Compose | Android companion app |

Each subproject has its own `CLAUDE.md` with detailed architecture and commands. Read those before working in a subproject.

## System Architecture

The two subprojects communicate exclusively over BLE. The Android app is always the central (client); the ESP32 is always the peripheral (server).

```
Android app (central)                   ESP32 (peripheral)
──────────────────────                  ──────────────────
BleManager                              BleServer
  connect() → MTU → discoverServices      advertises as "ESP32-Remote"
  enableNotifications()                   
  write {"cmd":"list"}        ─────────▶  _handle_command()
                              ◀─────────  gatts_notify (JSON array)
  parseControls() → StateFlow             
  write {"id":"x","value":1}  ─────────▶  controls.apply() → GPIO
                              ◀─────────  gatts_notify (on input change)
```

Control definitions live entirely in `esp32-device-controller/device_config.json`. Adding or renaming a control there is reflected automatically in the Android UI — no code changes needed on either side, provided the type is already supported.

## BLE Protocol (shared contract)

| | UUID |
|---|---|
| Service | `12345678-0000-1000-8000-00805f9b34fb` |
| Controls characteristic (notify) | `12345678-0001-1000-8000-00805f9b34fb` |
| Command characteristic (write) | `12345678-0002-1000-8000-00805f9b34fb` |

App → ESP32: `{"id":"<id>","value":<int>}` or `{"cmd":"list"}`  
ESP32 → App: JSON array — `[{"id","label","type","value"[,"min","max"]}]`

Supported `type` values: `switch`, `slider`, `digital_input`.
