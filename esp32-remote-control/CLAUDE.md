# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Deploy

```bash
# Build debug APK
./gradlew :app:assembleDebug

# Build + install directly to connected device
./gradlew :app:installDebug

# Install a previously built APK
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk

# Force recompile (Gradle sometimes considers tasks up-to-date incorrectly)
./gradlew :app:assembleDebug --rerun-tasks
```

Android SDK is at `~/Android/Sdk`. `local.properties` points to it and is gitignored.
The phone (Huawei, serial `SDEDU20108004368`) connects via USB only — WiFi ADB is not enabled.

## Architecture

Single-activity app with Jetpack Compose. The data flow is unidirectional:

```
BleManager (StateFlow) → ControlViewModel → MainScreen (Compose UI)
```

**`BleManager`** owns all BLE state. It exposes `connectionState`, `controls`, and `scanResults` as `StateFlow`s. The connection sequence is:
1. `connect()` → `requestMtu(512)` → `discoverServices()`
2. `enableNotifications()` writes CCCD descriptor
3. `onDescriptorWrite` success → sends `{"cmd":"list"}` to command characteristic
4. ESP32 responds with a notification containing the JSON control list
5. `parseControls()` updates `_controls` StateFlow

**`ControlViewModel`** bridges BLE and UI. It holds an optimistic local copy of controls (`_controls`) that updates immediately on user interaction, then gets overwritten when the ESP32 pushes a fresh list. `onControlChanged()` silently ignores `DIGITAL_INPUT` controls.

**`MainScreen`** collects both `vm.ble.controls` (raw BLE) and `vm.controls` (local copy) — a `LaunchedEffect` syncs the BLE list into the local copy via `vm.syncControls()`.

## BLE Protocol

| | UUID |
|---|---|
| Service | `12345678-0000-1000-8000-00805f9b34fb` |
| Controls characteristic (notify) | `12345678-0001-1000-8000-00805f9b34fb` |
| Command characteristic (write) | `12345678-0002-1000-8000-00805f9b34fb` |

ESP32 → App (notification): JSON array of control descriptors.
App → ESP32 (write): `{"id":"<id>","value":<int>}` or `{"cmd":"list"}` to request a fresh push.

## Control Types

| `type` in JSON | `Control.Type` | Widget |
|---|---|---|
| `"switch"` | `SWITCH` | Interactive `Switch` |
| `"slider"` | `SLIDER` | Interactive `Slider` with `min`/`max` |
| `"digital_input"` | `DIGITAL_INPUT` | Disabled (read-only) `Switch` |

Adding a new control type requires: adding to `Control.Type`, handling in `ControlCard`, and adding a case in `ControlViewModel.onControlChanged` if it needs special treatment.

## Companion firmware

The ESP32 firmware lives in the sibling repo `../esp32-device-controller`.
