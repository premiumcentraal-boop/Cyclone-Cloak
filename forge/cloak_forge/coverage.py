"""Coverage ledger for every field the Forge emits.

Each emitted leaf is exactly one of: APPLIED (reaches the scoped app through the
module's prop surface), PENDING (needs the deeper Java-level hook sprint), or
INTERNAL (bookkeeping that never faces the app). tests/test_coverage.py keeps
this ledger honest against both forge output and the module source.
"""

from __future__ import annotations

APPLIED = frozenset({
    "device.manufacturer",
    "device.brand",
    "device.model",
    "device.product",
    "device.device",
    "device.hardware",
    "device.fingerprint",
    "device.version_release",
    "device.sdk_int",
    "device.security_patch",
    "device.build_id",
    "device.version_incremental",
    "device.build_date_utc",
    "device.first_api_level",
    "device.bootloader",
    "device.baseband",
    "telephony.carrier_name",
    "telephony.mcc",
    "telephony.mnc",
    "telephony.network_type",
    "display.density",
    "identifiers.android_id",
    "identifiers.advertising_id",
    "identifiers.app_set_id",
    "identifiers.mac",
    "identifiers.bt_mac",
    "identifiers.imei_primary",
    "identifiers.imei_secondary",
    "identifiers.sim_serial",
    "identifiers.gsf_id",
    "identifiers.widevine_id",
    "identifiers.serial",
    "locale.language",
    "locale.country",
    "locale.timezone",
})

PENDING = frozenset({
    "telephony.sim_slot_count",
    "network.egress_hint",
    "display.width",
    "display.height",
    "display.refresh_rate_hz",
    "display.screen_size_class",
})

INTERNAL = frozenset({
    "schema_version",
    "id",
    "name",
    "seed",
    "integrity.verdict",
    "integrity.checked_at",
    "integrity.keybox_tag",
    "health.state",
    "health.last_seen",
    "meta.source",
    "meta.forge_version",
    "meta.template",
})