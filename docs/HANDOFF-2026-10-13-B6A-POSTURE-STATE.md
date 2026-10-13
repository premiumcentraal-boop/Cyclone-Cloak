# B6A local compatibility posture state — 2026-10-13

## What changed

Added a neutral local posture model and separate app-private store. The default
provider always returns UNKNOWN with no check timestamp or artifact digest.
No provider is automatically run on profile loading and no record is fabricated
for an existing profile. Identity cards show "not recorded" when a record is
absent, corrupt, unsupported or keyed to another profile.

Work started on clean main at `fbebdcf`, aligned with published
`v0.11.0-alpha.3`. AGENTS.md and the orientation docs were read; tags were fetched.
No version bump, tag, release or device action is part of this run.

## Public signatures

All model/provider/rotation types are in `dev.cyclone.cloak.data` and have no
dependency on forge, connector or root code:

```kotlin
enum class PostureState { UNKNOWN, PASS, DEGRADED, FAILED, NEEDS_ROTATION }

data class PostureRecord(
    val profileId: String,
    val state: PostureState,
    val checkedAt: String?,
    val source: String,
    val reason: String,
    val providerId: String? = null,
    val artifactDigest: String? = null,
)
PostureRecord.toJson(): JSONObject
PostureRecord.fromJson(json: JSONObject): PostureRecord

data class PostureResult(
    val state: PostureState = PostureState.UNKNOWN,
    val checkedAt: String? = null,
    val source: String = "local",
    val reason: String = "No compatibility posture result is available.",
    val providerId: String? = null,
    val artifactDigest: String? = null,
)
PostureResult.recordFor(profileId: String): PostureRecord

interface CompatibilityPostureProvider {
    fun evaluate(profileId: String): PostureResult
}
class LocalCompatibilityPostureProvider : CompatibilityPostureProvider

class PostureStore(context: Context)
PostureStore.read(profileId: String): PostureRecord?
PostureStore.update(record: PostureRecord): Unit
PostureStore.clear(profileId: String): Unit

PostureRotation.markNeedsRotation(profileId: String, reason: String, checkedAt: String? = null): PostureRecord
PostureRotation.afterExplicitReset(record: PostureRecord, resetCompleted: Boolean, checkedAt: String? = null): PostureRecord
```

No result is represented by UNKNOWN with checkedAt null. The provider takes only
the profile ID, returns providerId `local-unknown`, and never inspects the profile's
integrity block or evaluates runtime evidence. PASS means an explicitly supplied
local compatibility result; it is not a platform or attestation verdict. Future
providers must supply supported local observations without inventing such verdicts.

Rotation helpers are pure. markNeedsRotation returns a proposed NEEDS_ROTATION
record, source `local-rotation`, with the explicit supplied reason. Clearing
requires resetCompleted=true from a caller that has already completed an explicit
local reset/reseed; false throws without changing anything. NEEDS_ROTATION becomes
UNKNOWN, source `local-reset`, reason "Explicit local reset completed; compatibility
posture has not been checked." Old provider/artifact evidence is dropped. Other
states remain unchanged. The caller must deliberately call store.update to persist
either transition. Helpers never reset, reseed, delete, publish or bind a profile.

## Storage format

SharedPreferences namespace `compatibility-posture`, `Context.MODE_PRIVATE`,
one JSON string per profile-ID key. It is separate from `device-profile-engine`
preferences and `<filesDir>/profiles/`. Writes/removals use synchronous commit;
failed commits surface as errors. Reads, updates and clears are synchronized
across PostureStore instances in the app process.

```json
{
  "format": 1,
  "profile_id": "<saved profile id>",
  "state": "UNKNOWN",
  "checked_at": null,
  "source": "local",
  "reason": "No compatibility posture result is available.",
  "provider_id": "local-unknown",
  "artifact_digest": null
}
```

State names are stable uppercase strings. checked_at is null or an ISO-8601
instant; it is not auto-populated. provider_id and artifact_digest may be omitted
or null. An artifact digest, when supplied, must be lowercase SHA-256. Text fields
are bounded, nonblank and reject control characters. The parser rejects missing
required fields, unknown properties, unknown states/formats and invalid types.
Read bounds the serialized record to 4096 characters and checks the embedded
profile ID against the lookup key. Invalid records return null and remain on disk;
explicit update can replace a corrupt record and clear can remove it. Reading
does not delete or repair data automatically.

Existing profile files, imports, backups and engine records are unchanged. There
is no migration, profile rewrite, posture backfill or posture export. Removed
profiles may leave private orphan posture records; only explicit clear removes
them. This store is independent of synchronization, binding health, module readers,
publication and the engine's existing posture/PIF derivation operation.

## UI placement

The Identities tab displays one small line directly below each existing identity
card, using the existing theme typography and onSurfaceVariant color:

| Record | Label |
| --- | --- |
| Missing/corrupt | Local compatibility posture: not recorded |
| UNKNOWN | Local compatibility posture: Unknown |
| PASS | Local compatibility posture: Pass |
| DEGRADED | Local compatibility posture: Degraded |
| FAILED | Local compatibility posture: Failed |
| NEEDS_ROTATION | Local compatibility posture: Needs rotation |

Expanded cards show the stored reason followed by "Local compatibility record
only; no platform verdict." The compact label contains no profile ID, provider
text or artifact digest. No new screen, tab, permission or network action was
added. UI loading only reads existing posture records; it does not call a provider
or persist UNKNOWN on missing records.

UI public helper: `PostureSummary.label(record: PostureRecord?): String`.
Composable: `PostureSummaryLine(record: PostureRecord?, modifier: Modifier = Modifier)`.
CloakState adds `postures: Map<String, PostureRecord>` for the displayed saved
identity inventory.

## Changed files

- `android/app/src/main/java/dev/cyclone/cloak/data/PostureRecord.kt` — enum, record/result, provider and pure rotation helpers.
- `android/app/src/main/java/dev/cyclone/cloak/data/PostureStore.kt` — isolated app-private persistence.
- `android/app/src/main/java/dev/cyclone/cloak/ui/PostureSummary.kt` — labels and themed line.
- `android/app/src/main/java/dev/cyclone/cloak/ui/CloakViewModel.kt` — read posture alongside local inventory.
- `android/app/src/main/java/dev/cyclone/cloak/ui/IdentitiesScreen.kt` — display state and expanded reason.
- `android/app/src/test/java/dev/cyclone/cloak/data/PostureRecordTest.kt` — serialization, validity, default provider and rotations.
- `android/app/src/test/java/dev/cyclone/cloak/data/PostureStoreTest.kt` — storage/recovery, isolation, unchanged imports/profile bytes.
- `android/app/src/test/java/dev/cyclone/cloak/ui/PostureSummaryTest.kt` — all state labels, missing vs unknown, compact-label privacy.
- `android/app/src/test/java/dev/cyclone/cloak/ui/PostureSummaryScreenTest.kt` — real Compose rendering of all states and missing state.
- `docs/ARCHITECTURE.md` — local posture boundary.
- This handoff.

## Verification

Exact commands, from `C:\Users\Agent\Cyclone\cloak-repo`:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug --offline --no-daemon
uv run --with pytest -- python -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -m pytest forge/schema_tests -q
git diff --check
```

Final results: Android app **310 passed / 23 skipped** (333 total), module
**12 passed**, forge **227 passed**, supplemental schema suite **33 passed**.
No failures/errors. B6A adds 17 Android tests. The 23 existing skips are POSIX
publication shell tests on Windows without the optional WSL switch. Existing
architecture/import/engine tests remain green.

Debug APK build succeeds at `android/app/build/outputs/apk/debug/app-debug.apk`.
The existing build dependency packages the unchanged module sources as
`android/module/build/module/cyclone-cloak-0.11.0-alpha.3.zip`; no module files
were edited. No compiler/build warnings; ordinary Git LF-to-CRLF notices may
appear. No device verification was performed.

## Follow-ups and limits

Provider execution, explicit UI posture updates and any reset/reseed workflow
integration are future work. B6A supplies only the neutral state boundary and
pure transition helpers; existing identifier editing does not silently clear or
change posture. Missing results are valid. Posture is keyed only by profile ID,
not body digest; future providers must manage their own freshness/invalidation.
There is no automatic cleanup or export of records and no platform verdict claim.
Historical README/architecture release headers still mention RC2; the actual
published baseline is alpha.3. That unrelated documentation cleanup remains pending.

B6A is committed and pushed to main; the completion report gives its commit hash.
No release or tag was created.
