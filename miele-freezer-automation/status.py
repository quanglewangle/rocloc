"""
Quick status snapshot of all Miele freezers on your account.

Usage:
    python status.py
    python status.py --json
"""

import argparse
import json
from datetime import datetime

from miele_client import MieleClient, DEVICE_TYPE_FREEZER, DEVICE_TYPE_FRIDGE_FREEZER


def celsius(raw: int) -> str:
    return "N/A" if raw == -32768 else f"{raw / 100:.1f}C"


def print_freezer(freezer_id: str, ident: dict, state: dict):
    name = ident.get("deviceName") or freezer_id
    model = ident.get("deviceIdentLabel", {}).get("techType", "")
    status = state.get("status", {}).get("value_localized", "Unknown")
    door = "OPEN" if state.get("signalDoor") else "closed"
    info_fault = "YES" if state.get("signalInfo") else "no"

    print(f"\n{'─' * 50}")
    print(f"  {name}  [{model}]  (id: {freezer_id})")
    print(f"  Status  : {status}")
    print(f"  Door    : {door}")
    print(f"  Fault   : {info_fault}")

    temps = state.get("temperatures", [])
    for zone in temps:
        raw = zone.get("value_raw", -32768)
        target = zone.get("targetValue", -32768)
        if raw == -32768:
            continue
        zone_id = zone.get("zone", 1)
        print(f"  Zone {zone_id}   : {celsius(raw)}  (target {celsius(target)})")


def main():
    parser = argparse.ArgumentParser(description="Snapshot status of all Miele freezers")
    parser.add_argument("--json", action="store_true", help="Output raw JSON instead of formatted text")
    args = parser.parse_args()

    client = MieleClient.from_env()
    devices = client.get_all_devices()

    freezers = {
        did: info
        for did, info in devices.items()
        if info.get("ident", {}).get("type", {}).get("value_raw")
        in (DEVICE_TYPE_FREEZER, DEVICE_TYPE_FRIDGE_FREEZER)
    }

    if not freezers:
        print("No Miele freezers found.")
        return

    if args.json:
        output = {}
        for did in freezers:
            output[did] = {"ident": freezers[did].get("ident"), "state": client.get_device_state(did)}
        print(json.dumps(output, indent=2))
        return

    print(f"Miele Freezer Status -- {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"Found {len(freezers)} freezer(s)")

    for did, info in freezers.items():
        state = client.get_device_state(did)
        print_freezer(did, info.get("ident", {}), state)

    print(f"\n{'─' * 50}")


if __name__ == "__main__":
    main()
