"""
ESP-NOW mesh overlay for esp32-device-controller.

Adapted from shariltumin/mesh-espnow-micropython (examples/mesh.py).
Uses asyncio instead of the original worker/MT scheduler.
Encryption is omitted; ESP-NOW's built-in PMK provides basic protection.

Protocol (same 5-message types as the reference):
  HEY          — broadcast discovery
  AUQ + MAC(6) — "I see you, here's my address" (simplified auth query)
  AUR + MAC(6) — acknowledgment (simplified auth response)
  MSG + header + payload — routed/forwarded payload
  BYE          — graceful leave

MSG wire format:
  b'MSG' (3) | target MAC (6) | mid: src_mac(6)+seq(5) (11) | payload (N)

Payload types (JSON):
  {"id": ..., "value": ...}  — control command (broadcast to all peers)
  [{...}, ...]               — control state list (pushed to BLE app if gateway)
"""

import asyncio
import json
import time
from network import WLAN, STA_IF

_MSG_HDR   = 20          # bytes before payload in a MSG frame
_MID_RING  = 100         # deduplication ring buffer size
_BAS       = b'\xff'*6  # broadcast MAC


class MeshNetwork:
    def __init__(self, controls, ble_server=None):
        self._controls  = controls
        self._ble       = ble_server
        self._mesh      = []                        # authenticated peer MACs
        self._mid_buf   = [b'\x00'*11]*_MID_RING    # seen message IDs
        self._mid_ptr   = 0
        self._ew        = None
        self._mac       = None

    def start(self):
        sta = WLAN(STA_IF)
        sta.active(True)
        sta.disconnect()
        self._mac = sta.config('mac')

        from espnow import ESPNow
        self._ew = ESPNow()
        self._ew.active(True)
        self._ew.add_peer(_BAS)  # noqa: pre-registered broadcast address

        asyncio.create_task(self._discovery_loop())
        asyncio.create_task(self._receive_loop())
        print('Mesh ready, MAC:', self._mac.hex())

    # ── Discovery ───────────────────────────────────────────────────────────────

    async def _discovery_loop(self):
        # Immediate HEY, retry after 30 s, then every 5 min
        self._ew.send(_BAS, b'HEY')
        await asyncio.sleep_ms(30_000)
        self._ew.send(_BAS, b'HEY')
        while True:
            await asyncio.sleep_ms(5 * 60 * 1000)
            self._ew.send(_BAS, b'HEY')

    # ── Receive loop ────────────────────────────────────────────────────────────

    async def _receive_loop(self):
        while True:
            if not self._ew.any():
                await asyncio.sleep_ms(50)
                continue
            peer, msg = self._ew.recv()
            if not msg or len(msg) < 3:
                await asyncio.sleep_ms(0)
                continue
            try:
                self._ew.add_peer(peer)
            except Exception:
                pass
            self._dispatch(peer, msg)
            await asyncio.sleep_ms(0)

    def _dispatch(self, peer, msg):
        typ = msg[:3]
        if typ == b'HEY':
            self._ew.send(peer, b'AUQ' + self._mac)
        elif typ == b'AUQ':
            if peer not in self._mesh:
                self._mesh.append(peer)
            self._ew.send(peer, b'AUR' + self._mac)
        elif typ == b'AUR':
            if peer not in self._mesh:
                self._mesh.append(peer)
                print('Mesh peer joined:', peer.hex(), '| total:', len(self._mesh))
        elif typ == b'MSG':
            self._handle_msg(peer, msg)
        elif typ == b'BYE':
            self._remove_peer(peer)
            print('Mesh peer left:', peer.hex(), '| total:', len(self._mesh))

    # ── Message handling ─────────────────────────────────────────────────────────

    def _handle_msg(self, sender, msg):
        if len(msg) < _MSG_HDR:
            return
        mid = msg[9:20]
        if mid in self._mid_buf:
            return
        self._mid_buf[self._mid_ptr] = mid
        self._mid_ptr = (self._mid_ptr + 1) % _MID_RING

        payload_bytes = msg[_MSG_HDR:]
        try:
            payload = json.loads(payload_bytes)
        except Exception:
            return

        if isinstance(payload, dict) and 'id' in payload:
            # Control command — apply locally and forward to remaining peers
            self._controls.apply(payload['id'], int(payload['value']))
            self._forward(sender, msg)
        elif isinstance(payload, list) and self._ble:
            # Remote control state — push to BLE app if this node is the gateway
            asyncio.create_task(self._ble.push_raw(payload_bytes))

    def _forward(self, sender, msg):
        dead = []
        for peer in self._mesh:
            if peer == sender:
                continue
            try:
                self._ew.send(peer, msg)
            except Exception:
                dead.append(peer)
        for peer in dead:
            self._remove_peer(peer)

    # ── Public API ───────────────────────────────────────────────────────────────

    def broadcast_command(self, ctrl_id, value):
        """Broadcast a control command to all mesh peers."""
        if not self._mesh:
            return
        payload = json.dumps({'id': ctrl_id, 'value': value}).encode()
        self._send_all(self._build_msg(payload))

    def broadcast_controls(self):
        """Broadcast this node's full control state to all mesh peers."""
        if not self._mesh:
            return
        payload = json.dumps(self._controls.get_list()).encode()
        self._send_all(self._build_msg(payload))

    # ── Helpers ──────────────────────────────────────────────────────────────────

    def _build_msg(self, payload):
        mid = self._mac + b'%05d' % (time.ticks_us() % 100000)
        return b'MSG' + _BAS + mid + payload

    def _send_all(self, msg):
        dead = []
        for peer in self._mesh:
            try:
                self._ew.send(peer, msg)
            except Exception:
                dead.append(peer)
        for peer in dead:
            self._remove_peer(peer)

    def _remove_peer(self, peer):
        try:
            self._mesh.remove(peer)
        except Exception:
            pass
        try:
            self._ew.del_peer(peer)
        except Exception:
            pass
