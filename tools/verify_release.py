"""Check release metadata and the actual APK/module pair before attachment. Stdlib only."""
import argparse
import json
import re
from pathlib import Path
from zipfile import ZipFile


def require(condition, message):
    if not condition:
        raise ValueError(message)


def properties(text):
    return dict(line.strip().split("=", 1) for line in text.splitlines()
                if "=" in line and not line.lstrip().startswith("#"))


def verify(root, tag, artifacts=False):
    require(re.fullmatch(r"v\d+\.\d+\.\d+(?:-(?:alpha|beta|rc)\.\d+)?", tag), "Invalid release tag")
    app = (root / "android/app/build.gradle").read_text(encoding="utf-8")
    versions = re.findall(r'^\s*versionName "([^"]+)"', app, re.M)
    codes = re.findall(r"^\s*versionCode (\d+)", app, re.M)
    require(len(versions) == len(codes) == 1, "Expected one app release version/code")
    version, code = versions[0], int(codes[0])
    module = properties((root / "android/module/module.prop").read_text(encoding="utf-8"))
    require(tag == f"v{version}" == module.get("version"), "App/module/tag versions differ")
    require(str(code) == module.get("versionCode"), "App/module version codes differ")
    require(module.get("id") == "cyclone_cloak", "Wrong module ID")
    notes = root / f"docs/RELEASE_{version}.md"
    require(notes.is_file() and notes.stat().st_size > 0, "Missing release notes")
    result = {"tag": tag, "version_code": code, "release_notes": str(notes.relative_to(root))}
    if not artifacts:
        return result
    output = root / "android/app/build/outputs/apk/release"
    metadata = json.loads((output / "output-metadata.json").read_text(encoding="utf-8"))
    require(metadata.get("applicationId") == "dev.cyclone.cloak", "Wrong application ID")
    require(metadata.get("variantName") == "release", "Expected release APK")
    elements = metadata.get("elements", [])
    require(len(elements) == 1, "Expected one release APK")
    element = elements[0]
    require(element.get("versionName") == version and element.get("versionCode") == code,
            "APK output metadata is stale or mismatched")
    filename = element.get("outputFile", "")
    require(filename and Path(filename).name == filename and filename.endswith(".apk"), "Invalid APK output path")
    apk = output / filename
    zip_path = root / f"android/module/build/module/cyclone-cloak-{version}.zip"
    require(apk.is_file() and apk.stat().st_size > 0, "Missing APK")
    require(zip_path.is_file() and zip_path.stat().st_size > 0, "Missing module ZIP")
    with ZipFile(zip_path) as archive:
        require(archive.testzip() is None, "Corrupt module ZIP")
        bundled = properties(archive.read("module.prop").decode("utf-8"))
        require(bundled == module, "Packaged module metadata differs from source")
        for abi, elf_class in (("arm64-v8a", 2), ("armeabi-v7a", 1)):
            library = archive.read(f"zygisk/{abi}.so")
            require(library[:5] == b"\x7fELF" + bytes([elf_class]), f"Missing/invalid ELF for {abi}")
    with ZipFile(apk) as archive:
        require(archive.testzip() is None, "Corrupt APK")
        require(archive.read("assets/cyclone-cloak-module.zip") == zip_path.read_bytes(),
                "APK embeds a different module ZIP")
    result.update(apk=str(apk.relative_to(root)), module_zip=str(zip_path.relative_to(root)))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--artifacts", action="store_true")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    try:
        print(json.dumps(verify(args.root, args.tag, args.artifacts), indent=2))
    except (ValueError, OSError, KeyError) as error:
        parser.exit(1, f"Release verification failed: {error}\n")


if __name__ == "__main__":
    main()
