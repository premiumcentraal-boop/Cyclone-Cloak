import copy
import json
import subprocess
import sys
from pathlib import Path
import pytest
from cloak_forge.forge import forge_profile
from cloak_forge.engine import generate_unique, check_uniqueness
from cloak_forge.reality import validate, SECTIONS, UNIQUE_PATHS
from cloak_forge.templates import load_catalog
ROOT = Path(__file__).resolve().parents[2]
VECTOR = json.loads((ROOT / "forge/tests/vectors/reality.json").read_text())

def patch_profile(patch):
    p=copy.deepcopy(VECTOR["profile"])
    for path,value in patch.items():
        parent=p;keys=path.split(".")
        for key in keys[:-1]: parent=parent.setdefault(key,{})
        parent[keys[-1]]=value
    return p

@pytest.mark.parametrize("case",VECTOR["cases"],ids=lambda c:c["name"])
def test_shared_rule_cases(case):
    findings=validate(patch_profile(case["patch"]))
    assert [dict(severity=s,code=c,field=f,message=m) for s,c,f,m in sorted(findings,key=lambda f:(f[2],f[1]))] == case["findings"]
    if case["name"].startswith("valid "): assert not findings
    if case["name"].startswith("invalid "): assert findings

@pytest.mark.parametrize("phone",load_catalog(),ids=lambda p:p["id"])
def test_all_catalog_templates_deterministic_and_valid(phone):
    a=forge_profile("Reality","ab"*32,phone["id"],schema_version="0.4")
    assert a==forge_profile("Reality","ab"*32,phone["id"],schema_version="0.4")
    assert not validate(a)
    assert set(SECTIONS) <= set(a["reality"])
    assert a["reality"]["inventory_id"]!=forge_profile("Reality","cd"*32,phone["id"],schema_version="0.4")["reality"]["inventory_id"]

@pytest.mark.parametrize("version",["0.2","0.3"])
def test_legacy_no_migration(version):
    old=forge_profile("Old","ab"*32,"pixel_7",schema_version=version);before=copy.deepcopy(old)
    assert "reality" not in old and validate(old)==[] and old==before

def test_generation_parity_vector():
    assert VECTOR["profile"]==forge_profile("Reality","ab"*32,"pixel_7",schema_version="0.4")

def test_extended_uniqueness_and_retries_preserve_library():
    request=dict(name="Reality",seed="ab"*32,template="pixel_7",schema_version="0.4")
    a=generate_unique(request,[])["profile"]; other=copy.deepcopy(a);other["id"]="other";other["identifiers"]={}
    assert {f.field for f in check_uniqueness(a,[other])}==set(UNIQUE_PATHS)
    before=copy.deepcopy(other);b=generate_unique(request,[other])
    assert b["metadata"]["attempt_count"]==2 and not check_uniqueness(b["profile"],[other]) and before==other

def test_rule_and_coverage_registries_do_not_drift():
    subprocess.run([sys.executable,str(ROOT/"tools/generate_reality_rules.py"),"--check"],check=True)
    schema=json.loads((ROOT/"schema/device-reality.schema.json").read_text())
    fields={s:list(schema["properties"][s]["properties"]["values"]["properties"]) for s in SECTIONS}
    assert fields==json.loads((ROOT/"android/probe/src/main/assets/reality-fields.json").read_text())
    matrix=json.loads((ROOT/"catalog/reality-coverage.json").read_text())
    assert {r["row_id"] for r in matrix["fields"]}=={f"reality.{s}.{k}" for s,keys in fields.items() for k in keys}
    assert {r["state"] for r in matrix["fields"]}=={"unmapped"}
