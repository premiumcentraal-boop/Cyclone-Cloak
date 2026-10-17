"""Diagnostics-only overlay reversibility evidence; APK/base harness stay at alpha.18.

Never starts/unlocks users, grants permissions, or installs production packages.
Unbound report FAIL statuses compare against fixture expectations, not restoration.
Only paired baseline-versus-cold actual hashes decide restoration.
"""
import argparse
from collections import Counter
import hashlib
import importlib
import json
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import time
import uuid
from zipfile import ZipFile, ZIP_DEFLATED

TAG = 'v0.11.0-alpha.18'
COMMIT = 'fb1e3c6ae2e4caca56778a54d8025dba696b53b8'
PACKAGE = 'dev.cyclone.cloak.probe.diag'
EXPECTED_DIAGNOSTIC_APPOP_ADDITIONS = {
    'READ_DEVICE_IDENTIFIERS': 'deny',
    'USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER': 'default',
}


def phases(users):
    if len(users) != 2 or users[0] != 0 or type(users[1]) is not int or users[1] <= 0:
        raise ValueError('INVALID_USERS')
    return [(mode, u, mode != 'post-removal-warm') for mode in
            ('baseline-cold', 'bound-cold', 'post-removal-warm', 'post-removal-cold') for u in users]


def check_metadata(report, item, cold, expected_ids):
    if (report.get('package') != PACKAGE or type(report.get('uid')) is not int
            or report['uid'] // 100000 != item['user']
            or report.get('fixture') != item['fixture']
            or report.get('profile_sha256') != item['sha256']):
        raise ValueError('REPORT_USER_FIXTURE_OR_HASH_MISMATCH')
    ids = [r['id'] for r in report['rows']]
    if len(ids) != len(expected_ids) or set(ids) != set(expected_ids):
        raise ValueError('REPORT_INCOMPLETE_OR_DUPLICATE')
    if cold and report.get('process_reused') is not False:
        raise ValueError('COLD_PROCESS_REUSED')


def restoration(before, after):
    """Never turn blocked/missing/null evidence into restoration PASS."""
    if (before['android_user'] != after['android_user']
            or before['artifact'] != after['artifact']
            or before['profile_sha256'] != after['profile_sha256']
            or before['apk_sha256'] != after['apk_sha256']
            or before['request_id'] == after['request_id']
            or not before['cold_restart_requested'] or not after['cold_restart_requested']):
        raise ValueError('RESTORATION_METADATA_MISMATCH')
    left = {r['id']: r for r in before['rows']}
    right = {r['id']: r for r in after['rows']}
    rows = []
    for key in sorted(left.keys() | right.keys()):
        a, b = left.get(key), right.get(key)
        state, reason = 'FAIL', 'ROW_SET_CHANGED'
        if a and b:
            if 'BLOCKED' in (a['status'], b['status']):
                state, reason = 'BLOCKED', 'PERMISSION_OR_API_LIMITATION'
            elif 'MISSING' in (a['status'], b['status']):
                state, reason = 'OBSERVED', 'VALUE_UNAVAILABLE'
            elif a.get('expectation_policy') == 'EXTERNAL_STATE' or b.get('expectation_policy') == 'EXTERNAL_STATE':
                state, reason = 'OBSERVED', 'DYNAMIC_EXTERNAL_STATE'
            elif not a.get('actual_sha256') or not b.get('actual_sha256') or a.get('actual_available') is False or b.get('actual_available') is False:
                state, reason = 'OBSERVED', 'INSUFFICIENT_VALUE_EVIDENCE'
            elif a.get('actual_sha256') == hashlib.sha256(b'null').hexdigest() or b.get('actual_sha256') == hashlib.sha256(b'null').hexdigest():
                state, reason = 'OBSERVED', 'NULL_VALUE_EVIDENCE'
            elif a['actual_sha256'] == b['actual_sha256']:
                state, reason = 'PASS', 'BASELINE_RESTORED'
            elif a.get('expectation_policy') in {'TYPED', 'ENUM', 'RANGE'} and b.get('expectation_policy') == a.get('expectation_policy'):
                state, reason = 'OBSERVED', 'LOCAL_DEVICE_STATE_CHANGED'
            else:
                state, reason = 'FAIL', 'COLD_VALUE_NOT_RESTORED'
        rows.append(dict(id=key, status=state, reason=reason,
                         before_sha256=a.get('actual_sha256') if a else None,
                         after_sha256=b.get('actual_sha256') if b else None))
    counts = dict(Counter(r['status'] for r in rows))
    return dict(android_user=before['android_user'], result='FAIL' if counts.get('FAIL') else 'RESTORED_WITH_LIMITATIONS',
                counts=counts, rows=rows)


def cleanup_private(device, users):
    for user in users:
        device.shell(f'am force-stop --user {user} {PACKAGE}')
        device.shell(f'run-as {PACKAGE} --user {user} sh -c ' + shlex.quote(
            'rm -f files/expected-profile.json files/report.json files/report.json.*.tmp'))


def effective_permission_state(before, after):
    """Allow only the read-created diagnostics records; reject every other delta."""
    if (set(before) != {PACKAGE, 'dev.cyclone.cloak'} or set(before.get(PACKAGE, {})) != set(after.get(PACKAGE, {}))
            or set(before.get('dev.cyclone.cloak', {})) != set(after.get('dev.cyclone.cloak', {}))):
        return False
    production = before['dev.cyclone.cloak']
    if production != after['dev.cyclone.cloak']:
        return False
    old, new = before[PACKAGE], after[PACKAGE]
    if set(old) != set(new) or old.get('grants') != new.get('grants') or old.get('install_grants') != new.get('install_grants'):
        return False
    if set(old.get('appop_modes', {})) != set(new.get('appop_modes', {})):
        return False
    for user in old.get('appop_modes', {}):
        additions = [entry for entry in new['appop_modes'][user] if entry not in old['appop_modes'][user]]
        retained = [entry for entry in old['appop_modes'][user] if entry in new['appop_modes'][user]]
        expected = sorted(EXPECTED_DIAGNOSTIC_APPOP_ADDITIONS.items())
        if additions != expected or retained != old['appop_modes'][user]:
            return False
        if len(new['appop_modes'][user]) != len(old['appop_modes'][user]) + len(expected):
            return False
    return True


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source-checkout', type=Path, required=True)
    p.add_argument('--adb', required=True)
    p.add_argument('--serial', required=True)
    p.add_argument('--secondary', type=int, default=17)
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    users = [0, args.secondary]
    sequence = phases(users)
    source = args.source_checkout.resolve()
    def git(*cmd):
        return subprocess.check_output(['git', *cmd], cwd=source, text=True).strip()
    if git('rev-parse', 'HEAD') != COMMIT or git('rev-parse', TAG+'^{}') != COMMIT or git('status', '--porcelain', '--untracked-files=no'):
        p.error('Exact clean alpha.18 checkout required')
    if subprocess.run(['git', 'symbolic-ref', '-q', 'HEAD'], cwd=source, capture_output=True).returncode == 0:
        p.error('Detached checkout required')
    sys.path.insert(0, str(source/'tools'))
    pc = importlib.import_module('profile_check')
    if Path(pc.__file__).resolve() != source/'tools/profile_check.py':
        p.error('Wrong base harness')
    apk = source/'android/probe/build/outputs/apk/diagnostics/debug/probe-diagnostics-debug.apk'
    apk_hash = hashlib.sha256(apk.read_bytes()).hexdigest()
    ids = json.loads((source/'android/probe/src/main/assets/report-row-ids.json').read_text())
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    (out/'.gitignore').write_text('*\n')
    d = pc.Device(args.adb, args.serial, 90)
    m = dict(format=1, run_id=uuid.uuid4().hex, user=0, target_user=args.secondary,
             full_profile=False, phases={}, started_at=pc.utc(), started_epoch=time.time(),
             source_tag=TAG, source_commit=COMMIT, apk_sha256=apk_hash, overlay_active=False)
    h = pc.Harness(out, m, d, apk)
    selections, reports, errors = [], [], []
    before = permissions_before = original_user = None
    captures_started = False

    def permission_state():
        packages = {}
        for package in (PACKAGE, 'dev.cyclone.cloak'):
            text = d.shell('dumpsys package '+package).stdout
            grants = {}
            for match in re.finditer(r'^    User (\d+):.*?(?=^    User |^\S|\Z)', text, re.M | re.S):
                grants[match[1]] = {name:dict(granted=value=='true', flags=flags.strip()) for name,value,flags in
                    re.findall(r'(android\.permission\.[A-Z_]+): granted=(true|false), flags=\[([^\]]*)\]', match[0])}
            modes = {}
            for user in users:
                text_modes = d.shell(f'cmd appops get --user {user} {package}', check=False).stdout
                modes[str(user)] = sorted(re.findall(r'(?m)^\s*((?:Uid mode: )?[A-Z_]+): ([a-z]+)', text_modes))
            install = dict(re.findall(r'(android\.permission\.[A-Z_]+): granted=(true|false)', text.split('    User ')[0]))
            packages[package] = dict(grants=grants, appop_modes=modes, install_grants=install)
        return packages

    def audit():
        raw, index = d.index()
        if any(f'{u}/{PACKAGE}' in index['entries'] for u in users):
            raise ValueError('EXISTING_DIAGNOSTICS_BINDING')
        def files(command):
            entries = {}
            for line in d.root(command).stdout.splitlines():
                sha, path = line.split(None, 1)
                if not re.fullmatch('[a-f0-9]{64}', sha): raise ValueError('INVALID_AUDIT_HASH')
                entries[hashlib.sha256(path.strip().encode()).hexdigest()] = sha
            return dict(count=len(entries), files=entries, sha256=pc.digest(entries))
        return dict(production_entry_count=len(index['entries']), index_sha256=pc.digest(index),
                    index_bytes_sha256=hashlib.sha256(raw.encode()).hexdigest(),
                    entry_hashes={hashlib.sha256(k.encode()).hexdigest():pc.digest(v) for k,v in index['entries'].items()},
                    published_bodies=files(f'find {pc.STATE} -type f ! -name index.json -exec sha256sum {{}} +'),
                    publisher_shares=files(f'find {pc.ROOT}/publishers -type f -exec sha256sum {{}} +'),
                    module_files=files('find /data/adb/modules/cyclone_cloak -type f -exec sha256sum {} +'),
                    binding_stores=files('for u in /data/user/[0-9]*; do f="$u/dev.cyclone.cloak/no_backup/cloak-bindings-v1.json"; if [ -f "$f" ]; then sha256sum "$f"; fi; done'))

    def activate(user):
        if d.shell(f'am get-started-user-state {user}', check=False).stdout.strip() != 'RUNNING_UNLOCKED':
            raise ValueError('USER_UNAVAILABLE_OR_LOCKED')
        if int(d.shell('am get-current-user').stdout.strip()) != user:
            d.shell(f'am switch-user {user}')
        deadline = time.monotonic()+60
        while time.monotonic()<deadline:
            if int(d.shell('am get-current-user').stdout.strip()) == user: return
            time.sleep(.3)
        raise ValueError('USER_SWITCH_TIMEOUT')

    def capture(mode, user, cold):
        nonlocal captures_started
        captures_started = True
        activate(user)
        item = next(v for v in selections if v['user']==user)
        phase = f'user-{user}-{mode}'
        m['active_capture'] = phase
        h.checkpoint()
        _, index = d.index()
        published = None
        if mode == 'bound-cold':
            entry = index['entries'].get(f'{user}/{PACKAGE}', {})
            if entry.get('key') != pc.digest([m['run_id'], user]): raise ValueError('OVERLAY_LOST')
            published = d.root(f'sha256sum {pc.STATE}/{entry["key"]}/profile.json').stdout.split()[0]
            if published != item['sha256']: raise ValueError('PUBLISHED_BODY_HASH_MISMATCH')
        elif f'{user}/{PACKAGE}' in index['entries']:
            raise ValueError('UNEXPECTED_DIAGNOSTICS_BINDING')
        for u in users: index['entries'].pop(f'{u}/{PACKAGE}', None)
        if pc.digest(index) != before['index_sha256']: raise ValueError('PRODUCTION_ENTRIES_CHANGED')
        artifact = d.root('sha256sum /data/adb/modules/cyclone_cloak/zygisk/arm64-v8a.so').stdout.split()[0]
        if artifact != m['artifact']: raise ValueError('MODULE_ARTIFACT_CHANGED')
        body = pc.encoded(h.body(item)).decode()
        d.shell(f'run-as {PACKAGE} --user {user} sh -c '+shlex.quote('mkdir -p files; cat > files/expected-profile.json'), data=body)
        # PID comes from the previous capture of this user, not global pidof (both users may run).
        prior = next((r for r in reversed(reports) if r['android_user']==user), None)
        prior_pid = prior['pid'] if prior else None
        if cold: d.shell(f'am force-stop --user {user} {PACKAGE}')
        request = uuid.uuid4().hex
        context = dict(phase=phase, request_id=request, android_user=user, fixture=item['fixture'], profile_sha256=item['sha256'], published_sha256=published)
        launched = d.shell(f'am start --user {user} -n {PACKAGE}/dev.cyclone.cloak.probe.DiagnosticsProbeActivity --activity-clear-top --activity-single-top --ez external_profile true --ez no_permission_prompt true --ei expected_user {user} --es fixture {item["fixture"]} --es request_id {request}')
        if re.search('Error|Exception', launched.stdout+launched.stderr): raise ValueError('PROBE_LAUNCH_FAILED')
        deadline = time.monotonic()+90
        while time.monotonic()<deadline:
            output = d.shell(f'run-as {PACKAGE} --user {user} cat files/report.json', check=False)
            if output.returncode: time.sleep(.3); continue
            value = output.stdout
            try:
                if len(value.encode())>262144: raise ValueError('REPORT_TOO_LARGE')
                value = json.loads(value)
                if not isinstance(value, dict): raise ValueError('REPORT_NOT_OBJECT')
                if value.get('request_id') != request: time.sleep(.3); continue
                r = pc.redact_report(value)
                r.update(android_user=user, cold_restart_requested=cold, process_reused=(r.get('pid')==prior_pid),
                         captured_at=pc.utc(), artifact=artifact, apk_sha256=apk_hash, run=phase,
                         binding_present=mode=='bound-cold', published_sha256=published,
                         interpretation='fixture expectation results' if mode=='bound-cold' else 'unbound actual-value observation; fixture mismatches are expected')
                check_metadata(r, item, cold, ids)
                if mode=='bound-cold' and any(row['status'] in {'FAIL','MISSING'} for row in r['rows']):
                    raise ValueError('BOUND_CAPTURE_FAILED')
                pc.save(out/(phase+'.json'), r)
                reports.append(r)
                print(phase, json.dumps(dict(Counter(row['status'] for row in r['rows']))), 'reused='+str(r['process_reused']), flush=True)
                return
            except (ValueError, TypeError, KeyError, RuntimeError) as error:
                pc.save(out/('failed-'+phase+'.json'), pc.failed_report_evidence(value, type(error).__name__, context))
                raise
        raise ValueError('PROBE_REPORT_TIMEOUT')

    try:
        inventory = d.shell('cmd user list -v').stdout
        if not re.search(rf'\bid={args.secondary}, .*type=full.SECONDARY,', inventory): raise ValueError('SECONDARY_USER_UNAVAILABLE')
        for u in users:
            if d.shell(f'am get-started-user-state {u}', check=False).stdout.strip() != 'RUNNING_UNLOCKED': raise ValueError('USER_UNAVAILABLE_OR_LOCKED')
            if not d.shell(f'pm path --user {u} {PACKAGE}', check=False).stdout.strip(): raise ValueError('DIAGNOSTICS_NOT_INSTALLED_FOR_USER')
        original_user = int(d.shell('am get-current-user').stdout.strip())
        m['original_user'] = original_user
        before = audit(); pc.save(out/'audit-before.json', before)
        permissions_before = permission_state(); pc.save(out/'permissions-before.json', permissions_before)
        pc.save(out/'environment.json', h.environment())
        props = dict(line.split('=',1) for line in d.root('cat /data/adb/modules/cyclone_cloak/module.prop').stdout.splitlines() if '=' in line)
        app = d.shell('dumpsys package dev.cyclone.cloak').stdout
        m['installed_versions'] = dict(module=props['version'], module_code=props['versionCode'],
            app_version=re.search(r'versionName=(\S+)',app).group(1), app_code=re.search(r'versionCode=(\d+)',app).group(1))
        if m['installed_versions'] != dict(module=TAG,module_code='42',app_version='0.11.0-alpha.18',app_code='42'): raise ValueError('INSTALLED_VERSION_MISMATCH')
        aapt = Path(args.adb).resolve().parents[1]/'build-tools/35.0.0/aapt.exe'
        badging = subprocess.check_output([str(aapt),'dump','badging',str(apk)], text=True)
        if not re.search(r"^package: name='dev\.cyclone\.cloak\.probe\.diag'",badging,re.M): raise ValueError('WRONG_APK')
        if 'Success' not in d.run('install','--user',0,'-r',apk,timeout=120).stdout: raise ValueError('DIAGNOSTICS_INSTALL_FAILED')
        m['installed_apk_hashes'] = {}
        for u in users:
            path = d.shell(f'pm path --user {u} {PACKAGE}').stdout.splitlines()[0].removeprefix('package:')
            sha = d.root('sha256sum '+shlex.quote(path)).stdout.split()[0]
            m['installed_apk_hashes'][str(u)] = sha
            if sha != apk_hash: raise ValueError('INSTALLED_APK_HASH_MISMATCH')
        if permission_state() != permissions_before: raise ValueError('INSTALL_CHANGED_PERMISSIONS')
        for u,f in [(0,'a'),(args.secondary,'b')]:
            item = dict(user=u,fixture=f,kind='fixture',target_count=1)
            body = h.body(item); item.update(sha256=pc.digest(body),profile_id=body['id']); selections.append(item)
        m['selections'] = selections
        for mode,u,cold in sequence:
            if mode=='bound-cold' and not m['overlay_active']:
                m['overlay_active'] = True; h.checkpoint(); pc.save(out/'overlay-add.json',h.overlay())
            if mode=='post-removal-warm' and m['overlay_active']:
                pc.save(out/'overlay-remove.json',h.overlay(remove=True))
            capture(mode,u,cold)
        comparisons = []
        for u in users:
            base = next(r for r in reports if r['run']==f'user-{u}-baseline-cold')
            post = next(r for r in reports if r['run']==f'user-{u}-post-removal-cold')
            comparison = restoration(base,post); comparisons.append(comparison)
            pc.save(out/f'user-{u}-cold-restoration.json',comparison)
        m['restoration'] = {str(r['android_user']):r['result'] for r in comparisons}
        if any(r['counts'].get('FAIL') for r in comparisons): errors.append('COLD_RESTORATION_FAILED')
    except Exception as error:
        errors.append(str(error) if re.fullmatch('[A-Z_]+',str(error)) else type(error).__name__)
        m['failed_phase'] = m.get('active_capture','preflight')
        print('STOP',errors[-1],flush=True)
    finally:
        try:
            if m.get('overlay_active'): pc.save(out/'overlay-emergency-remove.json',h.overlay(remove=True))
            if captures_started: cleanup_private(d,users)
            if original_user is not None: activate(original_user)
            if captures_started:
                _, index = d.index(); proof = {}
                for u in users:
                    key = pc.digest([m['run_id'],u])
                    proof[str(u)] = dict(binding_removed=f'{u}/{PACKAGE}' not in index['entries'],
                        body_directory_removed=d.root(f'test ! -e {pc.STATE}/{key}',check=False).returncode==0,
                        private_files_removed=d.shell(f'run-as {PACKAGE} --user {u} sh -c '+shlex.quote('test ! -e files/expected-profile.json && test ! -e files/report.json && test -z "$(find files -maxdepth 1 -name \'report.json.*.tmp\' -print)"'),check=False).returncode==0)
                proof['scratch_removed'] = d.root(f'test ! -e /data/local/tmp/cloak-profile-check-{m["run_id"]} && test ! -e {pc.STATE}/index.harness-{m["run_id"]}',check=False).returncode==0
                pc.save(out/'cleanup-proof.json',proof)
                if not proof['scratch_removed'] or not all(all(proof[str(u)].values()) for u in users): errors.append('CLEANUP_PROOF_FAILED')
            if before is not None:
                after = audit(); pc.save(out/'audit-after.json',after)
                m['production_unchanged'] = before==after
                if before != after: errors.append('PRODUCTION_AUDIT_CHANGED')
            if permissions_before is not None:
                after = permission_state(); pc.save(out/'permissions-after.json',after)
                m['diagnostic_appop_records_expected'] = sorted(EXPECTED_DIAGNOSTIC_APPOP_ADDITIONS.items())
                m['permissions_unchanged'] = effective_permission_state(permissions_before, after)
                if not m['permissions_unchanged']: errors.append('PERMISSION_STATE_CHANGED')
        except Exception as error:
            errors.append('CLEANUP_OR_AUDIT_FAILED:'+type(error).__name__)
        m.update(errors=errors,finished_at=pc.utc(),duration_seconds=round(time.time()-m['started_epoch'],2),
                 result='FAIL' if errors else 'RESTORED_WITH_EXPECTED_LIMITATIONS',captures=len(reports))
        h.checkpoint()
        build_command='$env:JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot"; $env:ANDROID_HOME="C:/Users/Agent/android-sdk"; & "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p "$env:TEMP/cloak-alpha18-reversibility-clean/android" :probe:assembleDiagnosticsDebug :probe:testDiagnosticsDebugUnitTest --offline --no-daemon --console=plain'
        pc.save(out/'build-provenance.json',dict(tag=TAG,commit=COMMIT,detached_checkout=str(source),build_command=build_command,
            apk_sha256=apk_hash,installed_apk_hashes=m.get('installed_apk_hashes'),orchestration_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest()))
        lines=['# Diagnostics binding reversibility','',f'Result: {m["result"]}',f'Duration: {m["duration_seconds"]} seconds',
               'Errors: '+json.dumps(errors),'','| Phase | User | Fixture | PASS | FAIL | OBSERVED | BLOCKED | MISSING | Reused |',
               '|---|---:|---|---:|---:|---:|---:|---:|---|']
        for r in reports:
            counts=Counter(row['status'] for row in r['rows'])
            lines.append('| '+r['run']+' | '+str(r['android_user'])+' | '+r['fixture']+' | '+' | '.join(str(counts[s]) for s in ['PASS','FAIL','OBSERVED','BLOCKED','MISSING'])+' | '+str(r['process_reused'])+' |')
        lines+=['','Unbound/warm-removal fixture FAIL rows are expected mismatches against synthetic fixtures, not restoration failures. Cold restoration compares actual-value hashes with the same user baseline. Dynamic external-state rows remain observations and denied rows remain blocked.',
                '', 'Restoration: '+json.dumps(m.get('restoration')), 'Production unchanged: '+str(m.get('production_unchanged')),
                'Permissions/grant flags/app-op modes unchanged: '+str(m.get('permissions_unchanged'))+
                ' (expected diagnostics read-record additions: '+json.dumps(m.get('diagnostic_appop_records_expected'))+')',
                '', 'Limitations: only existing users 0 and '+str(args.secondary)+'; normal Android switching, not Cyclone bootstrap. Warm in-memory effects are observations. Locked-user private stores are unaudited. Permission app-op modes exclude volatile access times/counters. Installed module file hash is not in-process binary proof. No platform verdict.']
        (out/'summary.md').write_text('\n'.join(lines)+'\n')
        shutil.copyfile(__file__,out/Path(__file__).name)
        with ZipFile(out/'evidence-bundle.zip','w',ZIP_DEFLATED) as z:
            for f in sorted(out.iterdir()):
                if f.is_file() and f.suffix in {'.json','.md','.py','.log'}: z.write(f,f.name)
        print('EVIDENCE',out,flush=True); print('RESULT',m['result'],flush=True)
    return 1 if errors else 0


if __name__=='__main__':
    raise SystemExit(main())



