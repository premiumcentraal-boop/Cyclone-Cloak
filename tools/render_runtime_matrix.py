"""Render per-read proof, distinguishing real transitions from coincidental matches."""
import argparse
import json
from pathlib import Path


def classify(reads):
    a, repeat, b, before, after = [reads.get(name, {}) for name in
                                 ("bound-a", "bound-a-repeat", "bound-b", "unbound-before", "unbound-after")]
    if not all((a, repeat, b, before, after)):
        return "INCOMPLETE"
    if any(r.get('expectation_policy') for r in (a, repeat, b, before, after)):
        if any(r.get('status') == 'FAIL' for r in reads.values()):
            return 'FAIL'
        if any(r.get('status') in {'BLOCKED', 'MISSING'} for r in reads.values()):
            return 'BLOCKED'
        return 'POLICY_ONLY'
    if a["status"] == "OBSERVED" and b["status"] == "OBSERVED":
        return "UNMAPPED"
    if "BLOCKED" in (a["status"], b["status"]):
        return "BLOCKED"
    if a["status"] != "PASS" or b["status"] != "PASS":
        return "FAIL"
    if a.get("actual_sha256") != repeat.get("actual_sha256"):
        return "UNSTABLE"
    if before.get("actual_sha256") != after.get("actual_sha256"):
        return "CONTROL_CHANGED"
    if (a.get("expected_sha256") or a.get("expected")) == (b.get("expected_sha256") or b.get("expected")):
        return "MATCH_ONLY"
    return "VERIFIED"


def render(evidence):
    reads = {}
    for run in evidence["runs"]:
        for row in run["rows"]:
            reads.setdefault(row["id"], {})[run["run"]] = row
    lines = ["# Runtime measurements", "",
             "Generated from `" + evidence.get("artifact", "installed module") + "`. "
             "PASS means a stated equality or diagnostics policy check passed. POLICY_ONLY never establishes hook coverage. VERIFIED below requires equality checks with "
             "distinct profiles, repeat stability and restoration of the unbound control.", "",
             "Requested phases: " + ", ".join(evidence.get("metadata", {}).get("phases", [run["run"] for run in evidence["runs"]])) + ". Missing phases are not run; partial runs cannot establish VERIFIED proof.", "",
             "Phase availability: " + ", ".join(name + "=" + ("run" if any(run["run"] == name for run in evidence["runs"]) else "not run") for name in ("unbound-before", "bound-a", "bound-a-repeat", "bound-b", "unbound-after")) + ".", "",
             "| Read | Profile A | Profile B | Proof | Reason |", "| --- | --- | --- | --- | --- |"]
    for id, runs in reads.items():
        a, b = runs.get("bound-a", {}), runs.get("bound-b", {})
        reason = ", ".join(sorted({a.get("reason", "INCOMPLETE"), b.get("reason", "INCOMPLETE")}))
        lines.append(f"| `{id}` | {a.get('status', 'not run')} | {b.get('status', 'not run')} | {classify(runs)} | {reason} |")
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("evidence", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    args.output.write_text(render(json.loads(args.evidence.read_text())), encoding="utf-8")
