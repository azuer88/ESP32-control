# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Subprojects

| Directory | Language | Purpose |
|---|---|---|
| `esp32-device-controller/` | MicroPython | Firmware for the ESP32 — BLE GATT server + ESP-NOW mesh + GPIO control |
| `esp32-remote-control/` | Kotlin / Jetpack Compose | Android companion app |

Each subproject has its own `CLAUDE.md` with detailed architecture and commands. Read those before working in a subproject.

## System Architecture

The Android app connects over BLE to one ESP32 node (the gateway). That node participates in an ESP-NOW mesh with any number of other ESP32 nodes — all running the same firmware.

```
Android app (central)         ESP32 gateway node              ESP32 leaf nodes
──────────────────────        ──────────────────              ────────────────
BleManager                    BleServer + MeshNetwork         MeshNetwork
  connect() → discoverServices  advertises as "ESP32-Remote"    ESP-NOW peers
  write {"cmd":"list"}  ──────▶  _handle_command()
                        ◀──────  gatts_notify (JSON array)
  write {"id":"x","v":1} ──────▶  controls.apply() → GPIO
                                   mesh broadcast ──────────▶  controls.apply()
  (state update)        ◀──────  push_raw() ◀── mesh MSG ────  broadcast_controls()
```

Control definitions live in each node's `device_config.json`. Control IDs must be unique across all nodes — the app sees a merged view of all controls from all mesh nodes.

## BLE Protocol (shared contract)

| | UUID |
|---|---|
| Service | `12345678-0000-1000-8000-00805f9b34fb` |
| Controls characteristic (notify) | `12345678-0001-1000-8000-00805f9b34fb` |
| Command characteristic (write) | `12345678-0002-1000-8000-00805f9b34fb` |

App → ESP32: `{"id":"<id>","value":<int>}` or `{"cmd":"list"}`  
ESP32 → App: JSON array — `[{"id","label","type","value"[,"min","max"]}]`

Supported `type` values: `switch`, `slider`, `digital_input`.
