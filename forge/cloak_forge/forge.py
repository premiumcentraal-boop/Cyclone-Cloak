"""Cloak Forge: generate full coherent device profiles from templates."""

from __future__ import annotations

import copy
import uuid
from typing import Any, Mapping

from .derive import derive_chrome_version, derive_identifier_bundle
from .templates import TEMPLATES

FORGE_VERSION = "0.2.0"


class ForgeError(ValueError):
    """Raised when a forge input or template is not usable."""


def build_ua(device: Mapping[str, Any], display_class: str, seed: str) -> str:
    """Compose the WebView user agent from the profile's own device fields.

    The UA must agree with Build: the model and Android release in the agent
    string are taken from the device block, never typed separately.
    """
    major, build, patch = derive_chrome_version(seed)
    token = "Mobile Safari" if display_class != "large" else "Safari"
    return (
        f"Mozilla/5.0 (Linux; Android {device['version_release']}; {device['model']}) "
        f"AppleWebKit/537.36 (KHTML, like Gecko) Chrome/{major}.0.{build}.{patch} "
        f"{token}/537.36"
    )


def forge_profile(
    name: str,
    seed: str,
    template_name: str = "pixel_7",
) -> dict[str, Any]:
    """Generate a complete, coherent, deterministic Cloak profile."""
    if not name or not name.strip():
        raise ForgeError("name must be a non-empty string")
    try:
        template = TEMPLATES[template_name]
    except KeyError as exc:
        raise ForgeError(f"unknown template '{template_name}'") from exc

    profile = uuid.uuid5(uuid.NAMESPACE_URL, f"https://cloak.cyclone.dev/{seed}/{name}")
    return {
        "schema_version": "0.2",
        "id": str(profile),
        "name": name,
        "seed": seed,
        "device": copy.deepcopy(template["device"]),
        "identifiers": derive_identifier_bundle(seed),
        "telephony": copy.deepcopy(template["telephony"]),
        "network": copy.deepcopy(template["network"]),
        "display": copy.deepcopy(template["display"]),
        "locale": copy.deepcopy(template["locale"]),
        "ua": {"value": build_ua(template["device"], template["display"]["screen_size_class"], seed)},
        "integrity": {"verdict": "unknown", "checked_at": None, "keybox_tag": None},
        "health": {"state": "new", "last_seen": None},
        "meta": {
            "source": "forge",
            "forge_version": FORGE_VERSION,
            "template": template_name,
        },
    }


def available_templates() -> list[str]:
    return sorted(TEMPLATES)
