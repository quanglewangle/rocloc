"""
Miele Third Party API client for freezer automation.
Handles OAuth2 authentication and all API calls.

Docs: https://www.miele.com/developer/
"""

import os
import time
import requests
from datetime import datetime, timedelta
from typing import Optional

MIELE_BASE_URL = "https://api.mcs3.miele.com/v1"
TOKEN_URL = "https://api.mcs3.miele.com/thirdparty/token"

DEVICE_TYPE_FREEZER = 19
DEVICE_TYPE_FRIDGE_FREEZER = 20


class MieleAuthError(Exception):
    pass


class MieleAPIError(Exception):
    pass


class MieleClient:
    def __init__(
        self,
        client_id: str,
        client_secret: str,
        access_token: Optional[str] = None,
        refresh_token: Optional[str] = None,
    ):
        self.client_id = client_id
        self.client_secret = client_secret
        self._access_token = access_token
        self._refresh_token = refresh_token
        self._token_expiry: Optional[datetime] = None
        self._session = requests.Session()

    @classmethod
    def from_env(cls) -> "MieleClient":
        return cls(
            client_id=os.environ["MIELE_CLIENT_ID"],
            client_secret=os.environ["MIELE_CLIENT_SECRET"],
            access_token=os.environ.get("MIELE_ACCESS_TOKEN"),
            refresh_token=os.environ.get("MIELE_REFRESH_TOKEN"),
        )

    def authenticate(self, username: str, password: str) -> dict:
        """Exchange credentials for OAuth2 tokens."""
        resp = requests.post(
            TOKEN_URL,
            data={
                "grant_type": "password",
                "username": username,
                "password": password,
                "client_id": self.client_id,
                "client_secret": self.client_secret,
                "vg": "en-GB",
            },
        )
        if resp.status_code != 200:
            raise MieleAuthError(f"Authentication failed: {resp.status_code} {resp.text}")
        tokens = resp.json()
        self._store_tokens(tokens)
        return tokens

    def _store_tokens(self, tokens: dict):
        self._access_token = tokens["access_token"]
        self._refresh_token = tokens.get("refresh_token")
        expires_in = tokens.get("expires_in", 3600)
        self._token_expiry = datetime.now() + timedelta(seconds=expires_in - 60)

    def _refresh_access_token(self):
        if not self._refresh_token:
            raise MieleAuthError("No refresh token available — re-authenticate.")
        resp = requests.post(
            TOKEN_URL,
            data={
                "grant_type": "refresh_token",
                "refresh_token": self._refresh_token,
                "client_id": self.client_id,
                "client_secret": self.client_secret,
            },
        )
        if resp.status_code != 200:
            raise MieleAuthError(f"Token refresh failed: {resp.status_code} {resp.text}")
        self._store_tokens(resp.json())

    def _get_headers(self) -> dict:
        if not self._access_token:
            raise MieleAuthError("Not authenticated. Call authenticate() first.")
        if self._token_expiry and datetime.now() >= self._token_expiry:
            self._refresh_access_token()
        return {
            "Authorization": f"Bearer {self._access_token}",
            "Accept": "application/json",
            "Content-Type": "application/json",
        }

    def _get(self, path: str) -> dict:
        url = f"{MIELE_BASE_URL}{path}"
        resp = self._session.get(url, headers=self._get_headers())
        if resp.status_code == 401:
            self._refresh_access_token()
            resp = self._session.get(url, headers=self._get_headers())
        if resp.status_code not in (200, 204):
            raise MieleAPIError(f"GET {path} failed: {resp.status_code} {resp.text}")
        return resp.json() if resp.content else {}

    def _put(self, path: str, payload: dict) -> None:
        url = f"{MIELE_BASE_URL}{path}"
        resp = self._session.put(url, json=payload, headers=self._get_headers())
        if resp.status_code == 401:
            self._refresh_access_token()
            resp = self._session.put(url, json=payload, headers=self._get_headers())
        if resp.status_code not in (200, 204):
            raise MieleAPIError(f"PUT {path} failed: {resp.status_code} {resp.text}")

    # --- Device discovery ---

    def get_all_devices(self) -> dict:
        return self._get("/devices")

    def get_freezers(self) -> list[dict]:
        devices = self.get_all_devices()
        return [
            {"id": device_id, **info}
            for device_id, info in devices.items()
            if info.get("ident", {}).get("type", {}).get("value_raw")
            in (DEVICE_TYPE_FREEZER, DEVICE_TYPE_FRIDGE_FREEZER)
        ]

    # --- State queries ---

    def get_device_state(self, device_id: str) -> dict:
        return self._get(f"/devices/{device_id}/state")

    def get_temperature(self, device_id: str) -> list[dict]:
        """Return list of temperature zones with current and target values (degC)."""
        state = self.get_device_state(device_id)
        return state.get("temperatures", [])

    def is_door_open(self, device_id: str) -> bool:
        state = self.get_device_state(device_id)
        return state.get("signalDoor", False)

    def get_status_text(self, device_id: str) -> str:
        state = self.get_device_state(device_id)
        return state.get("status", {}).get("value_localized", "Unknown")

    # --- Actions ---

    def set_super_freeze(self, device_id: str, enabled: bool) -> None:
        """Enable or disable SuperFreeze mode."""
        self._put(
            f"/devices/{device_id}/actions",
            {"processAction": 6 if enabled else 7},
        )

    def set_target_temperature(self, device_id: str, zone: int, temperature: int) -> None:
        """Set target temperature for a zone (degC). Zone 1 = freezer compartment."""
        self._put(
            f"/devices/{device_id}/actions",
            {"targetTemperature": [{"zone": zone, "value": temperature}]},
        )

    def set_power(self, device_id: str, on: bool) -> None:
        """Power the appliance on or off (action 1=on, 2=off)."""
        self._put(f"/devices/{device_id}/actions", {"processAction": 1 if on else 2})
