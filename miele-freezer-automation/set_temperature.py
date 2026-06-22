"""
Set target temperature on a Miele freezer zone.

Usage:
    python set_temperature.py --device <id> --temp -18
    python set_temperature.py --device <id> --temp -18 --zone 2
"""

import argparse
from miele_client import MieleClient

FREEZER_SAFE_MIN = -30
FREEZER_SAFE_MAX = -10


def main():
    parser = argparse.ArgumentParser(description="Set freezer target temperature")
    parser.add_argument("--device", required=True, help="Miele device ID")
    parser.add_argument("--temp", type=int, required=True, help="Target temperature in C (e.g. -18)")
    parser.add_argument("--zone", type=int, default=1, help="Temperature zone (default 1)")
    args = parser.parse_args()

    if not (FREEZER_SAFE_MIN <= args.temp <= FREEZER_SAFE_MAX):
        raise SystemExit(
            f"Temperature {args.temp}C is outside the safe range "
            f"({FREEZER_SAFE_MIN}C to {FREEZER_SAFE_MAX}C). Aborting."
        )

    client = MieleClient.from_env()
    freezers = {f["id"]: f for f in client.get_freezers()}

    if args.device not in freezers:
        raise SystemExit(f"Device '{args.device}' not found or is not a freezer.")

    name = freezers[args.device].get("ident", {}).get("deviceName") or args.device
    print(f"Setting {name} zone {args.zone} target to {args.temp}C...")
    client.set_target_temperature(args.device, args.zone, args.temp)
    print("Done.")


if __name__ == "__main__":
    main()
