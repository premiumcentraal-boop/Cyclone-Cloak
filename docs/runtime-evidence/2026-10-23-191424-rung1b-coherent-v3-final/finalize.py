"""Archive the final authorized resume, without device actions."""
import collections
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

O = Path(__file__).resolve().parent
R = O.parents[2]
P = O.parent / '2026-10-23-185155-rung1b-coherent-v3'
def read(name): return json.loads((O/name).read_text(encoding='utf-8-sig'))
def save(name, value): (O/name).write_text(json.dumps(value,indent=2)+'\n',encoding='utf8')
def files(name): return {p:h for h,p in (line.split(None,1) for line in read(name)['files'].splitlines())}
before, after = files('module-before.json'), files('module-after.json')
changed = sorted(k for k in before.keys() | after.keys() if before.get(k) != after.get(k))
save('module-comparison.json',dict(immutable_equal=all(before.get(k)==after.get(k)
    for k in before.keys() | after.keys() if not k.endswith('/load-records.jsonl')),
    raw_changed_paths=changed,no_module_action=True,mutable_load_log_separate=True))
r = read('user-0-capture-1-report.json')
save('capture-counts.json',dict(user0=dict(pid=r['pid'],legacy=dict(collections.Counter(x['status'] for x in r['rows'])),
    linked=dict(collections.Counter(x['status'] for x in r['linked_parity']['rows'])),
    runtime_envelope=dict(collections.Counter(x['status'] for x in r['runtime_envelope']['rows']))),
    user17=dict(measured_captures=0,reason='PERMISSION_UI_NOT_INTERACTIVE'),APPLIED_promotions=[],VERIFIED_promotions=[]))
summary = '''# Rung 1b coherent v3 final — authorized resume

Result: **NOT_VALIDATED**. User-0 smoke completed; user-17 location UI could not
complete through the authorized normal UI flow. The coherent-C measured triplet
and settled determinism comparison were **NOT RUN**. This closes Rung 1b at its
stop gate, with zero new APPLIED/VERIFIED promotions and no platform verdict.

## Scope and provenance

Repository main: d4f0a5cb5f4d70bacaeb0b8ecdff184eff154150. All pre-existing dirty
source and evidence preserved. No new product code/build/release/commit/push in
this resume. Diagnostics APK reused exactly from the passing optional-subscriber
retest build in ../2026-10-23-185155-rung1b-coherent-v3/. Both installed user APK
hashes independently matched artifact-provenance.json. No production APK launch
or installation, module action, reboot, account operation or shell grant.

The candidate's immutable module files match the validated Rung-1a manifest and
the before/after snapshots. Local ZIP provenance differs from the loaded artifact
and was NOT installed. See artifact-provenance.json and module-comparison.json.
Durable load-log bytes are mutable and recorded separately, not asserted identical.

Coherent C asset SHA-256:
`c78353910306cda7b0897f3310d2637a7568ec4925de8d63822645cf8a9e8f66`.
Full schema/coherence validation passes with only PATCH_AFTER_BUILD from the real
host dates. Shared WebView UA syntax and exact model/release checks were tested
in the preceding v3 gate. `large` is the explicitly approved classification from
measured screen dimensions/density, not a physical diagonal or screenLayout read.
Unavailable identifiers remain absent; no guessed SIM serial was introduced.

## UI setup and captures

UI setup was deliberately independent of the 60-second report poll. User 0 first
showed the Android debug-only 16 KB alignment compatibility warning for libprobe.so.
The observed OK button was acknowledged once (x439,y1865); Don't-show-again was
not selected. The ordinary location While-using-app and nearby Allow buttons were
then observed and tapped by their current resource IDs/bounds. The final hierarchy
showed the probe report, with no permission dialog. All UI XML and taps retained.
Setup captures are extra setup activity, not silently counted as measured repeats.

| User | Phase | Legacy | Linked | Marker/hash result |
| --- | --- | --- | --- | --- |
| 0 | cold smoke A, PID 15193 | 82 PASS / 7 OBSERVED / 0 FAIL / 0 BLOCKED | 13 PASS | completed, hashes agree |
| 17 | permission setup, PID 15666 | no completed capture | not measured | pending normal location UI |
| 17 | coherent C cold 1/2/settled 3 | NOT RUN | NOT RUN | no claim |

User-0 report contains 89 unique legacy rows, valid filesystem schema and matching
STARTED/COMPLETED request markers. Raw expected-profile hash matches the packaged
A asset; canonical body hash matches the temporary published profile. These two
hashes deliberately use different serialization and are checked independently.
The user-0 A smoke demonstrates normal capture after the fix, but does NOT prove
the missing-SIM-serial branch on a completed C measurement. That branch currently
has passing local regression tests only.

For user 17, the proven switch/wait/force-stop/launch path was used. After the
switch overlay settled, the hierarchy exposed the location permission dialog.
Its observed While-using-app control was tapped once at x540,y1546. It remained
present in subsequent nearby, settle-check and final-wait hierarchies. No second
tap, alternate coordinate, force-close, relaunch, permission reset or shell grant
was attempted. WindowManager identified GrantPermissionsActivity as focused;
InputDispatcher reported FocusedWindows: none and result=NO_WINDOW. Dispatch was
enabled, not frozen, and the physical/logical display was 1080x2400 @420dpi.
This is an observed window/input inconsistency, not a proven root cause. A
read-only Cyclone control-status lookup also reported DEVICE_DISCONNECTED while
ADB remained reachable; it performed no mutation or alternate launch/control.

See permission-input-state.json, stop-gate.json, ui-17-*.xml and setup launch/
markers. No speculative fix was applied. No user-17 report means no PASS set to
compare across three processes, no settled legacy-count equality claim, and no
new coherent-C parity/promotions. Historical reports are not relabelled.

## Cleanup and audits

Both temporary diagnostics entries and bodies removed through existing lock/CAS
cleanup. Diagnostics globally uninstalled; CE, DE and external probe directories
absent for both users. UI scratch XML removed. User 0 active at finish.
Persisted production before/after snapshots match EXACTLY: production APK SHA,
grants, stable AppOps, accessible binding/index bytes, published bodies and shares.
Normal diagnostics-only UI permission changes are documented separately and the
diagnostics package is now absent. Production permission/AppOps were not written.
No module replacement/removal, SELinux/policy changes, user creation/removal or
production storage rewrite occurred. Earlier failed evidence remains untouched.

## Local gate reference

No source changed after the passing v3 build except whitespace/docs/evidence.
The preceding gate remains: 784 app tests, 80 module tests, 148 per probe flavor,
1,303 combined forge/schema tests and 34 host probe tests, all passing. Diagnostics
build passed; exact source/build provenance and logs are in the preceding v3
directory. Current test-counts.json copied with explicit origin. Current git diff
and cached diff checks are captured separately; no files staged.

## Open questions — instance UA and UI

Verbatim retained user-0 instance-UA report fields:
`id=envelope.webview.instanceUa`, `status=MISSING`, `reason=RuntimeException`.
No full RUNTIME_EXCEPTION stack is present in this resume's retained target-PID
logs. No stack is invented or borrowed from an older capture. The surface remains
**UNVERIFIED** in the ledger; the raw MISSING state stays unchanged. The UI-blocked
user-17 setup never reached its instance-UA reader. The earlier NullPointerException
capture failure concerned expected subscriber-ID derivation, not a WebView cause.

The user-17 permission controller's missing input window remains a blocker. This
final authorization does not permit further fixes/launch variants or bypasses.
No certification, broader compatibility, platform verdict or VERIFIED coverage
claim follows from this run.
'''
(O/'summary.md').write_text(summary,encoding='utf8')
(O/'exact-commands.txt').write_text('''# Executed from C:/Users/Agent/Cyclone/cloak-repo
uv run --offline -- python docs/runtime-evidence/2026-10-23-191424-rung1b-coherent-v3-final/prepare.py
uv run --offline --with jsonschema -- python -X utf8 docs/runtime-evidence/2026-10-23-191424-rung1b-coherent-v3-final/run_ops.py install
uv run --offline --with jsonschema -- python docs/runtime-evidence/2026-10-23-191424-rung1b-coherent-v3-final/ui_setup.py start 0
# UI inspect/tap/finish commands and current hierarchy tags are retained in scripts, XML and commands.jsonl. One compatibility-warning OK tap at x439,y1865.
uv run --offline --with jsonschema -- python -X utf8 docs/runtime-evidence/2026-10-23-191424-rung1b-coherent-v3-final/run_ops.py capture 0 1
uv run --offline --with jsonschema -- python docs/runtime-evidence/2026-10-23-191424-rung1b-coherent-v3-final/ui_setup.py start 17
# User17 location-ready hierarchy, one observed While-using-app tap, nearby/settle-check/final-wait hierarchy reads; no alternate taps.
uv run --offline -- python docs/runtime-evidence/2026-10-23-191424-rung1b-coherent-v3-final/preserve_stop.py
uv run --offline --with jsonschema -- python docs/runtime-evidence/2026-10-23-191424-rung1b-coherent-v3-final/run_ops.py cleanup
git diff --check
git diff --cached --check
# Prior local build/test commands unchanged: ../2026-10-23-185155-rung1b-coherent-v3/exact-commands.txt
''',encoding='utf8')
for args,name in [(['git','diff','--binary'],'run-only-dirty-prerequisites.patch'),
                  (['git','status','--short'],'git-status-final.txt'),
                  (['git','rev-parse','HEAD'],'git-head.txt')]:
    (O/name).write_bytes(subprocess.run(args,cwd=R,capture_output=True).stdout)
for name in ('optional-subscriber-retest.log','local-python-retest.log','local-android-retest.log','probe-host-tests.log'):
    (O/('prior-'+name)).write_bytes((P/name).read_bytes())
save('prior-gate-reference.json',dict(source=str(P),no_new_code_build_in_resume=True,
    APK_source='optional-subscriber-retest.log',fixture_validation='fixture-validation.json'))
paths = sorted(p for p in O.rglob('*') if p.is_file() and '__pycache__' not in p.parts
               and p.name not in ('evidence-bundle.zip','evidence-bundle.sha256'))
with zipfile.ZipFile(O/'evidence-bundle.zip','w',zipfile.ZIP_DEFLATED) as z:
    for p in paths: z.write(p,p.relative_to(O))
h=hashlib.sha256((O/'evidence-bundle.zip').read_bytes()).hexdigest()
(O/'evidence-bundle.sha256').write_text(h+'  evidence-bundle.zip\n',encoding='utf8')
print(json.dumps(dict(result='NOT_VALIDATED',files=len(paths),sha256=h)))
