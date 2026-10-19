"""Pure local expectations and evidence binding. No account or platform operations."""

from __future__ import annotations

import copy
import json
import re
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any

RULES = json.loads(
    (
        Path(__file__).resolve().parents[2] / "schema/play-surface.schema.json"
    ).read_text()
)
FORMAT = 1
ACCOUNT_MODES = tuple(RULES["properties"]["account"]["properties"]["mode"]["enum"])
READINESS_STATES = tuple(RULES["$defs"]["readiness"]["properties"]["state"]["enum"])
REASON_CODES = tuple(
    RULES["$defs"]["readiness"]["properties"]["reason_codes"]["items"]["enum"]
)
MAX_BYTES = 131072
MAX_PROFILES = 4096
MESSAGES = {
    "PS_TYPE": "Expected a value of the declared type.",
    "PS_FIELD": "Only declared fields are allowed; credentials are not supported.",
    "PS_REQUIRED": "A required contract field is missing.",
    "PS_VALUE": "Value is outside the allowed format, range or vocabulary.",
    "PS_ACCOUNT": "Account mode and slot assignment disagree.",
    "PS_RECORD": "Readiness state and evidence requirements disagree.",
    "PS_NO_EVIDENCE": "Complete local evidence is not available.",
    "PS_PROFILE_MISMATCH": "Evidence belongs to another profile or profile body.",
    "PS_ARTIFACT_MISMATCH": "Evidence belongs to a different artifact.",
    "PS_EVIDENCE_MISMATCH": "Evidence digest does not match the supplied report.",
    "PS_ACCOUNT_SLOT_CONFLICT": "Active profiles reuse an account slot without shared consent on every assignment.",
    "PS_PACKAGE_CONFLICT": "Package expectations disagree with the supplied local observation.",
    "PS_EXPECTATION_CONFLICT": "Service expectations disagree with the supplied local observation.",
    "PS_EXPECTATION_UNKNOWN": "An expectation or its observation is unknown.",
    "PS_NOT_PRESENT": "No Play surface expectations are recorded.",
    "PS_REVIEW_REQUIRED": "Local account setup needs review.",
    "PS_LOCAL_CHECKS_PASSED": "Supplied local checks passed; no platform verdict is implied.",
}


@dataclass(frozen=True, order=True)
class Issue:
    field: str
    code: str

    @property
    def message(self) -> str:
        return MESSAGES[self.code]


class ContractError(ValueError):
    def __init__(self, issues: list[Issue]):
        self.issues = tuple(sorted(set(issues)))
        super().__init__("; ".join(f"{i.field}: {i.code}" for i in self.issues))


def _check(value: Any, rule: dict, path: str, issues: list[Issue]) -> None:
    def add(code: str) -> None:
        issues.append(Issue(path, code))

    if "anyOf" in rule:
        if value is None:
            return
        _check(value, rule["anyOf"][0], path, issues)
        return
    kind = rule["type"]
    valid = {
        "object": type(value) is dict,
        "array": type(value) is list,
        "string": type(value) is str,
        "integer": type(value) is int,
        "boolean": type(value) is bool,
        "null": value is None,
    }[kind]
    if not valid:
        add("PS_TYPE")
        return
    if ("const" in rule and value != rule["const"]) or (
        "enum" in rule and value not in rule["enum"]
    ):
        add("PS_VALUE")
    if kind == "object":
        props = rule["properties"]
        if set(value) - set(props):
            add("PS_FIELD")  # Never echo unknown keys or rejected values.
        for key in rule["required"]:
            if key not in value:
                issues.append(Issue(path + "." + key, "PS_REQUIRED"))
        for key in sorted(props.keys() & value.keys()):
            _check(value[key], props[key], path + "." + key, issues)
    elif kind == "array":
        if len(value) > rule["maxItems"] or (
            rule.get("uniqueItems")
            and len({json.dumps(v, sort_keys=True) for v in value}) != len(value)
        ):
            add("PS_VALUE")
        for i, item in enumerate(value[: rule["maxItems"]]):
            _check(item, rule["items"], f"{path}[{i}]", issues)
    elif kind == "string":
        if not rule.get("minLength", 0) <= len(value) <= rule.get("maxLength", 100000):
            add("PS_VALUE")
        if "pattern" in rule and re.fullmatch(rule["pattern"], value) is None:
            add("PS_VALUE")
        if rule.get("format") == "date-time":
            try:
                datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ")
            except ValueError:
                add("PS_VALUE")


def validate_surface(value: Any) -> list[Issue]:
    issues: list[Issue] = []
    _check(value, RULES, "play_surface", issues)
    if not issues:
        account = value["account"]
        if (
            account["mode"] in ("dedicated", "managed") and account["slot_id"] is None
        ) or (
            account["mode"] == "none"
            and (account["slot_id"] is not None or account["shared"])
        ):
            issues.append(Issue("play_surface.account.slot_id", "PS_ACCOUNT"))
    return sorted(set(issues))


def validate_profile_section(profile: dict) -> list[Issue]:
    return (
        validate_surface(profile["play_surface"]) if "play_surface" in profile else []
    )


def validate_readiness(value: Any) -> list[Issue]:
    issues: list[Issue] = []
    _check(value, RULES["$defs"]["readiness"], "readiness", issues)
    if not issues:
        ready = value["state"] == "READY"
        complete = (
            value["evidence_digest"] is not None and value["artifact_hash"] is not None
        )
        reasons = value["reason_codes"]
        if (
            (value["state"] != "UNKNOWN" and not complete)
            or (ready and reasons != ["PS_LOCAL_CHECKS_PASSED"])
            or (not ready and (not reasons or "PS_LOCAL_CHECKS_PASSED" in reasons))
        ):
            issues.append(Issue("readiness", "PS_RECORD"))
    return sorted(set(issues))


def _parse(text: str, validator) -> dict:
    def reject_constant(_):
        raise ValueError

    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError
            result[key] = value
        return result

    try:
        if not isinstance(text, str) or len(text.encode("utf-8")) > MAX_BYTES:
            raise ValueError
        value = json.loads(
            text, object_pairs_hook=pairs, parse_constant=reject_constant
        )
        if type(value) is not dict:
            raise ValueError

        # Walk a bounded-depth JSON tree before recursive schema evaluation.
        def depth(v, level=0):
            if level > 32:
                raise ValueError
            for child in (
                v.values() if isinstance(v, dict) else v if isinstance(v, list) else []
            ):
                depth(child, level + 1)

        depth(value)
    except (ValueError, TypeError, RecursionError, UnicodeError):
        raise ContractError([Issue("contract", "PS_VALUE")]) from None
    issues = validator(value)
    if issues:
        raise ContractError(issues)
    return copy.deepcopy(value)


def parse_surface(text: str) -> dict:
    return _parse(text, validate_surface)


def parse_readiness(text: str) -> dict:
    return _parse(text, validate_readiness)


def encode_surface(value: dict) -> str:
    return json.dumps(
        parse_surface(json.dumps(value)), sort_keys=True, separators=(",", ":")
    )


def encode_readiness(value: dict) -> str:
    return json.dumps(
        parse_readiness(json.dumps(value)), sort_keys=True, separators=(",", ":")
    )


def account_slot_conflicts(assignments: list[dict]) -> list[Issue]:
    """Each input has profile_id, active, and a profile object. No saved data is changed."""
    if len(assignments) > MAX_PROFILES:
        raise ContractError([Issue("assignments", "PS_VALUE")])
    groups: dict[str, list[tuple[int, bool]]] = {}
    seen = set()
    for i, item in enumerate(assignments):
        if (
            not isinstance(item, dict)
            or set(item) != {"profile_id", "active", "profile"}
            or not isinstance(item["profile_id"], str)
            or re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._:-]{0,95}", item["profile_id"])
            is None
            or item["profile_id"] in seen
            or type(item["active"]) is not bool
            or not isinstance(item["profile"], dict)
        ):
            raise ContractError([Issue("assignments", "PS_VALUE")])
        seen.add(item["profile_id"])
        issues = validate_profile_section(item["profile"])
        if issues:
            raise ContractError(issues)
        surface = item["profile"].get("play_surface")
        if surface and item["active"] and surface["account"]["slot_id"] is not None:
            a = surface["account"]
            groups.setdefault(a["slot_id"], []).append((i, a["shared"]))
    return sorted(
        Issue(
            f"assignments[{i}].play_surface.account.slot_id", "PS_ACCOUNT_SLOT_CONFLICT"
        )
        for members in groups.values()
        if len(members) > 1 and not all(shared for _, shared in members)
        for i, _ in members
    )


def service_conflicts(expected: dict, observed: dict | None) -> list[Issue]:
    """Compare supplied descriptions; never reads packages or queries Google."""
    issues = validate_surface(expected)
    if issues:
        raise ContractError(issues)
    if observed is None:
        return [Issue("play_surface.services", "PS_NO_EVIDENCE")]
    malformed: list[Issue] = []
    _check(observed, RULES["properties"]["services"], "observed.services", malformed)
    if malformed:
        raise ContractError(malformed)
    result = []
    for key, wanted in expected["services"].items():
        actual = observed[key]
        path = "play_surface.services." + key
        if isinstance(wanted, dict):
            unknown = "unknown" in (wanted["state"], actual["state"])
            declared = [k for k in ("package_name", "version_name") if k in wanted]
            if (not unknown and wanted["state"] != actual["state"]) or any(
                k in actual and wanted[k] != actual[k] for k in declared
            ):
                result.append(Issue(path, "PS_PACKAGE_CONFLICT"))
            elif unknown or any(k not in actual for k in declared):
                result.append(Issue(path, "PS_EXPECTATION_UNKNOWN"))
        elif "unknown" in (wanted, actual):
            result.append(Issue(path, "PS_EXPECTATION_UNKNOWN"))
        elif wanted != actual:
            result.append(Issue(path, "PS_EXPECTATION_CONFLICT"))
    return sorted(result)


def bind_readiness(
    record: dict,
    *,
    profile_id: str,
    profile_hash: str,
    artifact_hash: str | None,
    evidence_digest: str | None,
) -> dict:
    """Downgrade unbound records to UNKNOWN. Never promotes a state or asserts a verdict."""
    result = parse_readiness(json.dumps(record))
    reasons = []
    if result["profile_id"] != profile_id or result["profile_hash"] != profile_hash:
        reasons.append("PS_PROFILE_MISMATCH")
    if result["artifact_hash"] != artifact_hash:
        reasons.append("PS_ARTIFACT_MISMATCH")
    if result["evidence_digest"] != evidence_digest:
        reasons.append("PS_EVIDENCE_MISMATCH")
    if (
        evidence_digest is None
        or artifact_hash is None
        or result["evidence_digest"] is None
        or result["artifact_hash"] is None
    ):
        reasons.append("PS_NO_EVIDENCE")
    if reasons:
        result["state"] = "UNKNOWN"
        result["reason_codes"] = sorted(set(reasons))
    return result
