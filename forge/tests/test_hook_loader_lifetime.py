"""Native lifecycle guards; repeated on-device GC/warm captures provide runtime proof."""
from pathlib import Path

SOURCE = (Path(__file__).resolve().parents[2] / "android/module/src/main/cpp/cloak.cpp").read_text(encoding="utf-8")
INIT = SOURCE.split("void init_java_hooks(", 1)[1].split("class CloakModule", 1)[0]


def test_loader_is_rooted_before_callbacks_are_installed():
    assert "jobject g_hook_loader = nullptr;" in SOURCE
    assert INIT.index("g_hook_loader = env->NewGlobalRef(loader)") < INIT.index("env->CallStaticVoidMethod(bridge, init")
    assert "NewWeakGlobalRef" not in INIT


def test_failed_root_aborts_before_hook_installation():
    failure = INIT.split("if (!g_hook_loader) {", 1)[1].split("env->CallStaticVoidMethod", 1)[0]
    assert "return;" in failure
    assert "ExceptionClear" in failure


def test_root_survives_local_cleanup_and_partial_installation():
    assert "DeleteLocalRef(loader)" in INIT
    assert "DeleteGlobalRef(g_hook_loader)" not in SOURCE
    assert "g_hook_loader != nullptr" in INIT
