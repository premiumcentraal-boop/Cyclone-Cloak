"""Standalone, cold-only diagnostics validation for existing users 0 and 17.

Run: uv run --offline --with jsonschema -- python -X utf8 tools/run_standalone_probe_validation.py
Installs only the diagnostics APK, suppresses permission prompts, force-stops only
that package before each read, and uninstalls it in finally. No root or overlays.
User 0 receives one smoke capture; user 17 receives two captures.
Every invocation uses a fresh dated evidence directory. Missing reports retain
request-bound marker diagnosis before cleanup. Stop on the first failed capture.
"""
import collections
import datetime
import hashlib
import json
import re
import subprocess
import time
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADB = 'C:/Users/Agent/android-sdk/platform-tools/adb.exe'
SERIAL = '3B171FDJH0061G'
PACKAGE = 'dev.cyclone.cloak.probe.diag'
APK = ROOT/'android/probe/build/outputs/apk/diagnostics/debug/probe-diagnostics-debug.apk'
STAMP = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=2))).strftime('%Y-%m-%d-%H%M%S')
OUT = ROOT/'docs/runtime-evidence'/f'{STAMP}-user17-launch-validation'
COMMANDS = []

def save(name, value):
    (OUT/name).write_text(json.dumps(value, indent=2)+'\n', encoding='utf8')

def adb_result(*args, timeout=30):
    completed = subprocess.run([ADB, '-s', SERIAL, *args], capture_output=True, text=True,
                               encoding='utf8', errors='replace', timeout=timeout)
    COMMANDS.append(dict(args=list(args), exit_code=completed.returncode))
    return dict(stdout=completed.stdout, stderr=completed.stderr, exit_code=completed.returncode)


def adb(*args, timeout=30, check=True):
    result = adb_result(*args, timeout=timeout)
    if check and result['exit_code']:
        raise RuntimeError('ADB_COMMAND_FAILED: '+ ' '.join(args[:4]))
    return result['stdout'].strip()

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
        'session_generation','profile_sha256','status','reason','error','failed_row_id','failure_details','filesystem_reader_failures') if key in report}
    safe['rows'] = [{key: row[key] for key in ('id','source','status','reason','expected_sha256',
        'actual_sha256','duration_ms') if key in row} for row in report.get('rows', [])]
    if 'filesystem_observations' in report:
        safe['filesystem_observations'] = report['filesystem_observations']
    for section in ('runtime_envelope', 'linked_parity', 'device_reality', 'reality_runtime'):
        value = report.get(section)
        if isinstance(value, dict):
            safe[section] = {k:v for k,v in value.items() if k in ('format','status','reason','counts')}
            safe[section]['rows'] = [{k:v for k,v in row.items() if k in ('id','field_path','surface','status','reason','expected_sha256','actual_sha256','duration_ms')} for row in value.get('rows', [])]
    return safe

def cold_launch(shell_command, user, request):
    """Only stop the diagnostics package in the selected existing user."""
    shell_command(f'am force-stop --user {user} {PACKAGE}')
    return shell_command(f'am start --user {user} -n {PACKAGE}/dev.cyclone.cloak.probe.DiagnosticsProbeActivity '
        f'--es request_id {request} --es fixture a --ei expected_user {user} --ez no_permission_prompt true')


def evidence_paths(user, name):
    if type(user) is not int or user < 0 or name not in ('report.json','capture-start.json','capture-end.json'):
        raise ValueError('INVALID_EVIDENCE_PATH')
    return (f'/storage/emulated/{user}/Android/data/{PACKAGE}/files/standalone-evidence/{name}',
            f'/data/user/{user}/{PACKAGE}/files/{name}')


def read_state(result):
    text = result['stderr'] + result['stdout']
    if result['exit_code'] == 0:
        return 'READABLE'
    if 'Permission denied' in text or 'not allowed' in text:
        return 'ACCESS_DENIED_EXISTENCE_UNKNOWN'
    if 'No such file or directory' in text:
        return 'ABSENT'
    return 'READ_ERROR'


def read_evidence(user, name):
    external, private = evidence_paths(user, name)
    visibility = []
    selected = None
    # Explicit per-user absolute paths; never rely on run-as's initial directory.
    for transport, command in (
        ('external_shell', f'cat {external}'),
        ('external_run_as', f'run-as {PACKAGE} --user {user} cat {external}'),
        ('private_run_as', f'run-as {PACKAGE} --user {user} cat {private}')):
        result = adb_result('shell', command)
        state = read_state(result)
        visibility.append(dict(transport=transport, path=external if transport.startswith('external') else private,
                               state=state, exit_code=result['exit_code'], stderr=result['stderr'][:2048]))
        if state == 'READABLE':
            try: candidate = json.loads(result['stdout'])
            except (ValueError,TypeError):
                visibility[-1]['state'] = 'MALFORMED'
                continue
            if isinstance(candidate, dict) and selected is None: selected = candidate
    return selected, visibility


def visibility_diagnosis(diagnosis, visibility):
    result = dict(diagnosis)
    if not result['start_seen'] and any(v['state'] in ('ACCESS_DENIED_EXISTENCE_UNKNOWN','READ_ERROR') for v in visibility):
        result['reason'] = 'CAPTURE_START_UNOBSERVABLE'
    result['visibility'] = visibility
    return result


def launch_snapshot(user):
    package = shell('dumpsys package '+PACKAGE)
    match = re.search(r'(?:userId|appId)=(\d+)', package)
    return dict(pidof=adb_result('shell','pidof '+PACKAGE),
        processes=[line for line in shell('ps -A -o UID,PID,NAME').splitlines() if PACKAGE in line][:20],
        package_path=adb_result('shell',f'pm path --user {user} {PACKAGE}'),
        target_user_state=[line.strip() for line in package.splitlines() if re.match(rf'\s*User {user}:',line)],
        target_uid=user*100000+int(match[1]) if match else None,
        run_as_identity=adb_result('shell',f'run-as {PACKAGE} --user {user} id'),
        run_as_directory=adb_result('shell',f'run-as {PACKAGE} --user {user} pwd'))


def launch_with_evidence(user, request):
    before = launch_snapshot(user)
    before['device_clock'] = shell("date '+%m-%d %H:%M:%S.000'")
    stopped = adb_result('shell', f'am force-stop --user {user} {PACKAGE}')
    if stopped['exit_code']: raise RuntimeError('DIAGNOSTICS_FORCE_STOP_FAILED')
    launch = adb_result('shell',f'am start --user {user} -n {PACKAGE}/dev.cyclone.cloak.probe.DiagnosticsProbeActivity '
        f'--es request_id {request} --es fixture a --ei expected_user {user} --ez no_permission_prompt true')
    return dict(request_id=request, cold_start=True, before=before, force_stop=stopped, am_start=launch,
                immediately_after=launch_snapshot(user))


def finish_launch_evidence(user, launch):
    launch['after_capture_wait'] = launch_snapshot(user)
    uid = launch['before']['target_uid']
    result = adb_result('shell',f'logcat -d -t 300 -v threadtime --uid={uid}') if uid else dict(stdout='',stderr='UID_UNAVAILABLE',exit_code=1)
    pids=set()
    for snapshot in ('before','immediately_after','after_capture_wait'):
        pids.update(launch[snapshot]['pidof']['stdout'].split())
    since = launch['before']['device_clock']
    lines=[line for line in result['stdout'].splitlines() if line[:18] >= since]
    launch['logcat'] = dict(exit_code=result['exit_code'], stderr=result['stderr'], lines=lines[-300:],
                            policy='bounded target-UID logcat since launch, no unrelated UID output')
    system = adb_result('shell','logcat -d -t 500 -v threadtime')
    launch['launch_system_logcat'] = dict(exit_code=system['exit_code'], stderr=system['stderr'],
        lines=[line for line in system['stdout'].splitlines() if line[:18] >= since and PACKAGE in line][-100:])
    return launch


def safe_marker(marker):
    if not isinstance(marker, dict):
        return None
    return {k:v for k,v in marker.items() if k in ('format','request_id','session_generation','uid','pid','phase','outcome','timestamp_ms')}


def marker_diagnosis(request, start, end):
    def valid(marker, phase):
        return (isinstance(marker, dict) and marker.get('format') == 1
            and marker.get('request_id') == request and marker.get('phase') == phase
            and marker.get('outcome') in (('STARTED',) if phase == 'start' else ('COMPLETED','FAILED'))
            and all(type(marker.get(k)) is int and marker[k] > 0 for k in ('uid','pid','session_generation','timestamp_ms')))
    started, ended = valid(start, 'start'), valid(end, 'end')
    if ended and not started:
        reason = 'END_WITHOUT_MATCHING_START'
    elif not started:
        reason = 'CAPTURE_NEVER_STARTED'
    elif not ended:
        reason = 'CAPTURE_STARTED_NO_END'
    elif any(start[k] != end[k] for k in ('uid','pid','session_generation')):
        reason = 'CAPTURE_MARKER_BINDING_MISMATCH'
        ended = False
    else:
        reason = 'CAPTURE_ENDED_' + end['outcome']
    return dict(start_seen=started, end_seen=ended, reason=reason)


def main():
    import jsonschema
    OUT.mkdir(parents=True, exist_ok=False)
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
            cold_start_policy='force-stop diagnostics package before every capture',
            source='locally built diagnostics APK with cold-capture markers and existing SIM/probe hardening; no module build or release change',
            module_provenance='Not inspected or changed; installed binary identity not established',
            permissions='No grants, revokes, AppOps changes or runtime permission prompts requested'))
        (OUT/'source.patch').write_bytes(diff)
        for path in ('android/probe/src/main/java/dev/cyclone/cloak/probe/FilesystemObservation.java',
                     'android/probe/src/test/java/dev/cyclone/cloak/probe/FilesystemObservationTest.java',
                     'schema/filesystem-observations.schema.json',
                     'android/probe/src/main/java/dev/cyclone/cloak/probe/RuntimeEnvelopeReport.java',
                     'android/probe/src/test/java/dev/cyclone/cloak/probe/SimStateExpectationTest.java'):
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
        for user in (0, 17):
            for repeat in ((1,) if user == 0 else (1, 2)):
                request = f'filesystem-{STAMP}-{user}-{repeat}'
                launch = launch_with_evidence(user, request)
                save(f'user-{user}-capture-{repeat}-launch.json', launch)
                report = None
                deadline = time.monotonic()+60
                while time.monotonic() < deadline:
                    candidate, report_visibility = read_evidence(user, 'report.json')
                    candidate = candidate or {}
                    if candidate.get('request_id') == request:
                        report = candidate
                        break
                    time.sleep(1)
                markers = {}
                for phase in ('start', 'end'):
                    marker, visibility = read_evidence(user, f'capture-{phase}.json')
                    markers[phase] = safe_marker(marker)
                    markers[phase + '_visibility'] = visibility
                diagnosis = visibility_diagnosis(marker_diagnosis(request, markers['start'], markers['end']), markers['start_visibility'])
                save(f'user-{user}-capture-{repeat}-report-visibility.json', report_visibility)
                save(f'user-{user}-capture-{repeat}-launch.json', finish_launch_evidence(user, launch))
                save(f'user-{user}-capture-{repeat}-markers.json', dict(request_id=request, markers=markers, diagnosis=diagnosis))
                if report is None:
                    save('timeout-diagnosis.json', dict(user=user, capture=repeat, request_id=request, **diagnosis))
                    raise RuntimeError(f'PROBE_REPORT_TIMEOUT_USER_{user}_CAPTURE_{repeat}: '+diagnosis['reason'])
                if not diagnosis['start_seen'] or not diagnosis['end_seen']:
                    save(f'user-{user}-capture-{repeat}.json', redact(report))
                    raise RuntimeError('CAPTURE_MARKERS_INCOMPLETE')
                safe = redact(report)
                save(f'user-{user}-capture-{repeat}.json', safe)
                if report.get('status') == 'FAIL' or 'error' in report:
                    if report.get('filesystem_observations') is not None:
                        jsonschema.validate(report['filesystem_observations'], schema)
                        save('partial-filesystem-results.json', dict(user=user, capture=repeat,
                            counts=dict(collections.Counter(r['status'] for r in report['filesystem_observations']['rows'])),
                            schema_valid=True, full_capture_completed=False))
                    raise RuntimeError(f'PROBE_CAPTURE_FAILED_USER_{user}_CAPTURE_{repeat}')
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
        completed = not failure and len(results)==3 and cleanup.get('removed') and unchanged
        completed = completed and not any(r['result']=='CHANGED' for r in stability)
        summary = ['# User-17 launch standalone validation', '', 'Result: '+('COMPLETE' if completed else 'INCOMPLETE'),
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
            'The diagnostics-only installation was globally uninstalled at cleanup; no private probe files are retained on-device.',
            'Legacy probe comparisons used the packaged fixture without creating a runtime binding and are not interpreted as parity validation.',
            'Cold-start policy: diagnostics-only force-stop before every capture; request/process-bound start/end markers preserved.',
            'Gate: any failed capture stops the run. No additional code fixes follow the device failure.',
            'Reports are redacted exports retaining filesystem digests/counts and existing row statuses/digests only. No raw file content retained.']
        (OUT/'summary.md').write_text('\n'.join(summary)+'\n',encoding='utf8')
        bundle=OUT/'evidence-bundle.zip'
        with zipfile.ZipFile(bundle,'w',zipfile.ZIP_DEFLATED) as archive:
            for path in sorted(OUT.rglob('*')):
                if path.is_file() and path!=bundle: archive.write(path,path.relative_to(OUT))
        (OUT/'evidence-bundle.sha256').write_text(hashlib.sha256(bundle.read_bytes()).hexdigest()+'  evidence-bundle.zip\n')
        print('EVIDENCE='+str(OUT),flush=True)
        print('RESULT='+('COMPLETE' if completed else 'INCOMPLETE'),flush=True)
    return completed


if __name__ == '__main__':
    raise SystemExit(0 if main() else 1)
