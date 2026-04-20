import asyncio
from controls import Controls
from ble_server import BleServer
from mesh_network import MeshNetwork


async def poll_inputs(controls, server, mesh):
    while True:
        if controls.poll_inputs():
            await server.push_controls_all()
            mesh.broadcast_controls()
        await asyncio.sleep_ms(50)


async def main():
    controls = Controls()

    server = BleServer(controls, device_name="ESP32-Remote")
    server.start()

    mesh = MeshNetwork(controls, ble_server=server)
    mesh.start()
    server.set_mesh(mesh)

    asyncio.create_task(poll_inputs(controls, server, mesh))

    while True:
        await asyncio.sleep_ms(1000)


asyncio.run(main())
