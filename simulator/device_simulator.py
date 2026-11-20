"""
Simulated Networking Devices (AP / Switch / Gateway) for Wi-Fi NMS Demo.
- Registers 3 virtual devices via REST API (idempotent).
- Connects to MQTT broker, publishes periodic heartbeats & telemetry.
- Listens for backend commands (APPLY_CONFIG, OTA_UPGRADE) and reports execution results.
"""

import json
import os
import random
import threading
import time
import requests
import paho.mqtt.client as mqtt

API_BASE_URL = os.getenv("API_BASE_URL", "http://localhost:8080")
MQTT_HOST = os.getenv("MQTT_HOST", "localhost")
MQTT_PORT = int(os.getenv("MQTT_PORT", "1883"))

VIRTUAL_DEVICES = [
    {
        "macAddress": "AA:BB:CC:01:00:01",
        "name": "AP-Taoyuan3-Lobby-01",
        "type": "AP",
        "model": "DWL-WiFi7-Pro",
        "siteName": "Taoyuan-Plant3",
        "ipAddress": "10.10.20.11",
        "firmwareVersion": "1.2.0",
    },
    {
        "macAddress": "AA:BB:CC:02:00:01",
        "name": "SW-Taoyuan3-Core-01",
        "type": "SWITCH",
        "model": "DGS-48P-PoE",
        "siteName": "Taoyuan-Plant3",
        "ipAddress": "10.10.10.2",
        "firmwareVersion": "2.0.4",
    },
    {
        "macAddress": "AA:BB:CC:03:00:01",
        "name": "GW-Taoyuan3-Edge-01",
        "type": "GATEWAY",
        "model": "DGW-Edge-500",
        "siteName": "Taoyuan-Plant3",
        "ipAddress": "10.10.10.1",
        "firmwareVersion": "3.1.1",
    },
]


def wait_for_backend():
    print(f"[Simulator] Waiting for NMS backend at {API_BASE_URL}/actuator/health ...")
    while True:
        try:
            resp = requests.get(f"{API_BASE_URL}/actuator/health", timeout=3)
            if resp.status_code == 200:
                print("[Simulator] Backend is UP!")
                return
        except Exception:
            pass
        time.sleep(3)


def register_devices():
    for dev in VIRTUAL_DEVICES:
        try:
            resp = requests.post(f"{API_BASE_URL}/api/v1/devices", json=dev, timeout=5)
            if resp.status_code == 201:
                print(f"[Simulator] Registered {dev['type']} {dev['name']} ({dev['macAddress']})")
            elif resp.status_code == 400:
                print(f"[Simulator] Already registered: {dev['macAddress']}")
            else:
                print(f"[Simulator] Register response {resp.status_code}: {resp.text}")
        except Exception as exc:
            print(f"[Simulator] Failed to register {dev['macAddress']}: {exc}")


def build_telemetry(dev):
    cpu = round(random.uniform(15.0, 65.0), 1)
    # Occasionally simulate high CPU spike to demonstrate backend anomaly alert
    if random.random() < 0.08:
        cpu = round(random.uniform(86.0, 96.0), 1)

    base = {
        "deviceType": dev["type"],
        "cpuUsage": cpu,
        "memoryUsage": round(random.uniform(30.0, 72.0), 1),
        "temperatureC": round(random.uniform(38.0, 58.0), 1),
    }

    if dev["type"] == "AP":
        base["metrics"] = {
            "connectedClients": random.randint(12, 85),
            "channel2g": 6,
            "channel5g": 149,
            "avgRssiDbm": random.randint(-68, -45),
        }
    elif dev["type"] == "SWITCH":
        base["metrics"] = {
            "activePorts": random.randint(18, 44),
            "poePowerWatts": round(random.uniform(120.0, 280.0), 1),
            "vlanCount": 6,
        }
    else:
        base["metrics"] = {
            "wanLatencyMs": round(random.uniform(4.2, 18.5), 2),
            "activeSessions": random.randint(400, 1800),
            "vpnTunnelsUp": 3,
        }
    return base


class SimulatedDevice:
    def __init__(self, dev_info):
        self.info = dev_info
        self.mac = dev_info["macAddress"]
        self.client = mqtt.Client(
            callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
            client_id=f"sim-{self.mac.replace(':', '')}",
        )
        self.client.on_connect = self.on_connect
        self.client.on_message = self.on_message

    def on_connect(self, client, userdata, flags, reason_code, properties):
        cmd_topic = f"nms/devices/{self.mac}/command"
        client.subscribe(cmd_topic, qos=1)
        print(f"[{self.info['name']}] Connected to MQTT & subscribed to {cmd_topic}")

    def on_message(self, client, userdata, msg):
        try:
            envelope = json.loads(msg.payload.decode("utf-8"))
            cmd_type = envelope.get("commandType")
            payload = envelope.get("payload", {})
            print(f"[{self.info['name']}] Received command: {cmd_type} -> {payload}")

            if cmd_type == "APPLY_CONFIG":
                event_payload = {
                    "eventType": "CONFIG_APPLIED",
                    "severity": "INFO",
                    "message": f"Configuration applied successfully on {self.info['name']}",
                    "details": payload,
                }
                client.publish(f"nms/devices/{self.mac}/event", json.dumps(event_payload), qos=1)

            elif cmd_type == "OTA_UPGRADE":
                threading.Thread(target=self.simulate_ota, args=(payload,), daemon=True).start()
        except Exception as exc:
            print(f"[{self.info['name']}] Error handling command: {exc}")

    def simulate_ota(self, payload):
        task_id = payload.get("taskId")
        target_ver = payload.get("targetVersion")
        print(f"[{self.info['name']}] Starting OTA {task_id} -> downloading {target_ver} ...")
        time.sleep(5)
        self.info["firmwareVersion"] = target_ver
        result_event = {
            "eventType": "OTA_RESULT",
            "severity": "INFO",
            "message": f"OTA upgrade to {target_ver} succeeded",
            "details": {
                "taskId": task_id,
                "status": "SUCCESS",
                "firmwareVersion": target_ver,
                "reason": "Verified SHA-256 checksum and rebooted cleanly",
            },
        }
        self.client.publish(f"nms/devices/{self.mac}/event", json.dumps(result_event), qos=1)
        print(f"[{self.info['name']}] Completed OTA {task_id}, reported SUCCESS to backend.")

    def start(self):
        self.client.connect(MQTT_HOST, MQTT_PORT, keepalive=60)
        self.client.loop_start()

        tick = 0
        while True:
            # Heartbeat every 10s
            hb = {"ipAddress": self.info["ipAddress"], "firmwareVersion": self.info["firmwareVersion"]}
            self.client.publish(f"nms/devices/{self.mac}/heartbeat", json.dumps(hb), qos=0)

            # Telemetry every 20s
            if tick % 2 == 0:
                telemetry = build_telemetry(self.info)
                self.client.publish(f"nms/devices/{self.mac}/telemetry", json.dumps(telemetry), qos=1)

            tick += 1
            time.sleep(10)


def main():
    wait_for_backend()
    register_devices()

    threads = []
    for dev in VIRTUAL_DEVICES:
        sim = SimulatedDevice(dev)
        t = threading.Thread(target=sim.start, daemon=True)
        t.start()
        threads.append(t)

    print("[Simulator] All 3 virtual devices (AP, SWITCH, GATEWAY) are running. Press Ctrl+C to stop.")
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\n[Simulator] Shutting down simulated devices.")


if __name__ == "__main__":
    main()
