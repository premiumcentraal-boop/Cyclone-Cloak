"""Pure engine facade. No UI, storage, connector or publication dependencies."""
from __future__ import annotations

from dataclasses import asdict
import hashlib
from typing import Any, Iterable, Mapping

from .coverage import APPLIED, INTERNAL, PENDING
from .forge import forge_profile
from .templates import TEMPLATES
from .pif import build_pif
from .validate import Finding, summarize, validate_profile

ENGINE_VERSION = "1"
IDENTIFIER_FIELDS = ("android_id", "advertising_id", "app_set_id", "serial", "widevine_id",
                     "mac", "bt_mac", "imei_primary", "imei_secondary", "sim_serial", "gsf_id")
TEST_STATES = {"VERIFIED", "PASS", "FAIL", "BLOCKED", "OBSERVED", "MATCH_ONLY", "UNMAPPED",
               "INCOMPLETE", "not verified"}


def generate(request: Mapping[str, Any]) -> dict[str, Any]:
    version = request.get("schema_version", "0.3")
    if version not in ("0.2", "0.3"):
        raise ValueError("schema_version must be 0.2 or 0.3")
    profile = forge_profile(request["name"], request["seed"], request["template"])
    profile["schema_version"] = version
    return {"profile": profile, "posture": derive_posture(profile),
            "findings": [asdict(f) for f in validate(profile)],
            "uniqueness": [asdict(f) for f in check_uniqueness(profile, [])],
            "metadata": {"engine_version": ENGINE_VERSION, "schema_version": version,
                         "template": request["template"]}}


def validate(profile: Mapping[str, Any]) -> list[Finding]:
    return validate_profile(profile)


def retry_seed(seed: str, attempt: int) -> str:
    import re
    if not isinstance(seed, str) or not re.fullmatch(r"[0-9a-f]{64}", seed) or type(attempt) is not int or not 2 <= attempt <= 128:
        raise ValueError("Invalid retry seed or attempt")
    return hashlib.sha256(f"cloak-engine-unique-v1:{seed}:{attempt}".encode("utf-8")).hexdigest()


def generate_unique(request: Mapping[str, Any], existingProfiles: Iterable[Mapping[str, Any]]) -> dict[str, Any]:
    """Deterministic bounded retry; candidate IDs are never treated as an edit of a saved record."""
    library = list(existingProfiles)
    for attempt in range(1, 129):
        seed = request["seed"] if attempt == 1 else retry_seed(request["seed"], attempt)
        bundle = generate({**request, "seed": seed})
        profile = bundle["profile"]
        if any(f.severity == "error" for f in validate(profile)):
            raise ValueError("Generated profile has coherence errors.")
        # Copy shells to prevent the edit-oriented own-ID exclusion, without touching saved data.
        index = [{**p, "id": f"saved-{i}"} for i, p in enumerate(library)]
        if any(p.get("id") == profile["id"] for p in library) or check_uniqueness(profile, index):
            continue
        template = TEMPLATES[request["template"]]
        bundle["metadata"].update(chosen_seed=seed, attempt_count=attempt,
            source_confidence=template.get("source_confidence", template.get("confidence", "needs verification")))
        return bundle
    raise ValueError("Could not generate a unique profile after 128 attempts. Nothing was saved.")


def derive_posture(profile: Mapping[str, Any]) -> dict[str, str]:
    return build_pif(profile)


def check_uniqueness(profile: Mapping[str, Any], library: Iterable[Mapping[str, Any]]) -> list[Finding]:
    """Mirror the existing Kotlin derived index, including own-ID exclusion."""
    identifiers = profile.get("identifiers")
    if not isinstance(identifiers, Mapping):
        return []
    saved = list(library)
    findings = []
    for field in sorted(IDENTIFIER_FIELDS):
        value = identifiers.get(field)
        if not isinstance(value, str) or not value.strip():
            continue
        owners = set()
        for other in saved:
            if not isinstance(other, Mapping) or other.get("id", "") == profile.get("id", ""):
                continue
            ids = other.get("identifiers")
            candidate = ids.get(field) if isinstance(ids, Mapping) else None
            if isinstance(candidate, str) and candidate.strip() and candidate.lower() == value.lower():
                owners.add(other.get("id", ""))
        if owners:
            path = f"identifiers.{field}"
            findings.append(Finding("warning", "DUPLICATE_IDENTIFIER",
                f"{path}: this value already exists in {len(owners)} other saved profile(s). "
                "Regenerate it or explicitly keep the duplicate.", path))
    return list(summarize(findings).findings)


def score_coverage(profile: Mapping[str, Any], device_report: Mapping[str, str]) -> dict[str, Any]:
    """Field-path report mapping; source implementation never implies verification."""
    def leaves(value, prefix=""):
        for key, item in value.items():
            path = f"{prefix}.{key}" if prefix else key
            if isinstance(item, Mapping):
                yield from leaves(item, path)
            else:
                yield path
    rows = []
    for field in sorted(leaves(profile)):
        implementation = "APPLIED" if field in APPLIED else "INTERNAL" if field in INTERNAL else "PENDING"
        status = device_report.get(field, "not verified")
        if status not in TEST_STATES:
            raise ValueError(f"Unsupported test status for {field}")
        rows.append({"field": field, "implementation": implementation, "test_status": status})
    return {"rows": rows, "summary": {
        state: sum(row["implementation"] == state for row in rows)
        for state in ("APPLIED", "PENDING", "INTERNAL")}}
