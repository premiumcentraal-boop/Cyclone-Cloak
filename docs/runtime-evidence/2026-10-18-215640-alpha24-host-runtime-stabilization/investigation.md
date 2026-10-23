# Host ART configuration investigation

Candidate code commit: 57e90bccdd922350f7f070b9b6bf7102e48288e1. Validation pending.

Two real configuration defects were identified: native apply_profile rewrites Build.VERSION.SDK_INT before PineConfig initializes from it; and HookBridge forced PineConfig.debuggable=false even for the installed debuggable diagnostics APK. The measured host is API 36, while fixtures A/B expose API 34/33. A debuggable ART process needs Pine's corresponding backup/trampoline handling. Log verbosity is independent.

The fix captures the actual native device API before hooks and observes (without modifying) the original Zygote runtime_flags. The internal JNI bridge passes those values to Pine before the first hook. DEBUG_JAVA_DEBUGGABLE (1<<8) selects the actual engine mode; no profile, system flags, permissions, connector, bindings or publication scripts are changed. Invalid host APIs reject initialization rather than configuring an engine against invented information.

Primary implementation references reviewed:
- https://github.com/canyie/pine/blob/master/core/src/main/java/top/canyie/pine/PineConfig.java
- https://github.com/canyie/pine/blob/master/core/src/main/cpp/art/art_method.cpp (AfterHook and debuggable handling)
- https://android.googlesource.com/platform/frameworks/base/+/master/core/java/com/android/internal/os/Zygote.java (runtime flag definitions)

Pine upstream source was inspected at 216d910; no dependency update or third-party runtime patch was introduced. The earlier tombstone identifies an ART abort/stack-dump path and does not prove which configuration mismatch triggered it. Physical repeated-warm and lifecycle comparisons are required before claiming resolution. No platform verdict.
