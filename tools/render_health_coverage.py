"""Generate the documented Health matrix from the app's checked-in coverage asset."""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "android/app/src/main/assets/compatibility-coverage.json"


def render(data):
    def cell(value):
        return str(value).replace("|", "\\|").replace("\n", " ")

    lines = ["# Health compatibility coverage", "",
             "Generated from `android/app/src/main/assets/compatibility-coverage.json`; "
             "the Health tab reads this same local asset. Do not edit this table by hand.", "",
             data["baseline"], "", data["scope_note"], "",
             "Evidence: [device results](runtime-evidence/release-api36-advertising.json), "
             "[per-read matrix](RUNTIME-MEASUREMENTS.md), "
             "[callback tests](../android/module/src/test/java/dev/cyclone/cloak/hook/HookBridgeTest.java).", "",
             "Last verification dates are UTC dates of successful **selected baseline reads**, "
             "not the date of a failed attempt or verification of the local runtime fixes. "
             "Camera/sensor enumeration and coincidental matches do not count as proof.", "",
             "| Area | Implementation status | Test status | Last verified (UTC) | Note |",
             "| --- | --- | --- | --- | --- |"]
    for row in data["rows"]:
        fields = [row["area"], row["implementation_status"], row["test_status"],
                  row["last_verified_utc"] or "not verified", row["note"]]
        lines.append("| " + " | ".join(cell(field) for field in fields) + " |")
    lines += ["", "Regenerate with `uv run -- python tools/render_health_coverage.py`. "
              "The evidence tests in `forge/tests/test_health_coverage.py` reject unsupported "
              "verification claims and a stale generated table."]
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=SOURCE)
    parser.add_argument("--output", type=Path, default=ROOT / "docs/HEALTH-COVERAGE.md")
    args = parser.parse_args()
    args.output.write_text(render(json.loads(args.source.read_text(encoding="utf-8"))), encoding="utf-8")
