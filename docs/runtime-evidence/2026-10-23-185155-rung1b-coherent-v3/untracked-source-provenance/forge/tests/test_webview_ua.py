"""Chrome compatibility plus exact Android WebView UA syntax and coherence."""
import json
from pathlib import Path

import jsonschema
import pytest

from cloak_forge.validate import validate_profile
from test_validate import make_profile


@pytest.mark.parametrize("suffix", ["", "; wv", " Build/TQ3A.230805.001", " Build/TQ3A.230805.001; wv"])
def test_webview_user_agent_preserved_and_schema_aligned(suffix):
    profile = make_profile()
    value = (f"Mozilla/5.0 (Linux; Android 13; Pixel 7{suffix}) AppleWebKit/537.36 "
             "(KHTML, like Gecko) Version/4.0 Chrome/155.0.8059.43 Mobile Safari/537.36")
    profile["ua"] = {"value": value}
    assert not [f for f in validate_profile(profile) if f.severity == "error"]
    schema = json.loads((Path(__file__).parents[2] / "schema/cloak-profile.schema.json").read_text())
    jsonschema.validate(profile["ua"], schema["$defs"]["ua"])
    assert profile["ua"]["value"] == value


@pytest.mark.parametrize("replacement,code", [
    (("Pixel 7", "Pixel 8"), "UA_MODEL_MISMATCH"),
    (("Android 13", "Android 16"), "UA_RELEASE_MISMATCH"),
    (("Version/4.0", "Version/broken"), "UA_FORMAT"),
])
def test_webview_does_not_hide_coherence_or_syntax_errors(replacement, code):
    profile = make_profile()
    value = ("Mozilla/5.0 (Linux; Android 13; Pixel 7 Build/TQ3A.230805.001; wv) AppleWebKit/537.36 "
             "(KHTML, like Gecko) Version/4.0 Chrome/155.0.8059.43 Mobile Safari/537.36")
    profile["ua"] = {"value": value.replace(*replacement)}
    assert code in {f.code for f in validate_profile(profile)}


def test_existing_chrome_user_agent_remains_valid():
    assert validate_profile(make_profile()) == []
