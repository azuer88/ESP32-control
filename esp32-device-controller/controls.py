"""
Loads control definitions from device_config.json and manages the corresponding GPIO pins.

Switch controls use machine.Pin (digital out).
Slider controls use machine.PWM (0-100 mapped to 0-1023 duty cycle).
"""

import json
from machine import Pin, PWM


class Controls:
    def __init__(self, config_path="device_config.json"):
        with open(config_path) as f:
            self._defs = json.load(f)
        self._drivers = {}
        self._init_pins()

    def _init_pins(self):
        for ctrl in self._defs:
            pin_num = ctrl.get("pin")
            if pin_num is None:
                continue
            cid = ctrl["id"]
            active_low = ctrl.get("active_low", False)
            if ctrl["type"] == "digital_input":
                pin = Pin(pin_num, Pin.IN, Pin.PULL_UP)
                raw = pin.value()
                ctrl["value"] = (1 - raw) if active_low else raw
                self._drivers[cid] = ("digital_input", pin, active_low)
            elif ctrl["type"] == "switch":
                pin = Pin(pin_num, Pin.OUT)
                pin.value(self._switch_level(ctrl["value"], active_low))
                self._drivers[cid] = ("switch", pin, active_low)
            elif ctrl["type"] == "slider":
                pwm = PWM(Pin(pin_num), freq=1000)
                pwm.duty(self._slider_duty(ctrl["value"], ctrl.get("max", 100)))
                self._drivers[cid] = ("slider", pwm, ctrl.get("max", 100))

    # ── Public API ──────────────────────────────────────────────────────────────

    def poll_inputs(self):
        """Read all digital_input pins. Returns True if any value changed."""
        changed = False
        for ctrl in self._defs:
            if ctrl["type"] != "digital_input":
                continue
            driver = self._drivers.get(ctrl["id"])
            if driver is None:
                continue
            _, pin, active_low = driver
            raw = pin.value()
            value = (1 - raw) if active_low else raw
            if value != ctrl["value"]:
                ctrl["value"] = value
                changed = True
        return changed

    def apply(self, ctrl_id, value):
        """Apply a value received from the app to the appropriate GPIO driver."""
        ctrl = self._find(ctrl_id)
        if ctrl is None:
            print("Unknown control:", ctrl_id)
            return
        ctrl["value"] = value
        driver = self._drivers.get(ctrl_id)
        if driver is None:
            return
        kind = driver[0]
        if kind == "switch":
            _, pin, active_low = driver
            pin.value(self._switch_level(value, active_low))
        elif kind == "slider":
            _, pwm, max_val = driver
            pwm.duty(self._slider_duty(value, max_val))

    def get_list(self):
        """Return a list of control descriptors suitable for JSON serialisation."""
        result = []
        for c in self._defs:
            item = {
                "id": c["id"],
                "label": c["label"],
                "type": c["type"],
                "value": c["value"],
            }
            if c["type"] == "slider":
                item["min"] = c.get("min", 0)
                item["max"] = c.get("max", 100)
            result.append(item)
        return result

    # ── Helpers ─────────────────────────────────────────────────────────────────

    def _find(self, ctrl_id):
        for c in self._defs:
            if c["id"] == ctrl_id:
                return c
        return None

    @staticmethod
    def _switch_level(value, active_low):
        level = 1 if value else 0
        return (1 - level) if active_low else level

    @staticmethod
    def _slider_duty(value, max_val):
        return int(value * 1023 // max_val)
