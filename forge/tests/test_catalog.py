"""Exercise every raw catalog row through the existing profile validation flow."""

import copy
import json
import re

import pytest

from cloak_forge.forge import available_templates, forge_profile
from cloak_forge.templates import CATALOG_PATH, load_catalog
from cloak_forge.validate import validate_profile


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
    assert set(phone) == {"id", "label", *BLOCKS}
    profile = forge_profile(phone["label"], "ab" * 32, phone["id"])
    for block in BLOCKS:
        assert profile[block] == phone[block], f"{phone['id']}: {block} changed during loading"
    assert validate_profile(profile) == [], phone["id"]
    assert phone == before
