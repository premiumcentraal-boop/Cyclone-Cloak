# Hook loader lifetime stabilization

Code commit: 68ef4cbb5f068ced6305a88c3bc566a54f04ff53. Physical validation pending.

The preceding 57e90bc candidate corrected real host-API/debug-mode configuration defects, but still crashed on warm repetition 4 (PID 12389, thread cloak-probe-5) at artQuickToInterpreterBridge+68, SIGSEGV/null read 0x38. Its matching tombstone identifies RealityProbe.lambda$collect$10, the camera inventory reader. The failed method pointer lies in dalvik-LinearAlloc and the private tombstone shows that method storage zeroed. A background concurrent mark-compact GC completed immediately before the failure (22:11:49.298; fatal signal at 22:11:49.305).

Native init_java_hooks loaded HookBridge and Pine through an InMemoryDexClassLoader, installed native trampolines, then deleted every local class/loader reference. Static references within that loader form an unreachable cycle; trampoline code addresses are not GC roots. This leaves ART free to unload the DEX and reclaim the methods that installed hooks still call.

The narrow fix creates one strong JNI global loader reference before installing any callback. A failed global allocation aborts before installation. The reference survives partial hook installation and remains for the scoped process lifetime; repeated initialization is rejected. No GC suppression, forced cold restart, dependency replacement, profile/content change, connector, binding, publication or permission change is introduced.

Focused source guards verify rooting order, failure-before-installation, and root survival through local cleanup. These do not simulate native ART collection; repeated warm captures and full lifecycle runs are the required physical regression proof. Earlier failures remain preserved, not relabeled. No platform verdict.

Installation-audit note: the previous candidate's first preservation check compared JSON lists with Python tuples and emitted a false PERMISSION_STATE_CHANGED. Its saved before/after JSON snapshots were identical. Normalizing the in-memory snapshot to JSON fixed the comparison; no device permissions were changed.
