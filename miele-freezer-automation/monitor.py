"""
Continuous temperature monitor with alerting.

Usage:
    python monitor.py [--interval 60] [--min-temp -25] [--max-temp -15]

Alerts are printed to stdout and optionally sent via email (set SMTP_* env vars).
"""

import argparse
import smtplib
import time
import os
from datetime import datetime
from email.mime.text import MIMEText

from miele_client import MieleClient, MieleAPIError


def send_email_alert(subject: str, body: str):
    smtp_host = os.environ.get("SMTP_HOST")
    if not smtp_host:
        return
    msg = MIMEText(body)
    msg["Subject"] = subject
    msg["From"] = os.environ["SMTP_FROM"]
    msg["To"] = os.environ["SMTP_TO"]
    with smtplib.SMTP(smtp_host, int(os.environ.get("SMTP_PORT", 587))) as server:
        server.starttls()
        server.login(os.environ["SMTP_USER"], os.environ["SMTP_PASSWORD"])
        server.send_message(msg)


def format_temperature(value_raw: int) -> str:
    """Miele returns temperature as integer degC * 100, or -32768 for N/A."""
    if value_raw == -32768:
        return "N/A"
    return f"{value_raw / 100:.1f}C"


def check_temperatures(
    client: MieleClient,
    freezer_id: str,
    freezer_name: str,
    min_temp: float,
    max_temp: float,
    alert_state: dict,
) -> None:
    temps = client.get_temperature(freezer_id)
    door_open = client.is_door_open(freezer_id)
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    for zone in temps:
        zone_id = zone.get("zone", 1)
        raw = zone.get("value_raw", -32768)
        target_raw = zone.get("targetValue", -32768)

        if raw == -32768:
            continue

        actual = raw / 100
        target_str = format_temperature(target_raw)
        print(f"[{now}] {freezer_name} zone {zone_id}: {actual:.1f}C (target {target_str})")

        alert_key = f"{freezer_id}:zone{zone_id}"

        if actual > max_temp:
            if not alert_state.get(f"{alert_key}:high"):
                msg = (
                    f"ALERT: {freezer_name} zone {zone_id} temperature too HIGH: "
                    f"{actual:.1f}C (max {max_temp}C)"
                )
                print(f"  *** {msg} ***")
                send_email_alert(f"Miele Freezer Alert -- {freezer_name}", msg)
                alert_state[f"{alert_key}:high"] = True
        else:
            alert_state.pop(f"{alert_key}:high", None)

        if actual < min_temp:
            if not alert_state.get(f"{alert_key}:low"):
                msg = (
                    f"ALERT: {freezer_name} zone {zone_id} temperature too LOW: "
                    f"{actual:.1f}C (min {min_temp}C)"
                )
                print(f"  *** {msg} ***")
                send_email_alert(f"Miele Freezer Alert -- {freezer_name}", msg)
                alert_state[f"{alert_key}:low"] = True
        else:
            alert_state.pop(f"{alert_key}:low", None)

    if door_open:
        door_key = f"{freezer_id}:door"
        if not alert_state.get(door_key):
            msg = f"ALERT: {freezer_name} door is open!"
            print(f"  *** {msg} ***")
            send_email_alert(f"Miele Freezer Alert -- {freezer_name}", msg)
            alert_state[door_key] = True
    else:
        alert_state.pop(f"{freezer_id}:door", None)


def main():
    parser = argparse.ArgumentParser(description="Monitor Miele freezer temperatures")
    parser.add_argument("--interval", type=int, default=60, help="Poll interval in seconds")
    parser.add_argument("--min-temp", type=float, default=-25.0, help="Minimum safe temperature C")
    parser.add_argument("--max-temp", type=float, default=-15.0, help="Maximum safe temperature C")
    args = parser.parse_args()

    client = MieleClient.from_env()
    freezers = client.get_freezers()

    if not freezers:
        print("No Miele freezers found on your account.")
        return

    print(f"Monitoring {len(freezers)} freezer(s) -- poll every {args.interval}s")
    for f in freezers:
        name = f.get("ident", {}).get("deviceName") or f["id"]
        print(f"  * {name} (id={f['id']})")

    alert_state: dict = {}

    while True:
        for freezer in freezers:
            fid = freezer["id"]
            name = freezer.get("ident", {}).get("deviceName") or fid
            try:
                check_temperatures(client, fid, name, args.min_temp, args.max_temp, alert_state)
            except MieleAPIError as exc:
                print(f"[{datetime.now()}] API error for {name}: {exc}")
        time.sleep(args.interval)


if __name__ == "__main__":
    main()
