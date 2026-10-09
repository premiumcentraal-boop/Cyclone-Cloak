"""Realistic device templates used by the Cloak Forge.

The phones live in `catalog/phones.json` at the repository root, shared with the Android app (which packages the
same file as an asset), so the two forges can never drift apart.
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

CATALOG_PATH = Path(__file__).resolve().parents[2] / "catalog" / "phones.json"

_TEMPLATE_BLOCKS = ("device", "telephony", "network", "display", "locale")


def load_catalog(path: Path = CATALOG_PATH) -> list[dict[str, Any]]:
    """The catalog's phones, in file order."""
    return json.loads(path.read_text(encoding="utf-8"))["phones"]


TEMPLATES: dict[str, dict[str, Any]] = {
    phone["id"]: {block: phone[block] for block in _TEMPLATE_BLOCKS} for phone in load_catalog()
}
