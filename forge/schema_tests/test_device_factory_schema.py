import pytest
from cloak_forge.device_factory import DeviceFactory, InstanceRequest
from test_engine_schema import validator


@pytest.mark.parametrize("template", DeviceFactory().catalog(), ids=lambda t: t["id"])
def test_factory_outputs_use_unmodified_profile_schema(template):
    bundle = DeviceFactory().generate(InstanceRequest(template["id"], "schema-test"))
    validator("cloak-profile.schema.json").validate(bundle["profile"])
    assert "quality" not in bundle["profile"]
