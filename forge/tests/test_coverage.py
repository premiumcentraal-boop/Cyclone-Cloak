"""Coverage ledger tests: no Forge field may silently skip the module."""

from __future__ import annotations

from pathlib import Path

from cloak_forge.coverage import APPLIED, INTERNAL, PENDING
from cloak_forge.forge import forge_profile

ROOT = Path(__file__).resolve().parents[2]


def leaf_paths(value, prefix=""):
    paths = set()
    for key, item in value.items():
        path = f"{prefix}.{key}" if prefix else key
        if isinstance(item, dict):
            paths |= leaf_paths(item, path)
        else:
            paths.add(path)
    return paths


def test_every_emitted_field_is_accounted_for():
    profile = forge_profile("Coverage", "a" * 64)
    assert leaf_paths(profile) == APPLIED | PENDING | INTERNAL


def test_ledger_states_are_disjoint():
    assert not APPLIED & PENDING
    assert not APPLIED & INTERNAL
    assert not PENDING & INTERNAL


def test_applied_fields_are_referenced_by_the_module():
    source = (ROOT / "android" / "module" / "src" / "main" / "cpp" / "cloak.cpp").read_text(encoding="utf-8")
    for path in sorted(APPLIED):
        key = path.split(".", 1)[1]
        assert key in source, f"module source never references profile key '{key}' ({path})"

def test_identifier_and_locale_read_paths_stay_wired():
    source = (ROOT / "android" / "module" / "src" / "main" / "cpp" / "cloak.cpp").read_text(encoding="utf-8")
    for prop in (
        "ro.serialno",
        "ro.boot.serialno",
        "persist.sys.timezone",
        "persist.sys.language",
        "persist.sys.country",
        "persist.sys.locale",
        "user.language",
        "user.country",
    ):
        assert prop in source, f"module lost the real read path for {prop}"


def test_identifier_props_are_namespaced():
    source = (ROOT / "android" / "module" / "src" / "main" / "cpp" / "cloak.cpp").read_text(encoding="utf-8")
    for key in (
        "cloak.android_id",
        "cloak.advertising_id",
        "cloak.app_set_id",
        "cloak.wifi_mac",
        "cloak.bt_mac",
        "cloak.imei0",
        "cloak.imei1",
        "cloak.sim_serial0",
        "cloak.gsf_id",
        "cloak.widevine_id",
    ):
        assert key in source, f"module lost the stable prop key {key}"
