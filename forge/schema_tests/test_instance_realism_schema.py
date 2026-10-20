import copy
import json
import sys
from pathlib import Path

import pytest
from jsonschema import Draft202012Validator
from referencing import Registry, Resource

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'forge'))
from cloak_forge.engine import generate_unique
from cloak_forge.forge import forge_profile
SCHEMAS = [json.loads(p.read_text()) for p in (ROOT / 'schema').glob('*.schema.json')]
REGISTRY = Registry().with_resources((s['$id'], Resource.from_contents(s)) for s in SCHEMAS)


def validator(name):
    return Draft202012Validator(json.loads((ROOT / 'schema' / name).read_text()), registry=REGISTRY)


def bundle():
    return generate_unique(dict(name='Instance', seed='ab'*32, template='pixel_7', schema_version='0.4', instance_realism=True), [])


def test_new_profile_and_bundle_roundtrip_schemas():
    b = json.loads(json.dumps(bundle()))
    validator('cloak-profile.schema.json').validate(b['profile'])
    validator('engine-bundle.schema.json').validate(b)


@pytest.mark.parametrize('field,value', [('format', 2), ('catalog_digest', 'bad'), ('source_id', 'foreign'),
    ('provenance', {}), ('axes', {}), ('unexpected', 'not allowed')])
def test_malformed_metadata_rejected(field, value):
    b = bundle(); b['profile']['meta']['instance_realism'][field] = value
    assert list(validator('cloak-profile.schema.json').iter_errors(b['profile']))


@pytest.mark.parametrize('version', ['0.2', '0.3', '0.4'])
def test_old_profiles_require_no_migration(version):
    p = forge_profile('Old', 'ab'*32, 'pixel_7', version); before = copy.deepcopy(p)
    validator('cloak-profile.schema.json').validate(p)
    assert p == before
