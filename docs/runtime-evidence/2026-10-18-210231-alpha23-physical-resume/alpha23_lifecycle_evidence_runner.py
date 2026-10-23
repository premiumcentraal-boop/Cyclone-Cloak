"""Diagnostics-only overlay reversibility evidence; APK/base harness stay at alpha.18.

Never starts/unlocks users, grants permissions, or installs production packages.
Unbound report FAIL statuses compare against fixture expectations, not restoration.
Only paired baseline-versus-cold actual hashes decide restoration.
"""
import argparse
import copy
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

TAG = 'v0.11.0-alpha.23'
COMMIT = '0d028a947b37013e0ad89a98d1455a79c33bfd3d'
PACKAGE = 'dev.cyclone.cloak.probe.diag'
APK_INSTALL_POLICY = 'reuse_matching_install_missing_once'
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
    """Allow only read-created diagnostics records; reject every other delta."""
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
    expected = dict(EXPECTED_DIAGNOSTIC_APPOP_ADDITIONS)
    for user in old.get('appop_modes', {}):
        before_entries = [_appop_entry(entry) for entry in old['appop_modes'][user]]
        after_entries = [_appop_entry(entry) for entry in new['appop_modes'][user]]
        if len(before_entries) != len(set(before_entries)) or len(after_entries) != len(set(after_entries)):
            return False
        additions = [entry for entry in after_entries if entry not in before_entries]
        if any(name not in expected or expected[name] != mode for name, mode in additions):
            return False
        if after_entries[:len(before_entries)] != before_entries:
            return False
        if after_entries[len(before_entries):] != sorted(additions):
            return False
    return True


def _appop_entry(value):
    if not isinstance(value, (list, tuple)) or len(value) != 2:
        raise ValueError('INVALID_APPOP_ENTRY')
    name, mode = value
    if isinstance(name, str) and name.startswith('Uid mode: '): name = name[len('Uid mode: '):]
    if not isinstance(name, str) or not re.fullmatch(r'[A-Z_]+', name):
        raise ValueError('INVALID_APPOP_ENTRY')
    if not isinstance(mode, str) or not re.fullmatch(r'[a-z]+', mode):
        raise ValueError('INVALID_APPOP_ENTRY')
    return name, mode


def cycle_plan(users, cycles=1):
    if type(cycles) is not int or cycles < 1:
        raise ValueError('INVALID_CYCLES')
    sequence = phases(users)
    return [dict(cycle=i, directory='.' if cycles == 1 else f'cycle-{i:03d}',
                 phases=list(sequence)) for i in range(1, cycles + 1)]


def ensure_diagnostics_apk(device, users, apk, expected_hash, install_state, evidence):
    """Read both user APK hashes; never replace mismatches or retry installation."""
    evidence.update(apk_install_policy=APK_INSTALL_POLICY, installation_skipped=True,
                    install_attempted=False, expected_sha256=expected_hash, checks=[])

    def inspect(stage):
        hashes, missing = {}, []
        for user in users:
            result = device.shell(f'pm path --user {user} {PACKAGE}', check=False)
            lines = result.stdout.strip().splitlines()
            check = dict(stage=stage, android_user=user, package_path=None,
                         sha256=None, status='MISSING')
            evidence['checks'].append(check)
            if not lines:
                if result.stdout.strip() or result.stderr.strip():
                    raise ValueError('DIAGNOSTICS_PACKAGE_LOOKUP_FAILED')
                missing.append(user)
                continue
            if result.returncode or len(lines) != 1 or not re.fullmatch(r'package:/[^\r\n]+\.apk', lines[0]):
                check['status'] = 'INVALID_PATH'
                raise ValueError('INVALID_DIAGNOSTICS_APK_PATH')
            path = lines[0].removeprefix('package:')
            check['package_path'] = path
            result = device.root('sha256sum '+shlex.quote(path), check=False)
            sha = result.stdout.split()[0] if result.stdout.split() else None
            check.update(sha256=sha if sha and re.fullmatch('[a-f0-9]{64}', sha) else None,
                         status='MATCH' if result.returncode == 0 and sha == expected_hash else 'MISMATCH')
            if check['status'] != 'MATCH':
                raise ValueError('INSTALLED_APK_HASH_MISMATCH')
            hashes[str(user)] = sha
        return hashes, missing

    hashes, missing = inspect('before-cycle')
    if missing:
        if install_state['install_attempts']:
            raise ValueError('DIAGNOSTICS_INSTALL_ALREADY_ATTEMPTED')
        install_state['install_attempts'] += 1
        evidence.update(installation_skipped=False, install_attempted=True)
        result = device.run('install', '--user', missing[0], '-r', apk, timeout=120)
        if 'Success' not in result.stdout:
            raise ValueError('DIAGNOSTICS_INSTALL_FAILED')
        hashes, missing = inspect('after-install')
        if missing:
            raise ValueError('DIAGNOSTICS_NOT_INSTALLED_FOR_USER')
    evidence['installed_apk_hashes'] = hashes
    return hashes


def snapshot_delta(before, after):
    """Describe redacted snapshot changes without normalizing away evidence."""
    if before == after:
        return []
    if isinstance(before, dict) and isinstance(after, dict):
        changes = []
        for key in sorted(before.keys() | after.keys()):
            if key not in before:
                changes.append(dict(path=[key], change='added', after=after[key]))
            elif key not in after:
                changes.append(dict(path=[key], change='removed', before=before[key]))
            else:
                changes.extend(dict(item, path=[key] + item['path'])
                               for item in snapshot_delta(before[key], after[key]))
        return changes
    return [dict(path=[], change='changed', before=before, after=after)]


def aggregate_summary(records, requested_cycles, install_state=None):
    install_state = install_state or {'install_attempts': 0}
    passed = len(records) == requested_cycles and all(r['passed'] for r in records)
    return dict(result='RESTORED_WITH_EXPECTED_LIMITATIONS' if passed else 'FAIL',
                requested_cycles=requested_cycles, completed_cycles=len(records),
                apk_install_policy=APK_INSTALL_POLICY,
                installation_skipped=install_state['install_attempts'] == 0,
                install_attempts=install_state['install_attempts'],
                passed_cycles=sum(r['passed'] for r in records), cycles=records,
                permission_state_changed=any(r['permission_delta'] or r['boundary_permission_delta'] for r in records),
                permission_policy_passed=all(r['permissions_unchanged'] for r in records),
                production_state_changed=any(r['production_unchanged'] is not True or r['boundary_production_delta'] for r in records),
                cleanup_passed=all(r['cleanup_passed'] for r in records),
                failed_cycle=next((r['cycle'] for r in records if not r['passed']), None))


def run_cycles(args, executor=None):
    executor = executor or run_cycle
    plan = cycle_plan([0, args.secondary], args.cycles)
    root = args.output.resolve()
    if root.exists():
        raise FileExistsError('OUTPUT_ALREADY_EXISTS')
    if args.cycles > 1:
        root.mkdir(parents=True)
        (root/'.gitignore').write_text('*\n')
    records = []
    install_state = dict(install_attempts=0)
    initial_audit = initial_permissions = previous_audit = previous_permissions = None
    for step in plan:
        child = copy.copy(args)
        child.cycle = step['cycle']
        child.apk_install_state = install_state
        child.output = root if args.cycles == 1 else root/step['directory']
        print('CYCLE', step['cycle'], 'OF', args.cycles, flush=True)
        error_type = None
        try:
            code = executor(child)
        except Exception as error:
            code, error_type = 1, type(error).__name__
        def load(name):
            path = child.output/name
            return json.loads(path.read_text(encoding='utf-8')) if path.is_file() else None
        m, proof = load('manifest.json') or {}, load('cleanup-proof.json') or {}
        before, after = load('audit-before.json'), load('audit-after.json')
        perm_before, perm_after = load('permissions-before.json'), load('permissions-after.json')
        if not records:
            initial_audit, initial_permissions = before, perm_before
        boundary_ok = not records or (before == previous_audit and perm_before == previous_permissions)
        production_ok = before is not None and before == after == initial_audit
        permissions_ok = False
        try:
            permissions_ok = (perm_before is not None and perm_after is not None
                              and effective_permission_state(perm_before, perm_after)
                              and effective_permission_state(initial_permissions, perm_after))
        except (TypeError, ValueError, KeyError):
            pass
        cleanup_ok = proof.get('scratch_removed') is True and all(
            all(proof.get(str(u), {}).get(key) is True for key in
                ('binding_removed', 'body_directory_removed', 'private_files_removed'))
            for u in (0, args.secondary))
        restored = m.get('restoration', {})
        apk_check = m.get('apk_install', {})
        apk_ok = (apk_check.get('apk_install_policy') == APK_INSTALL_POLICY
                  and isinstance(m.get('apk_sha256'), str)
                  and re.fullmatch('[a-f0-9]{64}', m['apk_sha256']) is not None
                  and apk_check.get('installed_apk_hashes') ==
                      {str(u): m['apk_sha256'] for u in (0, args.secondary)})
        restoration_ok = all(restored.get(str(u)) == 'RESTORED_WITH_LIMITATIONS' for u in (0, args.secondary))
        passed = (code == 0 and m.get('result') == 'RESTORED_WITH_EXPECTED_LIMITATIONS'
                  and m.get('captures') == 8 and not m.get('errors') and restoration_ok
                  and production_ok and permissions_ok and cleanup_ok and boundary_ok and apk_ok)
        record = dict(cycle=step['cycle'], directory=step['directory'], passed=passed,
                      result='RESTORED_WITH_EXPECTED_LIMITATIONS' if passed else 'FAIL', restoration=restored,
                      cleanup_passed=cleanup_ok, cleanup_proof=proof,
                      apk_install=apk_check, installed_apk_check_passed=apk_ok,
                      production_unchanged=production_ok, permissions_unchanged=permissions_ok,
                      permission_delta=snapshot_delta(perm_before, perm_after),
                      production_delta=snapshot_delta(before, after),
                      boundary_unchanged=boundary_ok,
                      boundary_permission_delta=snapshot_delta(previous_permissions, perm_before) if records else [],
                      boundary_production_delta=snapshot_delta(previous_audit, before) if records else [],
                      duration_seconds=m.get('duration_seconds'),
                      failed_phase=m.get('failed_phase') if passed else m.get('failed_phase', 'cycle-audit'),
                      errors=m.get('errors', []) + ([] if passed else
                          [reason for ok, reason in ((boundary_ok, 'CYCLE_BOUNDARY_CHANGED'),
                           (production_ok, 'PRODUCTION_AUDIT_CHANGED'), (permissions_ok, 'PERMISSION_STATE_CHANGED'),
                           (cleanup_ok, 'CLEANUP_PROOF_FAILED'), (restoration_ok, 'RESTORATION_INCOMPLETE'),
                           (apk_ok, 'INSTALLED_APK_CHECK_INCOMPLETE')) if not ok]),
                      error_type=error_type)
        records.append(record)
        previous_audit, previous_permissions = after, perm_after
        # A failed cycle has already run its normal finally/cleanup path. Never retry it.
        summary = aggregate_summary(records, args.cycles, install_state)
        root.mkdir(parents=True, exist_ok=True)
        (root/'aggregate-summary.json').write_text(json.dumps(summary, indent=2)+'\n', encoding='utf-8')
        lines = ['# Diagnostics lifecycle regression', '', 'Result: '+summary['result'],
                 f'Cycles passed: {summary["passed_cycles"]}/{args.cycles}', '',
                 '| Cycle | Result | User 0 restoration | Secondary restoration | Cleanup | Permission policy | Production |',
                 '|---|---|---|---|---|---|---|']
        for r in records:
            lines.append(f'| {r["cycle"]} | {"PASS" if r["passed"] else "FAIL"} | '
                         f'{r["restoration"].get("0", "not completed")} | '
                         f'{r["restoration"].get(str(args.secondary), "not completed")} | '
                         f'{r["cleanup_passed"]} | {r["permissions_unchanged"]} | {r["production_unchanged"]} |')
        lines += ['', 'Permission snapshot changed: '+str(summary['permission_state_changed']),
                  'Production snapshot changed: '+str(summary['production_state_changed']),
                  'APK install policy: '+summary['apk_install_policy'],
                  'APK installation skipped: '+str(summary['installation_skipped']),
                  'APK install attempts: '+str(summary['install_attempts']),
                  'Dynamic or blocked rows remain limitations. No platform verdict.']
        (root/'aggregate-summary.md').write_text('\n'.join(lines)+'\n', encoding='utf-8')
        if not passed:
            break
    shutil.copyfile(__file__, root/'run_probe_reversibility.py')
    repo = Path(__file__).resolve().parents[1]
    revision = subprocess.run(['git', 'rev-parse', 'HEAD'], cwd=repo, capture_output=True, text=True)
    (root/'runner-provenance.json').write_text(json.dumps(dict(
        base_commit=revision.stdout.strip() if revision.returncode == 0 else None,
        orchestration_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        source_tag=TAG, source_commit=COMMIT, cycles=args.cycles), indent=2)+'\n', encoding='utf-8')
    with ZipFile(root/'lifecycle-evidence-bundle.zip', 'w', ZIP_DEFLATED) as z:
        for path in sorted(root.rglob('*')):
            if path.is_file() and path.name not in {'.gitignore', 'lifecycle-evidence-bundle.zip'}:
                z.write(path, path.relative_to(root).as_posix())
    print('LIFECYCLE_EVIDENCE', root, flush=True)
    print('LIFECYCLE_RESULT', summary['result'], flush=True)
    return 0 if summary['result'] != 'FAIL' else 1


def parse_args(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source-checkout', type=Path, required=True)
    p.add_argument('--adb', required=True)
    p.add_argument('--serial', required=True)
    p.add_argument('--secondary', type=int, default=17)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--cycles', type=int, default=1, help='Full lifecycle repetitions (default: 1)')
    args = p.parse_args(argv)
    if args.cycles < 1:
        p.error('--cycles must be at least 1')
    return args


def main():
    return run_cycles(parse_args())


def run_cycle(args):
    p = argparse.ArgumentParser(description=__doc__)
    users = [0, args.secondary]
    sequence = phases(users)
    source = args.source_checkout.resolve()
    def git(*cmd):
        return subprocess.check_output(['git', *cmd], cwd=source, text=True).strip()
    if git('rev-parse', 'HEAD') != COMMIT or git('rev-parse', TAG+'^{}') != COMMIT or git('status', '--porcelain', '--untracked-files=no'):
        p.error('Exact clean alpha.23 checkout required')
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
             cycle=getattr(args, 'cycle', 1),
             apk_install_policy=APK_INSTALL_POLICY, apk_install={},
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
                linked = r.get('linked_parity', {})
                linked_rows = linked.get('rows', [])
                if linked.get('format') != 1 or len(linked_rows) != 13 or len({x.get('id') for x in linked_rows}) != 13:
                    raise ValueError('LINKED_PARITY_REPORT_INCOMPLETE')
                if mode == 'bound-cold' and any(x.get('status') in {'FAIL', 'MISSING'} for x in linked_rows):
                    pc.save(out/('failed-linked-'+phase+'.json'), dict(phase=phase, android_user=user,
                        fixture=item['fixture'], profile_sha256=item['sha256'], published_sha256=published,
                        linked_parity=linked, request_id=request))
                    raise ValueError('LINKED_PARITY_MISMATCH')

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
        original_user = int(d.shell('am get-current-user').stdout.strip())
        m['original_user'] = original_user
        before = audit(); pc.save(out/'audit-before.json', before)
        permissions_before = permission_state(); pc.save(out/'permissions-before.json', permissions_before)
        pc.save(out/'environment.json', h.environment())
        props = dict(line.split('=',1) for line in d.root('cat /data/adb/modules/cyclone_cloak/module.prop').stdout.splitlines() if '=' in line)
        app = d.shell('dumpsys package dev.cyclone.cloak').stdout
        m['installed_versions'] = dict(module=props['version'], module_code=props['versionCode'],
            app_version=re.search(r'versionName=(\S+)',app).group(1), app_code=re.search(r'versionCode=(\d+)',app).group(1))
        if m['installed_versions'] != dict(module=TAG,module_code='47',app_version='0.11.0-alpha.23',app_code='47'): raise ValueError('INSTALLED_VERSION_MISMATCH')
        aapt = Path(args.adb).resolve().parents[1]/'build-tools/35.0.0/aapt.exe'
        badging = subprocess.check_output([str(aapt),'dump','badging',str(apk)], text=True)
        if not re.search(r"^package: name='dev\.cyclone\.cloak\.probe\.diag'",badging,re.M): raise ValueError('WRONG_APK')
        m['installed_apk_hashes'] = ensure_diagnostics_apk(d, users, apk, apk_hash,
            getattr(args, 'apk_install_state', dict(install_attempts=0)), m['apk_install'])
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
        build_command=f'$env:JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot"; $env:ANDROID_HOME="C:/Users/Agent/android-sdk"; & "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p "{source.as_posix()}/android" :probe:assembleDiagnosticsDebug :probe:testDiagnosticsDebugUnitTest --offline --no-daemon --console=plain'
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
