"""
Schedule SuperFreeze automatically before grocery deliveries or big freezing jobs.

Usage:
    # Enable SuperFreeze 2 hours before a delivery, then disable after food has frozen
    python super_freeze.py --device <id> --lead-hours 2 --run-hours 4

    # Enable right now and disable after 3 hours
    python super_freeze.py --device <id> --now --run-hours 3

    # Disable immediately
    python super_freeze.py --device <id> --disable
"""

import argparse
from datetime import datetime, timedelta

from miele_client import MieleClient


def wait_until(target: datetime, device_name: str, action: str):
    import time
    remaining = (target - datetime.now()).total_seconds()
    if remaining > 0:
        print(f"Waiting {remaining / 3600:.1f}h until {target.strftime('%H:%M')} to {action} for {device_name}...")
        time.sleep(remaining)


def run(client: MieleClient, device_id: str, lead_hours: float, run_hours: float, start_now: bool, disable: bool):
    freezers = {f["id"]: f for f in client.get_freezers()}
    if device_id not in freezers:
        raise SystemExit(f"Device '{device_id}' not found or not a freezer. Available: {list(freezers)}")

    name = freezers[device_id].get("ident", {}).get("deviceName") or device_id

    if disable:
        print(f"Disabling SuperFreeze on {name}...")
        client.set_super_freeze(device_id, enabled=False)
        print("Done.")
        return

    if not start_now:
        enable_at = datetime.now() + timedelta(hours=lead_hours)
        wait_until(enable_at, name, "enable SuperFreeze")

    print(f"[{datetime.now().strftime('%H:%M')}] Enabling SuperFreeze on {name}...")
    client.set_super_freeze(device_id, enabled=True)

    disable_at = datetime.now() + timedelta(hours=run_hours)
    print(f"SuperFreeze will run until {disable_at.strftime('%H:%M')} ({run_hours}h).")
    wait_until(disable_at, name, "disable SuperFreeze")

    print(f"[{datetime.now().strftime('%H:%M')}] Disabling SuperFreeze on {name}...")
    client.set_super_freeze(device_id, enabled=False)
    print("SuperFreeze cycle complete.")


def main():
    parser = argparse.ArgumentParser(description="Schedule SuperFreeze on a Miele freezer")
    parser.add_argument("--device", required=True, help="Miele device ID")
    parser.add_argument("--lead-hours", type=float, default=2.0,
                        help="Hours before event to pre-cool (default 2)")
    parser.add_argument("--run-hours", type=float, default=4.0,
                        help="Total hours to run SuperFreeze (default 4)")
    parser.add_argument("--now", action="store_true", help="Enable immediately without waiting")
    parser.add_argument("--disable", action="store_true", help="Disable SuperFreeze immediately and exit")
    args = parser.parse_args()

    client = MieleClient.from_env()
    run(client, args.device, args.lead_hours, args.run_hours, args.now, args.disable)


if __name__ == "__main__":
    main()
