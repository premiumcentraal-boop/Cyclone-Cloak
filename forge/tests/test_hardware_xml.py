"""Offline, synthetic importer acceptance and rejection vectors."""

import copy
import hashlib
import json
from pathlib import Path

import pytest
from cloak_forge.catalog_provenance import (
    ImportRejected,
    canonical,
    digest,
    load_json,
    validate_manifest,
    validate_source,
)
from cloak_forge.device_factory import DeviceFactory, FactoryError, InstanceRequest
from cloak_forge.hardware_xml import (
    FIELD_TABLE,
    attach_build_properties,
    attach_review,
    completion,
    parse_hardware_xml,
    promote,
    resolve,
    validate_candidate,
)
from cloak_forge.prop_parse import parse_build_prop
from cloak_forge.validate import validate_profile

FIXTURES = Path(__file__).parent / "fixtures/catalog-import"
SOURCES = json.loads((FIXTURES / "sources.json").read_text())
XML = (FIXTURES / "hardware.xml").read_bytes()
PROPS = (FIXTURES / "matching.build.prop").read_bytes()
REVIEW = (FIXTURES / "synthetic-completion.json").read_bytes()
STAMP = "2026-10-18T12:00:00+00:00"


def source(raw, role, id="test", **overrides):
    return dict(
        id=id,
        local_label="Self-authored synthetic test",
        sha256=hashlib.sha256(raw).hexdigest(),
        license="CC0-1.0",
        license_reviewed=True,
        notice="Synthetic test only; not device evidence.",
        observed_on="2026-10-18",
        format={
            "hardware": "hardware-xml",
            "build": "build-prop",
            "review": "review-json",
        }[role],
        role=role,
        **overrides,
    )


def hardware(raw=XML):
    return parse_hardware_xml(raw, source(raw, "hardware", "hardware"))


def built(raw=PROPS):
    return attach_build_properties(hardware(), raw, source(raw, "build", "build"))


def complete():
    return attach_review(built(), REVIEW, source(REVIEW, "review", "review"))


def reviewed(manifest, rows, id="correction"):
    raw = canonical({"facts": rows})
    return attach_review(manifest, raw, source(raw, "review", id))


def row(path, value, *, availability="known", resolves=()):
    return {
        "path": path,
        "value": value,
        "availability": availability,
        "confidence": "reviewed",
        "evidence": "Explicit self-authored correction",
        "resolves": list(resolves),
    }


def test_complete_synthetic_promotion_and_checked_in_candidate():
    status = completion(complete())
    assert status["complete"] and not status["conflicts"] and not status["missing"]
    candidate = promote(complete(), reviewer="Synthetic example review only")
    assert validate_candidate(candidate)["id"] == "synthetic_pixel7_import"
    stored = load_json((FIXTURES / "synthetic-candidate.json").read_bytes())
    assert {k: v for k, v in validate_candidate(stored).items() if k != "sources"} == {
        k: v for k, v in candidate["template"].items() if k != "sources"
    }
    assert candidate["platform_trust"] == "unknown"


def test_hardware_only_is_incomplete_and_does_not_invent_facts():
    draft = hardware()
    status = completion(draft)
    assert not status["complete"]
    assert {
        "BUILD_PROPERTIES_REQUIRED",
        "EXPLICIT_REVIEW_REQUIRED",
        "INCOMPLETE_FACTS",
    } <= set(status["reasons"])
    assert "device.first_api_level" in status["missing"]
    assert "device.fingerprint" not in resolve(draft)["fields"]
    assert "camera.cameras" not in resolve(draft)["fields"]
    with pytest.raises(ImportRejected):
        promote(draft, reviewer="Test")


def test_properties_do_not_replace_review_requirement():
    assert "EXPLICIT_REVIEW_REQUIRED" in completion(built())["reasons"]


@pytest.mark.parametrize(
    "raw,code",
    [
        (b"<", "XML_MALFORMED"),
        (b"x" * 256001, "INPUT_SIZE"),
        (
            b'<!DOCTYPE device [<!ENTITY x "boom">]><device>&x;</device>',
            "XML_DECLARATION_FORBIDDEN",
        ),
        (XML.replace(b"Pixel 7", b"\x00Pixel 7"), "INPUT_ENCODING"),
        (XML.replace(b"UTF-8", b"UTF-16"), "XML_MALFORMED"),
        (
            XML.replace(
                b"http://schemas.android.com/sdk/devices/6", b"https://evil.invalid"
            ),
            "XML_NAMESPACE",
        ),
        (
            XML.replace(b"<d:mic>true</d:mic>", b"<d:execute>make all</d:execute>"),
            "XML_UNSUPPORTED_FIELD",
        ),
        (
            XML.replace(b"<d:mic>true</d:mic>", b'<d:mic exec="yes">true</d:mic>'),
            "XML_ATTRIBUTE",
        ),
        (
            XML.replace(
                b"<d:mic>true</d:mic>", b"<d:mic>true</d:mic><d:mic>false</d:mic>"
            ),
            "DUPLICATE_FIELD",
        ),
        (XML.replace(b"<d:hardware>", b"<d:hardware>script"), "XML_MIXED_CONTENT"),
        (XML.replace(b"Wifi Bluetooth NFC", b"Wifi Wifi"), "DUPLICATE_FIELD"),
        (
            XML.replace(b"<d:flash>true</d:flash>", b"<d:flash>yes</d:flash>"),
            "XML_BOOLEAN",
        ),
        (XML.replace(b"420dpi", b"physical420"), "XML_DENSITY"),
        (XML.replace(b'unit="GiB"', b'unit="unknown"'), "XML_UNIT"),
        (XML.replace(b">1080<", b">1080.5<"), "XML_VALUE"),
    ],
    ids=lambda v: v if isinstance(v, str) else "xml",
)
def test_narrow_bounded_xml_rejection(raw, code):
    with pytest.raises(ImportRejected) as error:
        hardware(raw)
    assert error.value.code == code


def test_multiple_device_selection_is_explicit():
    raw = XML.replace(
        b"</d:devices>",
        XML[XML.index(b"  <d:device>") : XML.index(b"</d:devices>")].replace(
            b"synthetic-pixel7", b"other-device"
        )
        + b"</d:devices>",
    )
    with pytest.raises(ImportRejected, match="DEVICE_SELECTION_REQUIRED"):
        hardware(raw)
    result = parse_hardware_xml(raw, source(raw, "hardware"), device_id="other-device")
    assert resolve(result)["fields"]["device.model"]["value"] == "Pixel 7"


@pytest.mark.parametrize(
    "density,value",
    [(b"mdpi", 160), (b"xhdpi", 320), (b"420dpi", 420), (b"tvdpi", 213)],
)
def test_logical_density_not_physical_dpi(density, value):
    fields = resolve(hardware(XML.replace(b"420dpi", density)))["fields"]
    assert fields["display.density"]["value"] == value
    assert fields["hardware.physical_xdpi"]["value"] == 416.5
    assert fields["hardware.diagonal_inches"]["value"] == 6.3


def test_physical_dpi_cannot_fill_absent_logical_density():
    draft = hardware(XML.replace(b"<d:pixel-density>420dpi</d:pixel-density>", b""))
    assert "display.density" not in resolve(draft)["fields"]
    assert "display.density" in completion(draft)["missing"]


@pytest.mark.parametrize("token", [b"nodpi", b"anydpi"])
def test_unavailable_density_preserved(token):
    field = resolve(hardware(XML.replace(b"420dpi", token)))["fields"][
        "display.density"
    ]
    assert field["availability"] == "unavailable" and field["value"] is None


@pytest.mark.parametrize(
    "xml,value",
    [
        (b'<d:ram unit="GiB">8</d:ram>', 8 * 1024**3),
        (b'<d:ram unit="MiB">8192</d:ram>', 8 * 1024**3),
        (b'<d:ram unit="GB">8</d:ram>', 8_000_000_000),
    ],
)
def test_memory_units_have_explicit_binary_decimal_semantics(xml, value):
    fields = resolve(hardware(XML.replace(b'<d:ram unit="GiB">8</d:ram>', xml)))[
        "fields"
    ]
    assert fields["hardware.ram_bytes"]["value"] == value


@pytest.mark.parametrize(
    "key", [b"ro.product.first_api_level", b"ro.board.first_api_level"]
)
def test_missing_first_api_remains_unknown(key):
    raw = b"\n".join(
        line
        for line in PROPS.splitlines()
        if not line.startswith(b"ro.product.first_api_level=")
    )
    fields = resolve(built(raw))["fields"]
    assert fields["device.sdk_int"]["value"] == int(
        parse_build_prop(PROPS.decode())["ro.build.version.sdk"]
    )
    assert fields["device.first_api_level"]["availability"] == "unknown"
    assert fields["device.first_api_level"]["value"] is None
    assert "device.first_api_level" in completion(built(raw))["missing"]
    raw += b"\n" + key + b"=33\n"
    assert resolve(built(raw))["fields"]["device.first_api_level"]["value"] == 33


@pytest.mark.parametrize(
    "old,new",
    [
        (b"ro.product.model=Pixel 7", b"ro.product.model=Pixel 8"),
        (b"ro.product.manufacturer=Google", b"ro.product.manufacturer=Sony"),
    ],
)
def test_build_mismatch_is_rejected(old, new):
    with pytest.raises(ImportRejected, match="BUILD_IDENTITY_MISMATCH"):
        built(PROPS.replace(old, new))


def test_build_must_supply_both_match_keys():
    raw = b"\n".join(
        line for line in PROPS.splitlines() if not line.startswith(b"ro.product.model=")
    )
    with pytest.raises(ImportRejected, match="BUILD_MATCH_INCOMPLETE"):
        built(raw)


def test_getprop_format_uses_existing_parser():
    props = parse_build_prop(PROPS.decode())
    text = "\n".join(f"[{k}]: [{v}]" for k, v in props.items()).encode()
    assert {p: f["value"] for p, f in resolve(built(text))["fields"].items()} == {
        p: f["value"] for p, f in resolve(built())["fields"].items()
    }


def test_property_duplicates_and_alias_conflict_rejected():
    for extra, code in [
        (b"\nro.product.model=Pixel 7", "DUPLICATE_PROPERTY"),
        (b"\nro.board.first_api_level=31", "PROPERTY_ALIAS_CONFLICT"),
    ]:
        with pytest.raises(ImportRejected, match=code):
            built(PROPS + extra)


def test_conflict_blocks_until_explicit_review_names_disagreeing_evidence():
    raw = PROPS.replace(b"ro.sf.lcd_density=420", b"ro.sf.lcd_density=440")
    draft = built(raw)
    assert resolve(draft)["conflicts"] == ["display.density"]
    assert not completion(draft)["complete"]
    implicit = reviewed(draft, [row("display.density", 440)])
    assert resolve(implicit)["conflicts"] == ["display.density"]
    old_ids = [
        f["id"]
        for f in draft["facts"]
        if f["path"] == "display.density" and f["value"] == 420
    ]
    explicit = reviewed(draft, [row("display.density", 440, resolves=old_ids)])
    assert not resolve(explicit)["conflicts"]
    assert resolve(explicit)["fields"]["display.density"]["source_id"] == "correction"


def test_agreeing_source_priority_is_stable():
    field = resolve(built())["fields"]["display.density"]
    assert field["source_id"] == "build"
    draft = reviewed(built(), [row("display.density", 420)])
    assert resolve(draft)["fields"]["display.density"]["source_id"] == "correction"
    shuffled = copy.deepcopy(draft)
    shuffled["facts"].reverse()
    assert resolve(shuffled) == resolve(draft)


@pytest.mark.parametrize(
    "change,code",
    [
        ({"license": "unknown"}, "SOURCE_METADATA"),
        ({"notice": ""}, "SOURCE_METADATA"),
        ({"sha256": "bad"}, "SOURCE_METADATA"),
        ({"observed_on": "2026-02-30"}, "OBSERVATION_DATE"),
        ({"format": []}, "SOURCE_METADATA"),
        ({"role": {}}, "SOURCE_METADATA"),
    ],
)
def test_provenance_metadata_rejection(change, code):
    with pytest.raises(ImportRejected, match=code):
        validate_source({**source(XML, "hardware"), **change})


@pytest.mark.parametrize(
    "url,accepted",
    [
        (
            "https://android.googlesource.com/platform/tools/base/+/eac8f291/devices.xml",
            True,
        ),
        ("https://github.com/jaredrummler/AndroidDeviceNames", True),
        ("https://github.com/jaredrummler/AndroidDeviceNames-evil", False),
        ("https://github.com.evil.test/jaredrummler/AndroidDeviceNames", False),
        ("http://github.com/jaredrummler/AndroidDeviceNames", False),
        ("https://token@github.com/jaredrummler/AndroidDeviceNames", False),
        ("file:///tmp/device.xml", False),
    ],
)
def test_bound_source_set(url, accepted):
    item = source(XML, "hardware")
    del item["local_label"]
    item["url"] = url
    if accepted:
        assert validate_source(item) == item
    else:
        with pytest.raises(ImportRejected, match="SOURCE_NOT_APPROVED"):
            validate_source(item)


def test_source_hashes_and_roles_bound_to_input():
    with pytest.raises(ImportRejected, match="SOURCE_HASH_MISMATCH"):
        parse_hardware_xml(XML + b" ", source(XML, "hardware"))
    with pytest.raises(ImportRejected, match="SOURCE_ROLE"):
        parse_hardware_xml(XML, source(XML, "review"))
    for path in ("../unapproved", "%2e%2e/unapproved"):
        record = source(XML, "hardware")
        record.pop("local_label")
        record["url"] = "https://github.com/jaredrummler/AndroidDeviceNames/" + path
        with pytest.raises(ImportRejected, match="SOURCE_NOT_APPROVED"):
            validate_source(record)


def test_duplicate_facts_unknown_paths_and_fact_tampering_rejected():
    allowed = set(load_json(FIELD_TABLE.read_bytes())["paths"])
    draft = hardware()
    for mutation, code in [
        (lambda d: d["facts"].append(copy.deepcopy(d["facts"][0])), "DUPLICATE_FIELD"),
        (lambda d: d["facts"][0].update(value="tamper"), "FACT_HASH_MISMATCH"),
        (lambda d: d["facts"][0].update(path="runtime.execute"), "FACT_METADATA"),
    ]:
        altered = copy.deepcopy(draft)
        mutation(altered)
        with pytest.raises(ImportRejected, match=code):
            validate_manifest(altered, allowed)


def test_missing_and_unavailable_facts_stay_incomplete():
    for availability in ("unknown", "unavailable"):
        draft = reviewed(built(), [row("id", None, availability=availability)])
        assert "id" in completion(draft)["missing"]
        assert resolve(draft)["fields"]["id"]["availability"] == availability


@pytest.mark.parametrize(
    "path,value,code",
    [
        (
            "classes.play_services_expectations.certification",
            "certified",
            "TRUST_MUST_REMAIN_UNKNOWN",
        ),
        (
            "classes.play_services_expectations.attestation",
            "pass",
            "TRUST_MUST_REMAIN_UNKNOWN",
        ),
        ("firmware_binding.fingerprint", "wrong", "FIRMWARE_BINDING_MISMATCH"),
        ("reality.memory.values.ram_gib", 16, "HARDWARE_COMPLETION_CONFLICT"),
        ("reality.compute.values.gpu", "invented", "HARDWARE_COMPLETION_CONFLICT"),
        ("reality.panel.values.width", 999, "PROFILE_COHERENCE"),
    ],
)
def test_completion_cannot_silently_invent_or_contradict_hardware(path, value, code):
    draft = complete()
    previous = [f["id"] for f in draft["facts"] if f["path"] == path]
    modified = reviewed(draft, [row(path, value, resolves=previous)])
    assert code in completion(modified)["reasons"]
    with pytest.raises(ImportRejected):
        promote(modified, reviewer="Test")


def test_candidate_tampering_and_template_provenance_mismatch_rejected():
    candidate = promote(complete(), reviewer="Synthetic review")
    candidate["template"]["device"]["model"] = "Wrong"
    with pytest.raises(ImportRejected, match="CANDIDATE_DIGEST"):
        validate_candidate(candidate)
    candidate["digest"] = digest({k: v for k, v in candidate.items() if k != "digest"})
    with pytest.raises(ImportRejected, match="CANDIDATE_PROVENANCE_MISMATCH"):
        validate_candidate(candidate)


def test_deterministic_factory_generation_and_distinct_instances():
    factory = DeviceFactory.from_imported_candidates(
        [promote(complete(), reviewer="Synthetic review")]
    )
    request = InstanceRequest("synthetic_pixel7_import", "local-seed")
    first = factory.generate(request, created_at=STAMP)
    assert first == factory.generate(request, created_at=STAMP)
    batch = factory.batch(request.template_id, "batch", 10, created_at=STAMP)
    for key in first["profile"]["identifiers"]:
        assert len({b["profile"]["identifiers"][key] for b in batch}) == 10
    assert all(
        not [f for f in validate_profile(b["profile"]) if f.severity == "error"]
        for b in batch
    )
    assert first["profile"]["integrity"]["verdict"] == "unknown"
    assert "provenance" not in first["profile"]
    with pytest.raises(FactoryError, match="COLLISION"):
        factory.generate(request, existing=[first["profile"]])


def test_imported_snapshot_defensive_and_canonical_factory_unchanged():
    original = DeviceFactory().catalog()
    candidate = promote(complete(), reviewer="Test")
    factory = DeviceFactory.from_imported_candidates([candidate])
    candidate["template"]["device"]["model"] = "Mutation"
    view = factory.catalog()
    view[0]["device"]["model"] = "Mutation"
    assert factory.catalog()[0]["device"]["model"] == "Pixel 7"
    assert DeviceFactory().catalog() == original
    assert len(original) == 12


def test_json_duplicate_keys_and_oversize_are_rejected():
    for raw in (b'{"a":1,"a":2}', b"x" * 2_000_001, b'{"v": NaN}'):
        with pytest.raises(ImportRejected):
            load_json(raw)
    with pytest.raises(ImportRejected, match="MALFORMED_JSON"):
        load_json("\ud800")
    with pytest.raises(ImportRejected, match="INPUT_ENCODING"):
        parse_hardware_xml("\ud800", source(XML, "hardware"))


def test_licensing_review_is_required_even_if_fields_are_complete():
    manifest = complete()
    manifest["sources"][0]["license_reviewed"] = False
    assert "SOURCE_LICENSE_REVIEW_REQUIRED" in completion(manifest)["reasons"]
    with pytest.raises(ImportRejected, match="SOURCE_LICENSE_REVIEW_REQUIRED"):
        promote(manifest, reviewer="Test")


def test_imported_catalog_rejects_duplicate_or_empty_candidates():
    candidate = promote(complete(), reviewer="Test")
    for candidates, reason in [
        ([], "EMPTY_CATALOG"),
        ([candidate, candidate], "DUPLICATE_TEMPLATE"),
    ]:
        with pytest.raises(FactoryError, match=reason):
            DeviceFactory.from_imported_candidates(candidates)


def test_class_metadata_cannot_disagree_with_completed_facts():
    manifest = complete()
    path = "classes.ram_storage_class"
    old = [f["id"] for f in manifest["facts"] if f["path"] == path]
    manifest = reviewed(manifest, [row(path, "16GiB/256GiB", resolves=old)])
    assert "TEMPLATE_CLASS_CONFLICT" in completion(manifest)["reasons"]


def test_unknown_source_registry_cannot_promote_hardware_as_verified():
    registry = load_json(
        (FIXTURES.parents[3] / "catalog/import-sources-v1.json").read_bytes()
    )
    assert len(registry["sources"]) == 8
    for item in registry["sources"]:
        validate_source(item)
        assert item["role"] == "reference" and not item["license_reviewed"]


def test_unknown_and_unavailable_review_fact_cannot_claim_a_value():
    for availability in ("unknown", "unavailable"):
        with pytest.raises(ImportRejected, match="FACT_METADATA"):
            reviewed(built(), [row("id", "invented", availability=availability)])


def test_review_input_is_plain_json_not_executable_content():
    for raw in (
        b"import os; os.system('true')",
        b'{"facts": [], "execute": "make all"}',
    ):
        with pytest.raises(ImportRejected):
            attach_review(built(), raw, source(raw, "review"))


def test_boolean_and_numeric_evidence_are_not_silently_equated():
    draft = reviewed(built(), [row("hardware.microphone", 1)])
    assert "hardware.microphone" in resolve(draft)["conflicts"]


def test_malformed_candidate_collections_fail_closed():
    for value in (None, {}, "candidate.json", [None]):
        with pytest.raises((FactoryError, ImportRejected)):
            DeviceFactory.from_imported_candidates(value)


def test_bad_schema_fact_does_not_disclose_rejected_contents():
    secret = "PRIVATE_TEST_SENTINEL"
    with pytest.raises(ImportRejected) as error:
        reviewed(built(), [row("device.secret", secret)])
    assert error.value.code == "FACT_METADATA"
    assert secret not in str(error.value)


def test_duplicate_json_reason_is_machine_readable():
    with pytest.raises(ImportRejected) as error:
        load_json(b'{"facts": [], "facts": []}')
    assert error.value.code == "DUPLICATE_JSON_KEY"


def test_checked_in_fixture_hashes_and_pipeline_are_reproducible():
    draft = parse_hardware_xml(XML, SOURCES[0])
    draft = attach_build_properties(draft, PROPS, SOURCES[1])
    draft = attach_review(draft, REVIEW, SOURCES[2])
    assert promote(draft, reviewer="Synthetic example review only") == load_json(
        (FIXTURES / "synthetic-candidate.json").read_bytes()
    )
