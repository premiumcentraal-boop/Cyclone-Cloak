"""Regenerate forge/tests/vectors/*.json from the Python forge.

The vectors are the contract between the Python forge and the app's Kotlin port: both test suites run them. Rerun
this only when a rule changes on purpose, and commit the result with the matching Kotlin change.

    python3 forge/tools/make_vectors.py
"""

from __future__ import annotations

import copy
import json
import sys
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from cloak_forge import draft_phone, forge_profile, parse_build_prop, validate_profile  # noqa: E402
from cloak_forge import validate as validate_module  # noqa: E402

SEED = "ab" * 32
TODAY = "2026-01-01"

# (name, phone, mutations). A mutation is [dotted path, value] or [dotted path] to delete.
CASES = [
    ("a forged Pixel 7 is clean", "pixel_7", []),
    ("a forged Pixel 4 is clean", "pixel_4", []),
    ("a forged Galaxy S23 is clean", "galaxy_s23", []),
    ("model changed without the user agent", "pixel_7", [["device.model", "Pixel 8"]]),
    ("fingerprint brand disagrees", "pixel_7", [["device.brand", "samsung"]]),
    ("fingerprint grammar broken", "pixel_7", [["device.fingerprint", "not a fingerprint"]]),
    ("debug build type", "pixel_7", [["device.fingerprint", "google/panther/panther:13/TQ3A.230805.001/10193186:userdebug/release-keys"]]),
    ("dev-keys is only a warning", "pixel_7", [["device.fingerprint", "google/panther/panther:13/TQ3A.230805.001/10193186:user/dev-keys"]]),
    ("release and sdk disagree", "pixel_7", [["device.sdk_int", 34]]),
    ("sdk out of range", "pixel_7", [["device.sdk_int", 20]]),
    ("first api above sdk", "pixel_7", [["device.first_api_level", 34]]),
    ("patch not a date", "pixel_7", [["device.security_patch", "August"]]),
    ("patch in the future", "pixel_7", [["device.security_patch", "2027-01-05"]]),
    ("patch newer than the build", "pixel_7", [["device.security_patch", "2023-12-05"]]),
    ("missing device field stops early", "pixel_7", [["device.hardware"], ["device.model", "Pixel 8"]]),
    ("bad seed", "pixel_7", [["seed", "xyz"]]),
    ("wrong schema", "pixel_7", [["schema_version", "0.1"]]),
    ("blank name", "pixel_7", [["name", "  "]]),
    ("imei fails luhn", "pixel_7", [["identifiers.imei_primary", "472186978248630"]]),
    ("android id format", "pixel_7", [["identifiers.android_id", "XYZ"]]),
    ("unknown identifier", "pixel_7", [["identifiers.oaid", "abc"]]),
    ("carrier mcc mnc network slots", "pixel_7", [["telephony.carrier_name", ""], ["telephony.mcc", "31"], ["telephony.mnc", "2"], ["telephony.network_type", "6G"], ["telephony.sim_slot_count", 3]]),
    ("display fields", "pixel_7", [["display.width", 0], ["display.screen_size_class", "huge"]]),
    ("locale", "pixel_7", [["locale.language", "EN"], ["locale.country", "us"], ["locale.timezone", "New York"]]),
    ("ua missing", "pixel_7", [["ua"]]),
    ("ua format", "pixel_7", [["ua.value", "curl/8"]]),
    ("ua release", "pixel_7", [["device.version_release", "14"], ["device.sdk_int", 34], ["device.fingerprint", "google/panther/panther:14/TQ3A.230805.001/10193186:user/release-keys"]]),
]

DUMP = """# a build.prop excerpt
ro.product.manufacturer=Google
ro.product.brand=google
ro.product.model=Pixel 8
ro.product.name=shiba
ro.product.device=shiba
ro.hardware=zuma
ro.build.fingerprint=google/shiba/shiba:14/AP1A.240305.019/11445699:user/release-keys
ro.build.version.release=14
ro.build.version.sdk=34
ro.build.version.security_patch=2024-03-05
ro.build.id=AP1A.240305.019
ro.build.version.incremental=11445699
ro.build.date.utc=1709683200
ro.product.first_api_level=34
ro.bootloader=ripcurrent-14.4-11330117
ro.sf.lcd_density=420
"""

GETPROP = """[ro.product.manufacturer]: [samsung]
[ro.product.brand]: [samsung]
[ro.product.model]: [SM-S911B]
[ro.product.name]: [dm1qxxx]
[ro.product.device]: [dm1q]
[ro.hardware]: [qcom]
[ro.build.fingerprint]: [samsung/dm1qxxx/dm1q:14/UP1A.231005.007/S911BXXS3BWK5:user/release-keys]
[ro.build.version.release]: [14]
[ro.build.version.sdk]: [34]
[ro.build.version.security_patch]: [2023-12-01]
[ro.build.id]: [UP1A.231005.007]
[ro.build.version.incremental]: [S911BXXS3BWK5]
[ro.build.date.utc]: [1701936000]
[ro.product.first_api_level]: [33]
[gsm.version.baseband]: [S911BXXS3BWK5,S911BXXS3BWK5]
[gsm.sim.operator.alpha]: [Vodafone.de,]
[gsm.sim.operator.numeric]: [26202,]
[gsm.network.type]: [NR_NSA,Unknown]
[persist.radio.multisim.config]: [dsds]
[ro.sf.lcd_density]: [450]
[persist.sys.locale]: [de-DE]
[persist.sys.timezone]: [Europe/Berlin]
"""


def apply(profile, mutations):
    for mutation in mutations:
        *parents, leaf = mutation[0].split(".")
        target = profile
        for part in parents:
            target = target[part]
        if len(mutation) == 1:
            target.pop(leaf, None)
        else:
            target[leaf] = mutation[1]
    return profile


class _FixedDate(date):
    @classmethod
    def today(cls):
        return date.fromisoformat(TODAY)


def main() -> None:
    validate_module.date = _FixedDate  # the rules compare against "today"; pin it
    cases = []
    for name, phone, mutations in CASES:
        profile = apply(copy.deepcopy(forge_profile("Vault 01", SEED, phone)), mutations)
        findings = validate_profile(profile)
        cases.append({
            "name": name,
            "phone": phone,
            "mutations": mutations,
            "expect": [[f.severity, f.code] for f in findings],
        })
    out = ROOT / "tests" / "vectors"
    out.mkdir(parents=True, exist_ok=True)
    (out / "validation.json").write_text(json.dumps(
        {"about": __doc__.strip().splitlines()[0], "seed": SEED, "name": "Vault 01", "today": TODAY, "cases": cases},
        indent=2) + "\n")
    dumps = [
        {"name": "build.prop", "text": DUMP, "expect": draft_phone(parse_build_prop(DUMP))},
        {"name": "getprop", "text": GETPROP, "expect": draft_phone(parse_build_prop(GETPROP))},
    ]
    (out / "dumps.json").write_text(json.dumps({"cases": dumps}, indent=2, ensure_ascii=False) + "\n")


if __name__ == "__main__":
    main()
