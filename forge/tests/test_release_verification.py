"""Release gates reject stale/missing/mismatched APK/module pairs."""
import json
import runpy
from pathlib import Path
from zipfile import ZipFile

import pytest

verify = runpy.run_path(str(Path(__file__).resolve().parents[2] / "tools/verify_release.py"))["verify"]


@pytest.fixture
def release(tmp_path):
    files = {
        "android/app/build.gradle": 'versionName "0.10.0-rc.1"\nversionCode 22\n',
        "android/module/module.prop": "id=cyclone_cloak\nversion=v0.10.0-rc.1\nversionCode=22\n",
        "docs/RELEASE_0.10.0-rc.1.md": "RC, device checks unverified",
    }
    for name, text in files.items():
        path = tmp_path / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
    module = tmp_path / "android/module/build/module/cyclone-cloak-0.10.0-rc.1.zip"
    module.parent.mkdir(parents=True)
    with ZipFile(module, "w") as archive:
        archive.writestr("module.prop", files["android/module/module.prop"])
        archive.writestr("zygisk/arm64-v8a.so", b"\x7fELF\x02test")
        archive.writestr("zygisk/armeabi-v7a.so", b"\x7fELF\x01test")
    output = tmp_path / "android/app/build/outputs/apk/release"
    output.mkdir(parents=True)
    with ZipFile(output / "app-release-unsigned.apk", "w") as archive:
        archive.writestr("assets/cyclone-cloak-module.zip", module.read_bytes())
    metadata = {"applicationId": "dev.cyclone.cloak", "variantName": "release",
                "elements": [{"versionName": "0.10.0-rc.1", "versionCode": 22,
                              "outputFile": "app-release-unsigned.apk"}]}
    (output / "output-metadata.json").write_text(json.dumps(metadata), encoding="utf-8")
    return tmp_path


def test_matching_metadata_and_both_artifacts_pass(release):
    result = verify(release, "v0.10.0-rc.1", artifacts=True)
    assert result["version_code"] == 22
    assert result["apk"].endswith(".apk")
    assert result["module_zip"].endswith(".zip")


@pytest.mark.parametrize("file,replacement", [
    ("android/module/module.prop", "id=cyclone_cloak\nversion=v0.10.0-alpha.1\nversionCode=22"),
    ("android/module/module.prop", "id=cyclone_cloak\nversion=v0.10.0-rc.1\nversionCode=19"),
    ("docs/RELEASE_0.10.0-rc.1.md", ""),
])
def test_stale_source_metadata_or_missing_notes_fail(release, file, replacement):
    (release / file).write_text(replacement, encoding="utf-8")
    with pytest.raises(ValueError):
        verify(release, "v0.10.0-rc.1")


@pytest.mark.parametrize("field,value", [("versionName", "0.10.0-alpha.1"), ("versionCode", 21),
                                        ("outputFile", "../other.apk")])
def test_stale_or_unsafe_apk_metadata_fails(release, field, value):
    path = release / "android/app/build/outputs/apk/release/output-metadata.json"
    metadata = json.loads(path.read_text())
    metadata["elements"][0][field] = value
    path.write_text(json.dumps(metadata))
    with pytest.raises(ValueError):
        verify(release, "v0.10.0-rc.1", artifacts=True)


def test_missing_module_zip_fails(release):
    (release / "android/module/build/module/cyclone-cloak-0.10.0-rc.1.zip").unlink()
    with pytest.raises(ValueError, match="Missing module ZIP"):
        verify(release, "v0.10.0-rc.1", artifacts=True)


def test_mismatched_embedded_module_fails(release):
    path = release / "android/app/build/outputs/apk/release/app-release-unsigned.apk"
    with ZipFile(path, "w") as archive:
        archive.writestr("assets/cyclone-cloak-module.zip", b"old module")
    with pytest.raises(ValueError, match="embeds a different"):
        verify(release, "v0.10.0-rc.1", artifacts=True)


def test_missing_native_abi_fails(release):
    path = release / "android/module/build/module/cyclone-cloak-0.10.0-rc.1.zip"
    with ZipFile(path) as archive:
        props = archive.read("module.prop")
    with ZipFile(path, "w") as archive:
        archive.writestr("module.prop", props)
        archive.writestr("zygisk/arm64-v8a.so", b"\x7fELF\x02test")
    with pytest.raises(KeyError):
        verify(release, "v0.10.0-rc.1", artifacts=True)
