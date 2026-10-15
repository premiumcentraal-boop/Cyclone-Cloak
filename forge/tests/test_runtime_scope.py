"""Integration guard; constexpr cases also compile for both NDK ABIs during packaging."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def test_companion_exclusion_precedes_profile_loading_and_all_hooks():
    source = (ROOT / "android/module/src/main/cpp/cloak.cpp").read_text(encoding="utf-8")
    pre = source.split("void preAppSpecialize", 1)[1].split("void postAppSpecialize", 1)[0]
    guard = pre.index("if (!runtime_package_allowed(g_active_package))")
    load = pre.index("g_active = load_profile_for")
    assert guard < load
    assert "g_active = false;" in pre[guard:load]
    assert "return;" in pre[guard:load]
    post = source.split("void postAppSpecialize", 1)[1]
    assert post.index("if (g_active)") < post.index("install_native_property_hook()")


def test_runtime_scope_is_exact_package_match_independent_of_android_user():
    header = (ROOT / "android/module/src/main/cpp/runtime_scope.hpp").read_text(encoding="utf-8")
    assert 'return package_name != "dev.cyclone.cloak";' in header
    assert 'static_assert(!runtime_package_allowed("dev.cyclone.cloak"))' in header
    assert 'static_assert(runtime_package_allowed("dev.cyclone.cloak.probe"))' in header
    assert 'static_assert(runtime_package_allowed("com.android.chrome"))' in header
