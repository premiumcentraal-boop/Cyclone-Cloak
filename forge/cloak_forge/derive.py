"""Stable, per-profile identifier derivation.

Every identifier derives from the profile seed with HMAC-SHA256, so values are stable for the
lifetime of a profile, unique across profiles, and never stored in more than one place.
"""

from __future__ import annotations

import hashlib
import hmac
import re

_SEED_RE = re.compile(r"^[0-9a-f]{64}$")
_BASE36 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
_VARIANT_NIBBLES = ("8", "9", "a", "b")


def _digest(seed: str, field: str, size: int) -> bytes:
    if not _SEED_RE.match(seed):
        raise ValueError("seed must be 64 lowercase hex characters")
    return hmac.new(seed.encode("ascii"), field.encode("ascii"), hashlib.sha256).digest()[:size]


def _hex(seed: str, field: str, nbytes: int) -> str:
    return _digest(seed, field, nbytes).hex()


def _base36(value: int, length: int) -> str:
    chars: list[str] = []
    while value and len(chars) < length:
        value, remainder = divmod(value, 36)
        chars.append(_BASE36[remainder])
    return "".join(reversed(chars)).rjust(length, "0")


def _uuid_from_digest(seed: str, field: str) -> str:
    # Shape the raw digest into a well-formed UUIDv4: version nibble and
    # variant bits set, everything else from the HMAC.
    h = _hex(seed, field, 16)
    h = h[:12] + "4" + h[13:16] + _VARIANT_NIBBLES[int(h[16], 16) % 4] + h[17:]
    return f"{h[:8]}-{h[8:12]}-{h[12:16]}-{h[16:20]}-{h[20:]}"


def luhn_sum(digits: str) -> int:
    total = 0
    for index, ch in enumerate(reversed(digits)):
        value = ord(ch) - 48
        if index % 2 == 1:
            value *= 2
            if value > 9:
                value -= 9
        total += value
    return total


def luhn_valid(digits: str) -> bool:
    return digits.isdigit() and luhn_sum(digits) % 10 == 0


def luhn_check_digit(first_digits: str) -> str:
    return str((10 - luhn_sum(first_digits + "0") % 10) % 10)


def derive_android_id(seed: str) -> str:
    value = _hex(seed, "android_id", 8)
    if value == "0" * 16:
        value = _hex(seed, "android_id:1", 8)
    return value


def derive_advertising_id(seed: str) -> str:
    return _uuid_from_digest(seed, "advertising_id")


def derive_app_set_id(seed: str) -> str:
    return _uuid_from_digest(seed, "app_set_id")


def derive_mac(seed: str, iface: str = "wlan0", oui: str | None = None) -> str:
    raw = _digest(seed, f"mac:{iface}", 6)
    if oui is None:
        first = (raw[0] | 0x02) & 0xFE  # locally administered, unicast
        octets = (first, *raw[1:6])
    else:
        prefix = [int(part, 16) for part in oui.split(":")]
        if len(prefix) != 3:
            raise ValueError("oui must look like 'AA:BB:CC'")
        octets = (*prefix, *raw[3:6])
    return ":".join(f"{octet:02X}" for octet in octets)


def derive_imei(seed: str, slot: int = 0) -> str:
    digits = str(int.from_bytes(_digest(seed, f"imei:{slot}", 8), "big") % 10**14).zfill(14)
    if digits[0] == "0":
        digits = "9" + digits[1:]
    return digits + luhn_check_digit(digits)


def derive_serial(seed: str) -> str:
    return _base36(int.from_bytes(_digest(seed, "serial", 8), "big"), 12)


def derive_gsf_id(seed: str) -> str:
    return _hex(seed, "gsf_id", 8)


def derive_widevine_id(seed: str) -> str:
    return _hex(seed, "widevine_id", 32)

def _digits(seed: str, field: str, length: int) -> str:
    value = int.from_bytes(_digest(seed, field, 8), "big") % (10 ** length)
    return str(value).zfill(length)


def derive_sim_serial(seed: str) -> str:
    digits = _digits(seed, "sim_serial", 18)
    if digits[0] == "0":
        digits = "9" + digits[1:]
    return digits + luhn_check_digit(digits)


def derive_identifier_bundle(seed: str) -> dict[str, str]:
    return {
        "android_id": derive_android_id(seed),
        "advertising_id": derive_advertising_id(seed),
        "app_set_id": derive_app_set_id(seed),
        "mac": derive_mac(seed),
        "bt_mac": derive_mac(seed, iface="bt"),
        "imei_primary": derive_imei(seed, slot=0),
        "imei_secondary": derive_imei(seed, slot=1),
        "sim_serial": derive_sim_serial(seed),
        "gsf_id": derive_gsf_id(seed),
        "widevine_id": derive_widevine_id(seed),
        "serial": derive_serial(seed),
    }
