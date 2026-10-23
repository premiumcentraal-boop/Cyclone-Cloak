# Ecosystem consolidation research - 2026-10-23

## What we actually built (honest inventory)

Our Zygisk module (cloak.cpp, ~1300 lines) does, per bound process:

1. Property override map keyed by cloak.* props, applied to Build statics and
   native property reads (apply_profile, lookup_prop).
2. Filesystem parity views for /system/build.prop, /vendor/build.prop,
   /proc/version, /proc/cpuinfo plus maps/mountinfo filtering (build_file_views,
   serve_synthetic, ShadowHook-backed open/openat/access/fstatat hooks).
3. Hand-rolled root trace filtering: a needle list (magisk, zygisk, libhook,
   cyclone_cloak, /data/adb, shadowhook) applied line-wise to maps/mountinfo
   plus ENOENT for root-trace paths.
4. Java-layer hooks via embedded Pine: 14 identifier kinds (IMEI, SIM, IMSI,
   carrier, android_id, wifi/bt MAC, serial, webview UA, network type, refresh
   rate), SensorManager inventory filtering, StatFs, MediaDrm, camera, display.
5. Per-process scoping keyed profileId + androidUserId + packageName, with
   cold/warm lifecycle and post-removal restoration.

Plus a pure-Python identity engine (forge, ~426 KB): profile generation,
coherence validation, Reality runtime table (17 measured surfaces, APPLIED /
PARTIAL / HOST_ONLY ledger), and a probe/evidence harness with hashed
expectations.

The binding model, lifecycle, forge engine, and probe discipline are ours and
defensible. Items 1-4 partially re-implement mature ecosystem tools.

## What the ecosystem already solves

| Capability we hand-built | Mature existing piece | Fit |
|---|---|---|
| Root/module trace hiding (item 3) | Shamiko (Magisk denylist module) | Denylist is per-package and can be toggled programmatically before app start. It hides at the kernel/native layer with far better coverage than our needle list. |
| Play Integrity passing | PlayIntegrityFix (PIF) + verified fingerprint resource | Solves GMS verdict mechanics. BUT PIF sets one fingerprint per device via Magisk module config; our per-profile requirement needs the verdict anchored per-process, which is where our envelope already works. |
| Build prop / fingerprint rewriting | MagiskHide Props Config | Overlaps our property map; keep ours (profile-scoped, no global mutation). |
| Java API hooks | LSPosed modules | Pine embed already covers our 14 kinds with profile scoping; LSPosed would add a dependency and scope conflicts. |

## The architecture decision

### Adopt: Shamiko as the masking substrate

Mechanism: for each bound package in a profile, Cloak writes the package into
Magisk's denylist (su to magisk --denylist add <user-independent pkg path> or
the util API), then our own native filter stays as a per-process second line
of defense and as a no-op when Shamiko is absent.

What gets deleted from our scope: the R2-C package/service self-trace masking
sub-sprint shrinks from a rewrite to (a) denylist integration, (b) three probe
rows proving "not present", (c) keeping our needle filter as fallback. Weeks of
native hardening work collapse into one integration sprint.

### Adopt-with-mapping: PIF for the Play verdict mechanics

PIF owns the GMS-side spoofing surface (keystore quirks, GMS package names,
verdict parameters). Our Rung 3 becomes: per-profile fingerprints still flow
through OUR per-process envelope (Build statics + props are per-process, which
is what PIF's global fingerprint cannot do), while PIF handles the
attestation-sensitive GMS behavior inside each process via our injected
config. Concretely: extend the envelope profile JSON with a pif section that
our module materializes into the process before GMS initializes, instead of a
device-global Magisk module. That preserves 20 unique identities on one phone -
something PIF alone cannot do - while reusing its verdict-passing logic for the
parts that must match GMS's expectations.

### Keep: our differentiators

- Per-profile/per-user/per-app binding with cold/warm lifecycle and
  restoration. No ecosystem tool does this; it is the product.
- Forge engine: generation, coherence validation, expectation hashes.
- Probe/evidence harness: proves the composed stack works; standalone modules
  ship no such discipline.

### Delete from our roadmap

- Deep native hardening of our own root-trace filter beyond the fallback
  (was D1 sprint 3).
- Our own GMS property shims where PIF already handles the surface.
- R2-B sensor/free-storage rework beyond fixture tuning (already hooks exist).

## Integration architecture (target state)

    Cyclone Cloak (companion app + forge engine)
        |-- profile generated + validated locally
        |-- binding: profileId + androidUserId + packageName
        v
    Cyclone Zygisk module (our code, thinner)
        |-- resolves bound profile pre-specialize
        |-- writes denylist entry (Shamiko path) OR applies own filter
        |-- serves profile props/file views per process (ours)
        |-- injects Pine hooks + envelope config (ours)
        |-- materializes per-profile PIF config before GMS init (ours)
        v
    Shamiko (denylist enforcement)      PIF logic (verdict mechanics)
        |                                    |
        +---------------- proof via our probe/evidence harness ----------+

## Sprint plan (revised, from here)

- S1: Shamiko integration + denylist wiring + probe rows for package/maps/
  services "not present". Stop gate: three rows PASS on both users.
- S2: PIF-config materialization inside the envelope (pif section in profile
  schema, module loads it pre-GMS). Stop gate: per-profile verdict request
  shows distinct fingerprints in evidence.
- S3: Delete/retire our superseded native filter paths; keep fallback.
- S4: Rung 3 Play verdict measurement end-to-end per profile (was planned).
- S5: RC packaging (delegated).

## Risks

- Shamiko is Magisk-ecosystem specific; KernelSU users need an alternate
  denylist path (keep our native filter as universal fallback).
- PIF's internals change with Play server updates; pin and pin again via
  forge schema versioning.
- Combined stack must be evidence-verified per profile, which is exactly what
  our probe gives us; never present module-internal success as a verdict.
