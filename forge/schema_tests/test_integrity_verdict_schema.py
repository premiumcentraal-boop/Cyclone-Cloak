"""Synthetic contract checks only; no platform verdicts or credentials."""

import copy
import json
from pathlib import Path

import pytest
from jsonschema import Draft202012Validator

SCHEMA = json.loads(
    (
        Path(__file__).resolve().parents[2] / "schema/integrity-verdict.schema.json"
    ).read_text()
)
PAYLOAD = {
    "format": 1,
    "provider": "synthetic.backend",
    "request_id": "synthetic-request",
    "request_hash": "c" * 64,
    "profile_id": "synthetic-profile",
    "profile_hash": "a" * 64,
    "artifact_hash": "b" * 64,
    "android_user_id": 17,
    "timestamp": "2026-10-20T10:00:00Z",
    "decoded_at": "2026-10-20T10:00:01Z",
    "app_recognition": "PLAY_RECOGNIZED",
    "app_licensing": "LICENSED",
    "device_recognition": ["MEETS_DEVICE_INTEGRITY"],
}


def validator():
    return Draft202012Validator(
        {
            **SCHEMA,
            "$ref": "#/$defs/payload",
            "required": [],
            "properties": {},
            "additionalProperties": True,
        }
    )


def test_schema_and_synthetic_payload():
    Draft202012Validator.check_schema(SCHEMA)
    validator().validate(PAYLOAD)


@pytest.mark.parametrize("field", list(PAYLOAD))
def test_each_payload_field_is_required(field):
    value = copy.deepcopy(PAYLOAD)
    del value[field]
    assert list(validator().iter_errors(value))


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("raw_token", "forbidden"),
        ("credentials", {}),
        ("format", 2),
        ("android_user_id", "17"),
        ("app_recognition", "FUTURE_VALUE"),
        ("device_recognition", ["MEETS_DEVICE_INTEGRITY"] * 2),
        ("profile_hash", "not-a-hash"),
    ],
)
def test_unsupported_and_private_fields_rejected(field, value):
    payload = copy.deepcopy(PAYLOAD)
    payload[field] = value
    assert list(validator().iter_errors(payload))


def test_unsigned_payload_is_not_an_authenticated_envelope():
    assert list(Draft202012Validator(SCHEMA).iter_errors(PAYLOAD))
