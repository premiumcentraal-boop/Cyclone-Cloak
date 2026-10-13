"""Exercise every raw catalog row through the existing profile validation flow."""

import copy
import json
import re

import pytest

from cloak_forge.forge import available_templates, forge_profile
from cloak_forge.templates import CATALOG_PATH, load_catalog
from cloak_forge.validate import validate_profile
from cloak_forge.validate import validate_catalog_record


PHONES = load_catalog()
BLOCKS = ("device", "telephony", "network", "display", "locale")


def test_catalog_schema_ids_and_additions():
    assert json.loads(CATALOG_PATH.read_text(encoding="utf-8"))["schemaVersion"] == 1
    ids = [phone["id"] for phone in PHONES]
    assert len(ids) == len(set(ids)), "duplicate ids would hide entries in TEMPLATES"
    assert all(re.fullmatch(r"[a-z0-9][a-z0-9_-]{0,47}", id) for id in ids)
    assert ids[:3] == ["pixel_4", "pixel_7", "galaxy_s23"]
    assert {"pixel_6", "pixel_6a", "pixel_7a", "pixel_8"} <= set(ids)
    assert set(available_templates()) == set(ids)


@pytest.mark.parametrize("phone", PHONES, ids=lambda phone: phone["id"])
def test_every_catalog_entry_validates_without_findings_or_mutation(phone):
    before = copy.deepcopy(phone)
    assert {"id", "label", *BLOCKS} <= set(phone)
    assert set(phone) <= {"id", "label", *BLOCKS, "camera", "confidence", "classification",
                          "source_confidence", "camera_class", "expected_capability_set", "sensor_class_list",
                          "drm_security_level", "board", "sources"}
    assert validate_catalog_record(phone) == []
    assert phone["confidence"] in {"public reference", "approximated", "needs verification"}
    profile = forge_profile(phone["label"], "ab" * 32, phone["id"])
    for block in BLOCKS:
        assert profile[block] == phone[block], f"{phone['id']}: {block} changed during loading"
    assert validate_profile(profile) == [], phone["id"]
    assert phone == before


ADDITIONS = {"pixel_6_pro", "pixel_7_pro", "pixel_8_pro", "pixel_8a", "pixel_9", "pixel_9_pro", "pixel_9_pro_xl", "pixel_9a"}


def test_additions_have_all_requested_classes_and_catalog_only_metadata():
    new = [phone for phone in PHONES if phone["id"] in ADDITIONS]
    assert len(new) == 8
    assert len(PHONES) >= 15
    classes = {c for phone in new for c in phone["classification"]["device_classes"]}
    assert {"compact", "mainstream", "large-display", "midrange", "older-supported-device", "newer-supported-device"} <= classes
    for phone in new:
        metadata = phone["classification"]
        assert metadata["display_class"] in {"compact", "mainstream", "large-display"}
        assert metadata["hardware_feature_group"]
        assert metadata["camera_class"] in {"dual-rear-front", "triple-rear-front"}
        profile = forge_profile(phone["label"], "ab" * 32, phone["id"])
        assert "classification" not in profile and "confidence" not in profile
        assert profile["telephony"] == {"sim_slot_count": 2, "carrier_name": "T-Mobile", "mcc": "310", "mnc": "260", "network_type": "5G"}
        assert profile["locale"] == {"language": "en", "country": "US", "timezone": "America/New_York"}
