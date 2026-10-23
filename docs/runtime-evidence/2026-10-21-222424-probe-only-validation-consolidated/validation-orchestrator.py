import collections
import datetime
import hashlib
import json
import re
import subprocess
import time
import zipfile
from pathlib import Path

import jsonschema

ROOT = Path('C:/Users/Agent/Cyclone/cloak-repo')
ADB = 'C:/Users/Agent/android-sdk/platform-tools/adb.exe'
SERIAL = '3B171FDJH0061G'
PACKAGE = 'dev.cyclone.cloak.probe.diag'
APK = ROOT/'android/probe/build/outputs/apk/diagnostics/debug/probe-diagnostics-debug.apk'
STAMP = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=2))).strftime('%Y-%m-%d-%H%M%S')
OUT = ROOT/'docs/runtime-evidence'/f'{STAMP}-probe-only-validation'
OUT.mkdir(parents=True, exist_ok=False)
COMMANDS = []

def save(name, value):
    (OUT/name).write_text(json.dumps(value, indent=2)+'\n', encoding='utf8')

def adb(*args, timeout=30, check=True):
    completed = subprocess.run([ADB, '-s', SERIAL, *args], capture_output=True, text=True,
                               encoding='utf8', errors='replace', timeout=timeout)
    COMMANDS.append(dict(args=list(args), exit_code=completed.returncode))
    if check and completed.returncode:
        raise RuntimeError('ADB_COMMAND_FAILED: '+ ' '.join(args[:4]))
    return completed.stdout.strip()

def shell(command, **kwargs):
    return adb('shell', command, **kwargs)

def package_info(package):
    result = {}
    text = shell('dumpsys package '+package)
    version = re.search(r'versionName=(\S+)', text)
    code = re.search(r'versionCode=(\d+)', text)
    result['version'] = version[1] if version else None
    result['version_code'] = int(code[1]) if code else None
    for user in (0, 17):
        path = shell(f'pm path --user {user} {package}', check=False)
        paths = [line.removeprefix('package:') for line in path.splitlines() if line.startswith('package:')]
        result[str(user)] = dict(paths=paths, apk_hashes=[shell('sha256sum '+p).split()[0] for p in paths])
    # Stable grant/mode facts only; no account data or volatile usage timestamps.
    result['permissions'] = sorted(set(re.findall(r'([\w.]+permission\.[\w.]+): granted=(true|false)', text)))
    return result

def redact(report):
    safe = {key: report[key] for key in ('format','fixture','package','uid','pid','request_id',
        'session_generation','profile_sha256','status','reason','error','failed_row_id') if key in report}
    safe['rows'] = [{key: row[key] for key in ('id','source','status','reason','expected_sha256',
        'actual_sha256','duration_ms') if key in row} for row in report.get('rows', [])]
    if 'filesystem_observations' in report:
        safe['filesystem_observations'] = report['filesystem_observations']
    return safe

schema = json.loads((ROOT/'schema/filesystem-observations.schema.json').read_text())
results = []
capture_failures = []
installed = False
failure = None
before = None
try:
    assert adb('get-state') == 'device'
    states = {str(u): shell(f'am get-started-user-state {u}') for u in (0, 17)}
    save('user-states.json', states)
    if any(s != 'RUNNING_UNLOCKED' for s in states.values()):
        raise RuntimeError('REQUIRED_USER_NOT_UNLOCKED')
    before = package_info('dev.cyclone.cloak')
    save('production-before.json', before)
    save('diagnostics-before.json', package_info(PACKAGE))
    source = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    diff = subprocess.check_output(['git', 'diff', '--binary'], cwd=ROOT)
    save('provenance.json', dict(commit=source, dirty_worktree=True, tracked_diff_sha256=hashlib.sha256(diff).hexdigest(),
        apk_sha256=hashlib.sha256(APK.read_bytes()).hexdigest(), apk_bytes=APK.stat().st_size,
        source='locally built diagnostics APK with uncommitted observation changes',
        module_provenance='Not inspected or changed; installed binary identity not established',
        permissions='No grants, revokes, AppOps changes or runtime permission prompts requested'))
    (OUT/'source.patch').write_bytes(diff)
    for path in ('android/probe/src/main/java/dev/cyclone/cloak/probe/FilesystemObservation.java',
                 'android/probe/src/test/java/dev/cyclone/cloak/probe/FilesystemObservationTest.java',
                 'schema/filesystem-observations.schema.json'):
        target = OUT/'source'/path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes((ROOT/path).read_bytes())
    output = adb('install', '--user', '0', '-r', str(APK), timeout=120)
    save('install.json', dict(output=output))
    if 'Success' not in output: raise RuntimeError('DIAGNOSTICS_INSTALL_FAILED')
    installed = True
    save('user-17-install-existing.json', dict(output=shell(f'cmd package install-existing --user 17 {PACKAGE}')))
    info = package_info(PACKAGE)
    expected = hashlib.sha256(APK.read_bytes()).hexdigest()
    if any(info[str(u)]['apk_hashes'] != [expected] for u in (0, 17)):
        raise RuntimeError('INSTALLED_APK_HASH_MISMATCH')
    save('diagnostics-installed.json', info)
    for user in (17,):
        for repeat in (1, 2):
            request = f'filesystem-{STAMP}-{user}-{repeat}'
            launch = shell(f'am start --user {user} -n {PACKAGE}/dev.cyclone.cloak.probe.DiagnosticsProbeActivity '
                f'--es request_id {request} --es fixture a --ei expected_user {user} --ez no_permission_prompt true')
            save(f'user-{user}-capture-{repeat}-launch.json', dict(output=launch, request_id=request))
            report = None
            deadline = time.monotonic()+60
            while time.monotonic() < deadline:
                output = shell(f'run-as {PACKAGE} --user {user} cat files/report.json', check=False)
                try: candidate = json.loads(output)
                except (ValueError, TypeError): candidate = {}
                if candidate.get('request_id') == request:
                    report = candidate
                    break
                time.sleep(1)
            if report is None: raise RuntimeError(f'PROBE_REPORT_TIMEOUT_USER_{user}_CAPTURE_{repeat}')
            safe = redact(report)
            save(f'user-{user}-capture-{repeat}.json', safe)
            if report.get('status') == 'FAIL' or 'error' in report:
                capture_failures.append(dict(user=user,capture=repeat,reason=report.get('reason'),error=report.get('error')))
                failure = 'PROBE_CAPTURE_FAILED'
                save('capture-failures.json', capture_failures)
                print(f'USER {user} CAPTURE {repeat}: CAPTURE_FAILED '+str(report.get('error')),flush=True)
                continue
            if report.get('package') != PACKAGE or int(report['uid'])//100000 != user:
                raise RuntimeError('REPORTED_USER_OR_PACKAGE_MISMATCH')
            jsonschema.validate(report.get('filesystem_observations'), schema)
            rows = report['filesystem_observations']['rows']
            results.append(dict(user=user, capture=repeat, request_id=request, pid=report['pid'],
                counts=dict(collections.Counter(r['status'] for r in rows)), rows=rows,
                legacy_counts=dict(collections.Counter(r.get('status') for r in report.get('rows', [])))))
            save('capture-results.json', results)
            print(f'USER {user} CAPTURE {repeat}: '+json.dumps(results[-1]['counts']), flush=True)
except Exception as error:
    failure = str(error) if isinstance(error, RuntimeError) else type(error).__name__
    save('failure.json', dict(reason=failure))
finally:
    cleanup = dict(uninstall_requested=installed)
    if installed:
        try:
            cleanup['uninstall_output'] = adb('uninstall', PACKAGE, timeout=120)
            cleanup['remaining_paths'] = {str(u): shell(f'pm path --user {u} {PACKAGE}', check=False) for u in (0,17)}
            cleanup['removed'] = cleanup['uninstall_output'] == 'Success' and not any(cleanup['remaining_paths'].values())
        except Exception as error:
            cleanup['error_type'] = type(error).__name__
    save('cleanup.json', cleanup)
    try:
        after = package_info('dev.cyclone.cloak')
        save('production-after.json', after)
        unchanged = before == after
    except Exception:
        unchanged = False
    stability = []
    for user in (0,17):
        captures = [r for r in results if r['user'] == user]
        for id in ('diagnostics.filesystem.build_prop','diagnostics.filesystem.proc_version'):
            rows = [next(r for r in c['rows'] if r['id'] == id) for c in captures]
            measured = len(rows) == 2 and all(r['status'] == 'OBSERVED' for r in rows)
            stability.append(dict(user=user,id=id,result=('IDENTICAL' if rows[0]['sha256']==rows[1]['sha256'] else 'CHANGED') if measured else 'NOT_COMPARABLE'))
    save('determinism.json', stability)
    save('commands.json', COMMANDS)
    completed = not failure and len(results)==4 and cleanup.get('removed') and unchanged
    completed = completed and not any(r['result']=='CHANGED' for r in stability)
    summary = ['# Probe-only validation', '', 'Result: '+('COMPLETE' if completed else 'INCOMPLETE'),
        'Failure: '+str(failure), 'APK SHA-256: '+hashlib.sha256(APK.read_bytes()).hexdigest(),
        'Production APK/version/permission snapshot unchanged: '+str(unchanged),
        'Diagnostics uninstall confirmed: '+str(cleanup.get('removed',False)), '',
        '| User | Capture | OBSERVED | BLOCKED | MISSING |', '| --- | --- | --- | --- | --- |']
    for r in results:
        summary.append(f"| {r['user']} | {r['capture']} | {r['counts'].get('OBSERVED',0)} | {r['counts'].get('BLOCKED',0)} | {r['counts'].get('MISSING',0)} |")
    summary += ['', 'Determinism:']+[f"- user {r['user']} {r['id']}: {r['result']}" for r in stability]
    summary += ['', 'Limits: probe-only caller-visible observations; no parity verdict or VERIFIED promotion.',
        'No module install, root shell, reboot, binding overlay, user switch, permission grant/reset or production app launch was performed.',
        'Private production stores/module files were not independently audited. APK/version/permission equality is not proof of private-store byte equality.',
        'Installed module provenance was not established; the handoff alpha.28 description is not independently confirmed.',
        'The existing diagnostics app was updated and then globally uninstalled as requested; its old private data is not restored.',
        'Legacy probe comparisons used the packaged fixture without creating a runtime binding and are not interpreted as parity validation.',
        'Reports are redacted exports retaining filesystem digests/counts and existing row statuses/digests only. No raw file content retained.']
    (OUT/'summary.md').write_text('\n'.join(summary)+'\n',encoding='utf8')
    bundle=OUT/'evidence-bundle.zip'
    with zipfile.ZipFile(bundle,'w',zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(OUT.rglob('*')):
            if path.is_file() and path!=bundle: archive.write(path,path.relative_to(OUT))
    (OUT/'evidence-bundle.sha256').write_text(hashlib.sha256(bundle.read_bytes()).hexdigest()+'  evidence-bundle.zip\n')
    print('EVIDENCE='+str(OUT),flush=True)
    print('RESULT='+('COMPLETE' if completed else 'INCOMPLETE'),flush=True)
