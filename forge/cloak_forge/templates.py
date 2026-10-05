"""Realistic device templates used by the Cloak Forge."""

from __future__ import annotations

from typing import Any

TEMPLATES: dict[str, dict[str, Any]] = {
    "pixel_7": {
        "device": {
            "manufacturer": "Google",
            "brand": "google",
            "model": "Pixel 7",
            "product": "panther",
            "device": "panther",
            "hardware": "gs101",
            "fingerprint": "google/panther/panther:13/TQ3A.230805.001/10193186:user/release-keys",
            "version_release": "13",
            "sdk_int": 33,
            "security_patch": "2023-08-05",
            "build_id": "TQ3A.230805.001",
            "version_incremental": "10193186",
            "build_date_utc": 1691712000,
            "first_api_level": 33,
            "bootloader": "panther-1.0-8769422",
            "baseband": "g5123-230712-230712-B10046521",
        },
        "telephony": {
            "sim_slot_count": 1,
            "carrier_name": "T-Mobile",
            "mcc": "310",
            "mnc": "260",
            "network_type": "5G",
        },
        "network": {"egress_hint": "us-east"},
        "display": {
            "width": 1080,
            "height": 2400,
            "density": 420,
            "refresh_rate_hz": 90,
            "screen_size_class": "large",
        },
        "locale": {"language": "en", "country": "US", "timezone": "America/New_York"},
    },
    "galaxy_s23": {
        "device": {
            "manufacturer": "samsung",
            "brand": "samsung",
            "model": "SM-S911B",
            "product": "beyond1q",
            "device": "beyond1q",
            "hardware": "exynos2200",
            "fingerprint": "samsung/beyond1q/beyond1q:13/TP1A.220624.014/S911BXXU2AWA1:user/release-keys",
            "version_release": "13",
            "sdk_int": 33,
            "security_patch": "2023-08-01",
            "build_id": "TP1A.220624.014",
            "version_incremental": "S911BXXU2AWA1",
            "build_date_utc": 1691952000,
            "first_api_level": 33,
            "bootloader": "beyond1q-1.0-24551412",
            "baseband": "S911BXXU2AWA1",
        },
        "telephony": {
            "sim_slot_count": 2,
            "carrier_name": "Vodafone",
            "mcc": "262",
            "mnc": "02",
            "network_type": "5G",
        },
        "network": {"egress_hint": "eu-central"},
        "display": {
            "width": 1080,
            "height": 2340,
            "density": 420,
            "refresh_rate_hz": 120,
            "screen_size_class": "large",
        },
        "locale": {"language": "de", "country": "DE", "timezone": "Europe/Berlin"},
    },
}
