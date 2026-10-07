import hashlib

# Mirrors CloakStateLayout (app) and the module reader. If either side changes the
# tuple format, this vector changes too, in the same commit.
EXPECTED_KEY = "d531e13ba21eb8f7c08a1afc89d49f148ac076b065ce3f9bd8aebbf42fb0b122"


def _key(profile_id: str, user_id: int, package: str) -> str:
    return hashlib.sha256(f"{profile_id}\n{user_id}\n{package}".encode()).hexdigest()


def test_state_key_vector():
    assert _key("Cyclone_0123456789abcdef", 7, "com.example.app") == EXPECTED_KEY


def test_state_path_convention():
    key = _key("Cyclone_0123456789abcdef", 7, "com.example.app")
    assert len(key) == 64 and all(c in "0123456789abcdef" for c in key)
    path = f"/data/user/7/dev.cyclone.cloak/no_backup/cyclone-profile-state-v1/{key}/profile.json"
    assert path.endswith(f"no_backup/cyclone-profile-state-v1/{key}/profile.json")