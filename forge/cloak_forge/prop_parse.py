"""Parse Android build.prop dumps into draft Cloak profiles."""

from __future__ import annotations

import re
import uuid
from typing import Any, Mapping

from .derive import derive_chrome_version

_DEVICE_KEYS = {
    "manufacturer": ("ro.product.manufacturer",),
    "brand": ("ro.product.brand", "ro.product.manufacturer"),
    "model": ("ro.product.model",),
    "product": ("ro.product.name", "ro.product.model"),
    "device": ("ro.product.device", "ro.product.model"),
    "hardware": ("ro.hardware",),
    "fingerprint": ("ro.build.fingerprint",),
    "version_release": ("ro.build.version.release",),
    "sdk_int": ("ro.build.version.sdk",),
    "security_patch": ("ro.build.version.security_patch",),
    "build_id": ("ro.build.id",),
    "version_incremental": ("ro.build.version.incremental",),
    "build_date_utc": ("ro.build.date.utc",),
    "first_api_level": ("ro.product.first_api_level", "ro.board.first_api_level"),
    "bootloader": ("ro.bootloader",),
    "baseband": ("ro.baseband", "gsm.version.baseband"),
}


_GETPROP_LINE = re.compile(r"^\[([^\]]+)\]:\s*\[(.*)\]$")


def parse_build_prop(text: str) -> dict[str, str]:
    """Parse build.prop text, or `adb shell getprop` output, into a key/value mapping.

    Comments and blanks are ignored. getprop lines look like `[ro.product.model]: [Pixel 7]`.
    """
    props: dict[str, str] = {}
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        getprop = _GETPROP_LINE.match(line)
        if getprop:
            props[getprop.group(1).strip()] = getprop.group(2).strip()
            continue
        if "=" not in line:
            continue
        key, _, value = line.partition("=")
        props[key.strip()] = value.strip()
    return props


def _first(props: Mapping[str, str], keys: tuple[str, ...]) -> str:
    for key in keys:
        value = props.get(key)
        if value:
            return value
    return ""


def _to_int(value: str, default: int) -> int:
    try:
        return int(value.strip())
    except (TypeError, ValueError):
        return default


def draft_device(props: Mapping[str, str]) -> dict[str, Any]:
    """Build the device block of a Cloak profile from parsed dump properties."""
    get = _first
    sdk = _to_int(get(props, _DEVICE_KEYS["sdk_int"]), 0)
    return {
        "manufacturer": get(props, _DEVICE_KEYS["manufacturer"]),
        "brand": get(props, _DEVICE_KEYS["brand"]),
        "model": get(props, _DEVICE_KEYS["model"]),
        "product": get(props, _DEVICE_KEYS["product"]),
        "device": get(props, _DEVICE_KEYS["device"]),
        "hardware": get(props, _DEVICE_KEYS["hardware"]),
        "fingerprint": get(props, _DEVICE_KEYS["fingerprint"]),
        "version_release": get(props, _DEVICE_KEYS["version_release"]),
        "sdk_int": sdk,
        "security_patch": get(props, _DEVICE_KEYS["security_patch"]),
        "build_id": get(props, _DEVICE_KEYS["build_id"]),
        "version_incremental": get(props, _DEVICE_KEYS["version_incremental"]),
        "build_date_utc": _to_int(get(props, _DEVICE_KEYS["build_date_utc"]), 0),
        "first_api_level": _to_int(get(props, _DEVICE_KEYS["first_api_level"]), sdk),
        "bootloader": get(props, _DEVICE_KEYS["bootloader"]),
        "baseband": get(props, _DEVICE_KEYS["baseband"]),
    }


def draft_profile(name: str, seed: str, build_prop_text: str) -> dict[str, Any]:
    """Assemble a full draft profile; the id is deterministic for a given seed and name."""
    props = parse_build_prop(build_prop_text)
    device = draft_device(props)
    profile = uuid.uuid5(uuid.NAMESPACE_URL, f"https://cloak.cyclone.dev/{seed}/{name}")
    return {
        "schema_version": "0.2",
        "id": str(profile),
        "name": name,
        "seed": seed,
        "device": device,
        "ua": _draft_ua(device, seed),
        "meta": {"source": "build.prop"},
    }


def _draft_ua(device: Mapping[str, Any], seed: str) -> dict[str, str]:
    """Compose the draft UA from the dumped device block, same as the forge path.

    Dump profiles carry no display class, so they always use the phone form.
    """
    major, build, patch = derive_chrome_version(seed)
    value = (
        f"Mozilla/5.0 (Linux; Android {device['version_release']}; {device['model']}) "
        f"AppleWebKit/537.36 (KHTML, like Gecko) Chrome/{major}.0.{build}.{patch} "
        f"Mobile Safari/537.36"
    )
    return {"value": value}


_NETWORK_TYPES = {
    "NR": "5G", "NR_NSA": "5G", "NR_SA": "5G", "5G": "5G",
    "LTE": "LTE", "LTE_CA": "LTE", "IWLAN": "LTE",
    "HSPA": "HSPA", "HSPAP": "HSPA", "HSDPA": "HSPA", "HSUPA": "HSPA",
    "UMTS": "UMTS", "TD_SCDMA": "UMTS",
    "GSM": "GSM", "EDGE": "GSM", "GPRS": "GSM",
}


def _first_of_list(value: str) -> str:
    """Dual-SIM properties list both slots: `Vodafone,` or `LTE,Unknown`. Take the first real one."""
    for part in value.split(","):
        part = part.strip()
        if part and part.lower() not in ("unknown", "null"):
            return part
    return ""


def draft_phone(props: Mapping[str, str]) -> dict[str, Any]:
    """Every template block a dump can tell: device, telephony, display (density only), locale.

    What a dump cannot tell (screen size, refresh rate, size class, egress) is left out for the owner to fill in.
    """
    phone: dict[str, Any] = {"device": draft_device(props)}

    telephony: dict[str, Any] = {}
    carrier = _first_of_list(props.get("gsm.sim.operator.alpha", "") or props.get("gsm.operator.alpha", ""))
    if carrier:
        telephony["carrier_name"] = carrier
    numeric = _first_of_list(props.get("gsm.sim.operator.numeric", "") or props.get("gsm.operator.numeric", ""))
    if re.fullmatch(r"\d{5,6}", numeric):
        telephony["mcc"], telephony["mnc"] = numeric[:3], numeric[3:]
    network = _NETWORK_TYPES.get(_first_of_list(props.get("gsm.network.type", "")).upper())
    if network:
        telephony["network_type"] = network
    multisim = props.get("persist.radio.multisim.config", "").lower()
    if multisim in ("dsds", "dsda", "tsts"):
        telephony["sim_slot_count"] = 2
    elif multisim or telephony:
        telephony["sim_slot_count"] = 1
    if telephony:
        phone["telephony"] = telephony

    density = _to_int(props.get("ro.sf.lcd_density", ""), 0)
    if density > 0:
        phone["display"] = {"density": density}

    locale: dict[str, str] = {}
    tag = props.get("persist.sys.locale", "") or props.get("ro.product.locale", "")
    match = re.fullmatch(r"([a-z]{2})[-_]([A-Z]{2}).*", tag)
    if match:
        locale["language"], locale["country"] = match.group(1), match.group(2)
    else:
        language = props.get("ro.product.locale.language", "")
        country = props.get("ro.product.locale.region", "")
        if re.fullmatch(r"[a-z]{2}", language):
            locale["language"] = language
        if re.fullmatch(r"[A-Z]{2}", country):
            locale["country"] = country
    timezone_name = props.get("persist.sys.timezone", "")
    if timezone_name:
        locale["timezone"] = timezone_name
    if locale:
        phone["locale"] = locale
    return phone
