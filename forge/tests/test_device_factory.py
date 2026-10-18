"""Acceptance tests for the pure, opt-in Device Factory v1 API."""

import copy
import json
import uuid

import cloak_forge.device_factory as module
import pytest
from cloak_forge.derive import luhn_check_digit, luhn_valid
from cloak_forge.device_factory import DeviceFactory, FactoryError, InstanceRequest
from cloak_forge.engine import IDENTIFIER_FIELDS
from cloak_forge.forge import forge_profile
from cloak_forge.validate import validate_profile

STAMP = "2026-10-18T12:00:00+00:00"


@pytest.fixture(scope="module")
def factory():
    return DeviceFactory()


def request(**changes):
    return InstanceRequest(**dict(template_id="pixel_7", seed="example", **changes))


def codes(findings):
    return {f.code for f in findings}


def test_catalog_covers_complete_real_device_classes(factory):
    catalog = factory.catalog()
    assert len(catalog) >= 12
    assert {t["device"]["brand"] for t in catalog} == {"google", "samsung"}
    for t in catalog:
        assert not module._template_findings(t)
        assert t["device"]["bootloader"] and t["device"]["baseband"]
        assert all(t["reality"][s]["values"] for s in module.SECTIONS)
        assert (
            t["classes"]["play_services_expectations"]["certification"] == "unverified"
        )
        bundle = factory.generate(
            InstanceRequest(t["id"], "catalog-test"), created_at=STAMP
        )
        assert bundle["quality"]["validation_state"] == "valid"
        assert bundle["quality"]["completeness_score"] == 100
        assert not bundle["quality"]["failed_checks"]
        assert not [
            f for f in validate_profile(bundle["profile"]) if f.severity == "error"
        ]
        assert not factory.validate(bundle["profile"], t["id"])


def test_determinism_and_golden_vector(factory):
    r = InstanceRequest("pixel_7", "golden", 3, 2, "Golden")
    a = factory.generate(r, created_at=STAMP)
    assert a == factory.generate(r, created_at=STAMP)
    assert (
        a["profile"]["seed"]
        == "1f187cc963a340d255d691590ceb79b4b7e2c6bd0e845cfdf165bbc7410ad005"
    )
    assert a["profile"]["id"] == "3075f229-eaa5-50c7-bd76-04e6bdd04551"
    renamed = factory.generate(InstanceRequest("pixel_7", "golden", 3, 2, "Renamed"))
    assert renamed["profile"]["identifiers"] == a["profile"]["identifiers"]
    assert renamed["profile"]["id"] == a["profile"]["id"]
    assert (
        factory.generate(r)["profile"] == a["profile"]
    )  # Timestamp belongs to report only.


@pytest.mark.parametrize(
    "changes",
    [
        {"template_id": "pixel_8"},
        {"seed": "another"},
        {"ordinal": 1},
        {"revision": 1},
    ],
)
def test_every_derivation_axis_changes_all_identifiers(factory, changes):
    base = {"template_id": "pixel_7", "seed": "example", "ordinal": 0, "revision": 0}
    a = factory.generate(InstanceRequest(**base))["profile"]
    b = factory.generate(InstanceRequest(**{**base, **changes}))["profile"]
    assert all(a["identifiers"][k] != b["identifiers"][k] for k in IDENTIFIER_FIELDS)
    assert a["id"] != b["id"]


@pytest.mark.parametrize("field", IDENTIFIER_FIELDS)
@pytest.mark.parametrize("bad", ["", None, 123, "malformed"])
def test_malformed_identifiers_are_rejected(factory, field, bad):
    p = factory.generate(request())["profile"]
    p["identifiers"][field] = bad
    assert any(f.severity == "error" for f in factory.validate(p, "pixel_7"))


def test_checksums_mac_bits_and_region(factory):
    p = factory.generate(request())["profile"]
    for k in ("imei_primary", "imei_secondary", "sim_serial"):
        assert luhn_valid(p["identifiers"][k])
        bad = copy.deepcopy(p)
        v = bad["identifiers"][k]
        bad["identifiers"][k] = v[:-1] + str((int(v[-1]) + 1) % 10)
        assert any(f.severity == "error" for f in factory.validate(bad, "pixel_7"))
    for field in ("mac", "bt_mac"):
        assert int(p["identifiers"][field][:2], 16) & 3 == 2
        for first in ("00", "01", "03"):
            bad = copy.deepcopy(p)
            bad["identifiers"][field] = first + bad["identifiers"][field][2:]
            assert "MAC_LOCALITY" in codes(factory.validate(bad, "pixel_7"))
    bad = copy.deepcopy(p)
    digits = "8949" + p["identifiers"]["sim_serial"][4:-1]
    bad["identifiers"]["sim_serial"] = digits + luhn_check_digit(digits)
    assert "ICCID_REGION_CHECKSUM" in codes(factory.validate(bad, "pixel_7"))
    for field in ("advertising_id", "app_set_id"):
        assert uuid.UUID(p["identifiers"][field]).version == 4


@pytest.mark.parametrize("field", ["android_id", "serial", "gsf_id", "widevine_id"])
def test_placeholders_are_errors(factory, field):
    p = factory.generate(request())["profile"]
    p["identifiers"][field] = "0" * len(p["identifiers"][field])
    assert "IDENTIFIER_REQUIRED" in codes(factory.validate(p, "pixel_7"))


@pytest.mark.parametrize(
    "block,key,value",
    [
        ("device", "security_patch", "2023-07-05"),
        ("device", "sdk_int", 28),
        ("device", "hardware", "zuma"),
        ("device", "fingerprint", "invalid"),
        ("display", "width", 1440),
        ("display", "refresh_rate_hz", 240),
        ("display", "screen_size_class", "large"),
        ("telephony", "mcc", "262"),
        ("locale", "timezone", "Europe/Berlin"),
        ("camera", "cameras", []),
    ],
)
def test_impossible_profile_combinations_rejected(factory, block, key, value):
    p = factory.generate(request())["profile"]
    p[block][key] = value
    assert "TEMPLATE_MISMATCH" in codes(factory.validate(p, "pixel_7"))


@pytest.mark.parametrize(
    "section,key,value",
    [
        ("memory", "ram_gib", 64),
        ("compute", "gpu", "Adreno 740"),
        ("power", "design_capacity_mah", 1200),
        ("cameras", "inventory", []),
        ("sensors", "inventory", []),
        ("panel", "modes", []),
        ("webview", "version", "999.0.0.0"),
        ("carrier", "country", "DE"),
    ],
)
def test_impossible_capability_combinations_rejected(factory, section, key, value):
    p = factory.generate(request())["profile"]
    p["reality"][section]["values"][key] = value
    assert "CAPABILITY_MISMATCH" in codes(factory.validate(p, "pixel_7"))


def test_collision_gate_includes_same_ids_cross_slots_and_cross_types(factory):
    p = factory.generate(request())["profile"]
    assert "PROFILE_ID_COLLISION" in codes(factory.validate(p, "pixel_7", [p]))
    other = factory.generate(request(ordinal=1))["profile"]
    other["identifiers"]["imei_secondary"] = p["identifiers"]["imei_primary"]
    assert "LIBRARY_COLLISION" in codes(factory.validate(p, "pixel_7", [other]))
    other["identifiers"] = {"gsf_id": p["identifiers"]["android_id"].upper()}
    assert "LIBRARY_COLLISION" in codes(factory.validate(p, "pixel_7", [other]))
    p["identifiers"]["imei_secondary"] = p["identifiers"]["imei_primary"]
    assert "IDENTIFIER_REUSED" in codes(factory.validate(p, "pixel_7"))


@pytest.mark.parametrize("policy", ["shared", "per-item"])
def test_batch_policies_regenerate_and_are_unique(factory, policy):
    kwargs = {"seed_policy": policy, "created_at": STAMP}
    if policy == "per-item":
        kwargs["item_seeds"] = ["item"] * 30
    a = factory.batch("pixel_7", "root", 30, label_prefix="Fleet", **kwargs)
    assert a == factory.batch("pixel_7", "root", 30, label_prefix="Fleet", **kwargs)
    assert a[0]["profile"]["name"] == "Fleet 001"
    all_values = [
        v.lower().replace(":", "").replace("-", "")
        for b in a
        for v in b["profile"]["identifiers"].values()
    ]
    assert len(all_values) == len(set(all_values))


def test_many_templates_and_instances_do_not_reuse_identifiers(factory):
    library = []
    for t in factory.catalog():
        batch = factory.batch(t["id"], "same-root", 20, existing=library)
        library.extend(b["profile"] for b in batch)
    assert len({p["id"] for p in library}) == 240


def test_batch_rejects_collision_without_reseeding_or_mutating(factory):
    saved = factory.batch("pixel_7", "root", 1)[0]["profile"]
    before = copy.deepcopy(saved)
    with pytest.raises(FactoryError):
        factory.batch("pixel_7", "root", 3, existing=[saved])
    assert saved == before
    with pytest.raises(FactoryError):
        factory.generate(
            InstanceRequest("pixel_7", "root", 0, 0, "Device 001"), [saved]
        )


def test_mid_batch_failure_returns_nothing_and_does_not_mutate_library(
    factory, monkeypatch
):
    saved = factory.generate(request())["profile"]
    before = copy.deepcopy(saved)
    original = module._body

    def colliding(template, r):
        p = original(template, r)
        if r.ordinal == 2:
            p["identifiers"]["serial"] = original(
                template, InstanceRequest(r.template_id, r.seed, 0)
            )["identifiers"]["serial"]
        return p

    monkeypatch.setattr(module, "_body", colliding)
    with pytest.raises(FactoryError) as caught:
        factory.batch("pixel_7", "other", 5, existing=[saved])
    assert "LIBRARY_COLLISION" in codes(caught.value.findings)
    assert saved == before


def test_defensive_copies(factory):
    catalog = factory.catalog()
    catalog[0]["device"]["model"] = "changed"
    assert factory.catalog()[0]["device"]["model"] != "changed"
    p = factory.generate(request())["profile"]
    p["reality"]["memory"]["values"]["ram_gib"] = 100
    assert (
        factory.generate(request())["profile"]["reality"]["memory"]["values"]["ram_gib"]
        == 8
    )


@pytest.mark.parametrize(
    "field,value",
    [
        ("seed", ""),
        ("seed", None),
        ("seed", "x" * 1025),
        ("ordinal", True),
        ("ordinal", -1),
        ("revision", -1),
        ("revision", 1.2),
        ("label", "x" * 65),
        ("label", "bad\nlabel"),
        ("template_id", "missing"),
    ],
)
def test_invalid_requests(factory, field, value):
    args = {"template_id": "pixel_7", "seed": "example"}
    args[field] = value
    with pytest.raises(FactoryError):
        factory.generate(InstanceRequest(**args))


@pytest.mark.parametrize(
    "kwargs",
    [
        {"count": 0},
        {"count": True},
        {"count": 1001},
        {"count": 1, "seed_policy": "wrong"},
        {"count": 2, "seed_policy": "per-item", "item_seeds": ["one"]},
        {"count": 1, "item_seeds": ["one"]},
        {"count": 1, "start_ordinal": -1},
        {"count": 1, "label_prefix": "x" * 64},
    ],
)
def test_invalid_batches(factory, kwargs):
    with pytest.raises(FactoryError):
        factory.batch("pixel_7", "example", **kwargs)


@pytest.mark.parametrize(
    "mutation",
    [
        lambda t: t["device"].update(sdk_int=26),
        lambda t: t["device"].update(security_patch="2023-01-01"),
        lambda t: t["display"].update(refresh_rate_hz=240),
        lambda t: t["classes"].update(mode_class="fixed-60"),
        lambda t: t["camera"].update(cameras=[]),
        lambda t: t["reality"]["compute"]["values"].update(soc="Google Tensor G4"),
        lambda t: t["reality"]["power"]["values"].update(design_capacity_mah=20),
        lambda t: t["classes"]["carrier_region"].update(iccid_prefix="8949"),
        lambda t: t["device"].update(bootloader="unrelated"),
        lambda t: t.pop("classes"),
    ],
)
def test_invalid_templates_rejected_at_load(factory, tmp_path, mutation):
    t = next(t for t in factory.catalog() if t["id"] == "pixel_7")
    mutation(t)
    path = tmp_path / "catalog.json"
    path.write_text(json.dumps({"format": 1, "templates": [t]}))
    with pytest.raises(FactoryError):
        DeviceFactory(path)


def test_duplicate_and_empty_catalog(factory, tmp_path):
    path = tmp_path / "catalog.json"
    t = factory.catalog()[0]
    for rows in ([], [t, t]):
        path.write_text(json.dumps({"format": 1, "templates": rows}))
        with pytest.raises(FactoryError):
            DeviceFactory(path)


@pytest.mark.parametrize("bad", [None, [], {}, {"device": None}, {"identifiers": []}])
def test_malformed_profiles_fail_closed(factory, bad):
    assert any(f.severity == "error" for f in factory.validate(bad, "pixel_7"))


def test_quality_never_trusts_imported_validation_state(factory):
    r = request()
    bundle = factory.generate(r, created_at=STAMP)
    p = bundle["profile"]
    p["identifiers"]["serial"] = (
        "ABCDEF123456"  # Well formed but not derived from this request.
    )
    report = factory.quality_report(p, r, created_at=STAMP)
    assert report["validation_state"] == "invalid"
    assert "DERIVATION_MISMATCH" in {f["code"] for f in report["failed_checks"]}
    public = json.dumps(report)
    assert all(v not in public for v in p["identifiers"].values())
    assert report["revision"] == 0 and report["template_id"] == "pixel_7"
    assert report["creation_timestamp"] == STAMP
    del p["identifiers"]["android_id"]
    assert factory.quality_report(p, r)["completeness_score"] < 100


def test_legacy_generation_stays_compatible(factory):
    for version in ("0.2", "0.3", "0.4"):
        a = forge_profile("Legacy", "ab" * 32, "pixel_7", version)
        factory.generate(request())
        assert forge_profile("Legacy", "ab" * 32, "pixel_7", version) == a
        assert validate_profile(a) == []


@pytest.mark.parametrize("stamp", ["", "invalid", "2026-10-18T12:00:00", 123])
def test_timestamp_requires_an_explicit_timezone(factory, stamp):
    with pytest.raises(FactoryError):
        factory.generate(request(), created_at=stamp)


def test_label_length_counts_surrounding_whitespace(factory):
    with pytest.raises(FactoryError):
        factory.generate(request(label=" " * 64 + "x"))


@pytest.mark.parametrize(
    "document", [[], None, {"format": True, "templates": []}, {"format": 99}]
)
def test_malformed_catalog_documents(tmp_path, document):
    path = tmp_path / "catalog.json"
    path.write_text(json.dumps(document))
    with pytest.raises(FactoryError):
        DeviceFactory(path)


def test_consistent_wrong_model_platform_is_rejected(factory, tmp_path):
    t = next(t for t in factory.catalog() if t["id"] == "pixel_7")
    t["device"]["hardware"] = "gs101"
    t["reality"]["compute"]["values"]["soc"] = "Google Tensor"
    t["reality"]["compute"]["values"]["gpu"] = "Mali-G78"
    t["classes"]["graphics_adapter_class"] = "Mali-G78"
    path = tmp_path / "catalog.json"
    path.write_text(json.dumps({"format": 1, "templates": [t]}))
    with pytest.raises(FactoryError):
        DeviceFactory(path)


def test_strict_profile_fields_and_request_binding(factory):
    r = request()
    p = factory.generate(r)["profile"]
    p["unsupported"] = True
    assert "PROFILE_FIELDS" in codes(factory.validate(p, "pixel_7"))
    del p["unsupported"]
    p["id"] = str(uuid.uuid4())
    assert "PROFILE_ID_DERIVATION" in codes(factory.validate(p, "pixel_7"))
    assert factory.quality_report(p, r)["validation_state"] == "invalid"


def test_batch_chunking_and_per_item_seeds(factory):
    all_items = factory.batch("pixel_7", "root", 6, created_at=STAMP)
    first = factory.batch("pixel_7", "root", 3, created_at=STAMP)
    second = factory.batch(
        "pixel_7",
        "root",
        3,
        start_ordinal=3,
        existing=[b["profile"] for b in first],
        created_at=STAMP,
    )
    assert first + second == all_items
    items = factory.batch(
        "pixel_7",
        "unused",
        2,
        seed_policy="per-item",
        item_seeds=["first", "second"],
        created_at=STAMP,
    )
    assert items[0] == factory.generate(
        InstanceRequest("pixel_7", "first", 0, 0, "Device 001"), created_at=STAMP
    )
    assert items[1] == factory.generate(
        InstanceRequest("pixel_7", "second", 1, 0, "Device 002"), created_at=STAMP
    )
