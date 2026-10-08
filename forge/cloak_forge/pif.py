"""Per-profile Play Integrity posture artifact (pif.json).

The Forge derives a PIF-style key/value file from the profile's device block so
every profile carries its own posture instead of sharing the host device's.
The companion publishes it next to the binding's profile.json; the module does
not read it (a Tricky Store-style consumer does), so it stays out of the
prop surface and the coverage ledger.
"""

from __future__ import annotations

from typing import Any, Mapping

_PIF_KEYS = (
    ("manufacturer", "MANUFACTURER"),
    ("model", "MODEL"),
    ("brand", "BRAND"),
    ("product", "PRODUCT"),
    ("device", "DEVICE"),
    ("fingerprint", "FINGERPRINT"),
    ("security_patch", "SECURITY_PATCH"),
    ("first_api_level", "FIRST_API_LEVEL"),
    ("build_id", "BUILD_ID"),
    ("version_incremental", "INCREMENTAL"),
    ("bootloader", "BOOTLOADER"),
    ("baseband", "BASEBAND"),
    ("version_release", "VERSION_RELEASE"),
    ("sdk_int", "SDK_INT"),
)


def build_pif(profile: Mapping[str, Any]) -> dict[str, str]:
    """Return the pif.json body for a forged profile. Never raises on data."""
    device = profile.get("device") or {}
    body: dict[str, str] = {}
    for field, key in _PIF_KEYS:
        value = device.get(field)
        if value is None:
            continue
        body[key] = str(value)
    return body
