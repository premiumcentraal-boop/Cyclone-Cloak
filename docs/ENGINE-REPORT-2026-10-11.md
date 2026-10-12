# Cyclone Cloak Identity Engine Report

Reviewed: 2026-10-11. Based on the local Cyclone Cloak checkout and published `v0.10.0-rc.1`.

## Executive summary

We are closer than it may look, but not as close as the UI suggests.

The project is already split into three useful layers:

1. **Profile generator and validator** - this is mostly working.
2. **Runtime module** - this is the hard part and is only partially proven.
3. **Cyclone integration and UI** - this is working well enough to be expanded safely.

The generator already creates many identities from a phone template. A template is not one identity. A template is the body of a phone: model, build, display, camera class, carrier and other stable facts. Each generated identity gets its own seed and derives its own identifiers from that seed, including Android ID, advertising ID, app set ID, Wi-Fi MAC, Bluetooth MAC, primary and secondary IMEI, SIM serial, GSF ID, Widevine ID and serial number.

The current weakness is not identity creation. The current weakness is that many Android APIs still read the real phone instead of the generated profile. Runtime coverage is only partially proven. The `pif.json` output exists, but it does not yet prove a Play Integrity pass.

So the realistic status is:

- Generator: ~70-75 percent of the way to a clean internal engine.
- Validation: ~70 percent.
- Runtime coverage: ~50 percent, with important gaps.
- Play Integrity and banking-grade trust: not yet achieved.
- Cyclone integration: good enough to accept the engine if the contract stays stable.

The best next step is not to rebuild Cyclone Cloak. The best next step is to extract the generator and validator into a separate, clean identity engine, then wire the runtime and integrity work to that same contract.

---

## How the current system is separated

```text
catalog/phones.json
        |
        v
forge/cloak_forge (Python reference)
android/app/.../forge (Kotlin app-side mirror)
        |
        +-- profile.json
        +-- pif.json
        |
        v
data + Cyclone binding + publication
        |
        v
Zygisk module / hook bridge
        |
        v
app-visible Android APIs
```

### What is already good

- `catalog/phones.json` holds reusable phone bodies.
- `forge/cloak_forge` is the Python reference generator.
- The Kotlin app has a mirrored forge under `android/app/src/main/java/dev/cyclone/cloak/forge`.
- Both sides generate identifiers from the profile seed.
- Both sides validate profile coherence.
- Bindings are stored per Cyclone profile, Android user and app package.
- `pif.json` is already generated per binding.
- Publication and health reporting are separated from the app UI.
- The app has tests for generation, validation, uniqueness and release artifacts.

### What is weak

- Runtime coverage is incomplete.
- Some runtime changes are built locally but have not been installed or rebooted into the test phone.
- Camera and sensor facts are not modeled yet.
- Display geometry is incomplete.
- Uptime and other behavioral signals are not covered.
- The generator does not yet prove that every identity is strong enough for a target app.
- `pif.json` is only a posture artifact. It does not manage keyboxes or verify a real Play Integrity result.

---

## Templates versus identities

A template is not an identity. It is the reusable device shape.

Example:

```json
{
  "id": "pixel_4",
  "label": "Pixel 4"
}
```

The current generator uses the template for stable device facts, then generates identifiers from a seed. Each generated profile has:

- its own profile ID;
- its own seed;
- its own Android ID;
- its own advertising ID;
- its own app set ID;
- its own Wi-Fi MAC;
- its own Bluetooth MAC;
- its own IMEI and secondary IMEI;
- its own SIM serial;
- its own GSF ID;
- its own Widevine ID;
- its own serial number.

So the model can be reused, but the generated identity is unique.

This is the correct design. A template should stay stable because a Pixel 4 should always look like a Pixel 4. The identity should vary because each generated phone should look like a different Pixel 4.

---

## Play Integrity reality

This is the hardest part.

Current state:

- `pif.json` is generated per binding.
- The runtime module can apply Build fields and some Java-side callbacks.
- There is some measured runtime evidence, but many callbacks still fail or are blocked.
- The installed API 36 test showed 13 verified reads out of 70, with many failures and blocked reads.
- Local fixes have been built but not installed or rebooted into the phone.
- Automatic Play Integrity checking, keybox management, fingerprint rotation and reseed-on-reset are not implemented.

Honest assessment:

- We can make profiles internally coherent.
- We can expose many identifiers correctly.
- We can create a `pif.json` per profile.
- We cannot yet honestly claim full Play Integrity success.
- We also cannot promise permanent success. Google can tighten checks at any time.

The realistic path is to integrate the profile generator with an integrity adapter that handles:

1. `pif.json` output.
2. device attestation posture;
3. keybox handling;
4. fingerprint rotation;
5. reset-triggered reseeding;
6. verdict testing;
7. clear reporting to the user.

That adapter should not live inside the app UI. It should be its own small engine layer.

---

## External projects to combine

The existing foundation is already close to this stack:

- **Magisk/Zygisk** - process entry point and module loading.
- **Pine** - Java method hooking.
- **Play Integrity Fix / PIF-style consumers** - per-profile posture artifact.
- **Tricky Store / keybox-style modules** - device attestation and keybox handling.
- **Cloak Forge** - profile generation and validation.
- **Cloak module** - runtime application to Android APIs.

The correct architecture is not to merge all code blindly. It is to define one clean profile contract and then keep the pieces separate.

---

## Proposed engine architecture

### Layer 1 - Identity Forge

This is the core generator. It should be independent and testable.

Input:

- device template;
- region preference;
- uniqueness constraints;
- optional seed;
- optional existing library.

Output:

- one coherent profile JSON;
- one `pif.json` posture artifact;
- validation findings;
- uniqueness findings;
- confidence metadata.

This layer should not know anything about Cyclone UI, Magisk or Android runtime hooks.

### Layer 2 - Validation Engine

This is the quality gate.

It should enforce:

- build/fingerprint coherence;
- SDK/Android release coherence;
- display coherence;
- hardware consistency;
- camera and sensor consistency;
- carrier and telephony consistency;
- locale/timezone consistency;
- identifier format and uniqueness;
- no impossible combinations.

It should produce machine-readable reasons so other tools and agents can build against it.

### Layer 3 - Device Fact Corpus

This is the missing mature piece.

We need a structured database of real device facts, not just hand-written templates. The corpus should contain:

- model;
- product;
- device;
- fingerprint;
- security patch;
- first API level;
- build ID;
- hardware;
- display;
- camera capabilities;
- sensor list;
- DRM level;
- carrier variants;
- regional variants;
- known OEM quirks.

This can start as JSON, then later become a signed corpus file.

### Layer 4 - Runtime Adapter

This is the module that applies the profile inside an app process.

It should consume the profile contract and apply:

- Build fields;
- system properties;
- telephony fields;
- Android ID;
- advertising ID;
- Wi-Fi and Bluetooth identity;
- serial;
- locale/timezone;
- WebView user agent;
- display geometry;
- camera and sensor metadata;
- uptime offsets where needed.

This layer should have its own test APK and an evidence-driven coverage matrix.

### Layer 5 - Integrity Adapter

This is the dirty core that should be isolated.

It should handle:

- `pif.json`;
- Play Integrity posture;
- keybox handling;
- fingerprint rotation;
- verdict testing;
- reseed on reset or failure.

This layer should report clear pass, fail or unknown states. It should not pretend that a generated profile guarantees success.

### Layer 6 - Cyclone Cloak Shell

Cyclone Cloak should become the user-facing shell:

- phone catalog;
- generator UI;
- identity library;
- binding UI;
- health doctor;
- release packaging.

The heavy logic should live in the engine, not in the UI layer.

---

## Why this separation matters

If the logic remains inside Cyclone Cloak, every future change becomes harder to test and harder to hand to other agents.

A separate engine gives us:

1. A clear contract.
2. Faster iteration.
3. Better test vectors.
4. Safer delegation.
5. Reusable runtime and integrity adapters.
6. Less coupling between app UI and low-level behavior.

---

## Current confidence levels

| Area | Current confidence | Reason |
| --- | ---: | --- |
| Profile generation | High | Already implemented in Kotlin and Python |
| Identifier generation | High | Deterministic HMAC-derived identifiers exist |
| Basic coherence validation | High | Shared rules and test vectors exist |
| Runtime Build fields | Medium | Device evidence shows several verified reads |
| Runtime identifier coverage | Low-medium | Many callback reads currently fail or remain unverified |
| Display coverage | Low | Not fully implemented |
| Camera and sensors | Low | Not modeled or hooked yet |
| Play Integrity | Low | `pif.json` exists, but no complete attestation path |
| Banking-grade trust | Low | Requires full runtime and attestation work |
| Cyclone integration | High | Binding and sync model already work |
| App UI | High | Existing screens already support most workflows |

---

## What should be built next

### Step 1 - Extract the Identity Forge

Create a clean engine package, separate from the Cyclone Cloak UI. It should expose:

```text
forge_profile(template, options) -> profile
validate_profile(profile) -> findings
derive_pif(profile) -> pif.json
check_uniqueness(profile, library) -> findings
```

It should be available as:

- a local Python library;
- a local Kotlin/JVM library;
- or a small CLI/API wrapper.

### Step 2 - Define profile contract v1

Current schema is 0.2. The next version should become a stable contract:

```text
identity-forge.profile/1
```

It should include:

- device block;
- identifiers;
- telephony;
- network;
- display;
- camera;
- sensors;
- locale;
- integrity posture;
- confidence metadata;
- engine version.

### Step 3 - Build the device fact corpus

Move away from only hand-written templates. Build a real device corpus.

This is the difference between “plausible” and “strong.”

### Step 4 - Fix runtime coverage

Use the existing probe app. Every claimed hook must be proven against:

- two distinct profiles;
- repeated cold starts;
- unbound control before and after;
- real device API reads;
- a saved evidence file.

### Step 5 - Add the integrity adapter

Integrate the existing external work into one adapter. Do not put this directly in the UI.

It should answer:

- can this profile pass device checks;
- is a keybox available;
- is the fingerprint healthy;
- does the profile need rotation;
- has the verdict changed.

### Step 6 - Wire Cyclone Cloak to the engine

Cyclone Cloak should call the engine, not implement it.

The UI becomes:

```text
choose template
    -> engine generates profile
    -> engine validates profile
    -> engine checks uniqueness
    -> engine emits profile.json and pif.json
    -> Cloak binds and publishes
    -> runtime adapter applies profile
```

---

## Practical sprint plan

### Sprint A - Extract Forge

Extract generator, validator and uniqueness logic into a clean package. No UI changes.

### Sprint B - Contract hardening

Freeze profile schema v1. Add camera, sensor, display and integrity metadata.

### Sprint C - Device fact corpus

Add structured device facts and provenance. Do not invent facts.

### Sprint D - Runtime coverage repair

Use the probe to fix failing callbacks. Every pass must be proven.

### Sprint E - Integrity adapter

Integrate PIF-style posture, attestation handling and verdict testing.

### Sprint F - Shell integration

Wire Cyclone Cloak to the engine.

### Sprint G - Validation harness

Build one command that says:

```text
generate -> validate -> publish -> launch -> inspect -> report
```

This becomes the acceptance test for future releases.

---

## Realistic distance to the target

We are not far from a working internal engine, but we are not at banking-grade yet.

A realistic milestone order is:

1. **Near-term:** a separate forge that always emits coherent, validated, unique profiles.
2. **Mid-term:** runtime adapter with proven coverage over the main Android identity surfaces.
3. **Later:** integrity adapter with real Play Integrity posture, rotation and verdict feedback.
4. **Final:** Cyclone Cloak as the shell that drives the whole machine.

The most important architectural decision is to stop treating Cyclone Cloak as the engine. Cyclone Cloak should be the cockpit. The engine should be a separate core that produces profiles and lets other tools apply them.

---

## Bottom line

The idea is realistic. The current repository already has most of the first half of the system.

What is missing is not the concept. It is the separation of the engine from the app, a stronger device corpus, and real runtime proof.

If we do this correctly, the future flow becomes simple for the user:

1. Open Cyclone Cloak.
2. Pick a phone.
3. Press generate.
4. The engine produces a valid profile.
5. Cyclone Cloak binds it to the profile and app.
6. The user sees a clear readiness verdict.

That is the right target, and it is achievable. But we should not claim full Play Integrity or banking compatibility until the engine and runtime evidence prove it.
