import re

import pytest
from cloak_forge.derive import (
    derive_advertising_id,
    derive_android_id,
    derive_mac,
    derive_serial,
    luhn_check_digit,
    luhn_valid,
)

SEED = "7c1e" * 16

ANDROID_ID_RE = re.compile(r"^[0-9a-f]{16}$")
UUID_V4_RE = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
MAC_RE = re.compile(r"^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
SERIAL_RE = re.compile(r"^[0-9A-Z]{12}$")


def test_luhn_known_vectors():
    assert luhn_valid("490154203237518")
    assert not luhn_valid("490154203237519")
    assert luhn_check_digit("49015420323751") == "8"


def test_derivation_is_deterministic():
    assert derive_android_id(SEED) == derive_android_id(SEED)
    assert derive_advertising_id(SEED) == derive_advertising_id(SEED)


def test_different_seeds_derive_different_values():
    other_seed = "ab" * 32
    assert derive_android_id(SEED) != derive_android_id(other_seed)
    assert derive_advertising_id(SEED) != derive_advertising_id(other_seed)


def test_android_id_format():
    assert ANDROID_ID_RE.match(derive_android_id(SEED))


def test_advertising_id_is_uuid_v4():
    assert UUID_V4_RE.match(derive_advertising_id(SEED))


def test_mac_format_and_oui():
    default_mac = derive_mac(SEED)
    assert MAC_RE.match(default_mac)
    assert default_mac[:9] == derive_mac(SEED)[:9]
    branded = derive_mac(SEED, oui="3C:5A:B4")
    assert branded.startswith("3C:5A:B4:")


def test_serial_format():
    assert SERIAL_RE.match(derive_serial(SEED))


def test_bad_seed_rejected():
    with pytest.raises(ValueError):
        derive_android_id("tooshort")
