import asyncio
from controls import Controls
from ble_server import BleServer


async def poll_inputs(controls, server):
    while True:
        if controls.poll_inputs():
            await server.push_controls_all()
        await asyncio.sleep_ms(50)


async def main():
    controls = Controls()
    server = BleServer(controls, device_name="ESP32-Remote")
    server.start()
    asyncio.create_task(poll_inputs(controls, server))

    while True:
        await asyncio.sleep_ms(1000)


asyncio.run(main())
