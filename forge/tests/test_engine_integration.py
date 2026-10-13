import copy
import json
from pathlib import Path

import pytest
from cloak_forge.engine import generate, generate_unique, retry_seed, check_uniqueness

REQUEST = {"name": "Unique", "seed": "ab" * 32, "template": "pixel_7"}


def test_empty_library_keeps_old_direct_body():
    assert generate_unique(REQUEST, [])["profile"] == generate(REQUEST)["profile"]


def test_same_id_and_multiple_identifier_conflicts_retry_deterministically():
    first = generate(REQUEST)["profile"]
    second = generate({**REQUEST, "seed": retry_seed(REQUEST["seed"], 2)})["profile"]
    library = [first, second]
    before = copy.deepcopy(library)
    a = generate_unique(REQUEST, library)
    assert a == generate_unique(REQUEST, reversed(library))
    assert a["metadata"]["attempt_count"] == 3
    assert a["metadata"]["chosen_seed"] == retry_seed(REQUEST["seed"], 3)
    assert not check_uniqueness(a["profile"], library)
    assert library == before


def test_one_case_insensitive_conflict_retries():
    existing = generate({**REQUEST, "name": "other"})["profile"]
    existing["identifiers"] = {"serial": existing["identifiers"]["serial"].lower()}
    assert generate_unique(REQUEST, [existing])["metadata"]["attempt_count"] == 2


def test_exhaustion_leaves_library_untouched():
    library = [generate({**REQUEST, "seed": REQUEST["seed"] if attempt == 1 else retry_seed(REQUEST["seed"], attempt)})["profile"]
               for attempt in range(1, 129)]
    before = copy.deepcopy(library)
    with pytest.raises(ValueError, match="128 attempts"):
        generate_unique(REQUEST, library)
    assert library == before


def test_shared_unique_vector():
    vector = json.loads((Path(__file__).parent / "vectors" / "engine-unique.json").read_text())
    assert generate_unique(vector["request"], vector["library"]) == vector["bundle"]


@pytest.mark.parametrize("attempt", [0, 1, 129, True, 2.0])
def test_invalid_retry_attempt(attempt):
    with pytest.raises(ValueError):
        retry_seed(REQUEST["seed"], attempt)
