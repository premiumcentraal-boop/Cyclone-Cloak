"""Diagnostics-only A/secondary-B/return-A orchestration using the committed diagnostics harness.

No production installs, permission changes, forced unlocks or user creation.
The APK and base harness must come from a clean checkout at --source-commit.
"""
import argparse
import hashlib
import importlib
import json
import re
import shlex
import shutil
import subprocess
import sys
import time
import uuid
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

TAG = 'v0.11.0-alpha.17'


def select_for_user(selections, user):
    matches = [v for v in selections if v['user'] == user]
    if len(matches) != 1:
        raise ValueError('AMBIGUOUS_OR_MISSING_USER_SELECTION')
    return matches[0]


def plan(secondary):
    if type(secondary) is not int or secondary <= 0:
        raise ValueError('INVALID_SECONDARY_USER')
    return [('owner-initial', 0, False), ('secondary-warm', secondary, False),
            ('secondary-cold', secondary, True), ('owner-return-warm', 0, False),
            ('owner-return-cold', 0, True)]


def check_capture(report, item, published_hash, cold, require_warm=False):
    if (report.get('android_user') != item['user'] or report.get('uid', -1) // 100000 != item['user']
            or report.get('fixture') != item['fixture']
            or report.get('profile_sha256') != item['sha256'] or published_hash != item['sha256']):
        raise ValueError('CROSS_USER_OR_FIXTURE_HASH_MISMATCH')
    if report.get('cold_restart_requested') is not cold:
        raise ValueError('CAPTURE_MODE_MISMATCH')
    if cold and report.get('process_reused') is not False:
        raise ValueError('COLD_PROCESS_REUSED')
    if require_warm and report.get('process_reused') is not True:
        raise ValueError('WARM_PROCESS_NOT_REUSED')
    return dict(android_user=item['user'], fixture=item['fixture'],
                profile_sha256=report['profile_sha256'], published_sha256=published_hash,
                request_id=report['request_id'], pid=report['pid'],
                cold_restart_requested=cold, process_reused=report['process_reused'], match=True)


def cleanup(harness, selections, users, package):
    """Always remove the entire two-user overlay, not just the last selection."""
    harness.m['selections'] = selections
    result = harness.overlay(remove=True) if harness.m.get('overlay_active') else dict(not_created=True)
    for user in users:
        harness.d.shell(f'am force-stop --user {user} {package}', check=False)
        harness.d.shell(f'run-as {package} --user {user} rm -f files/expected-profile.json files/report.json')
    return result


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source-checkout', type=Path, required=True)
    p.add_argument('--source-commit', required=True)
    p.add_argument('--adb', required=True)
    p.add_argument('--serial', required=True)
    p.add_argument('--secondary', type=int, required=True)
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    phases = plan(args.secondary)
    source = args.source_checkout.resolve()
    def git(*cmd):
        return subprocess.check_output(['git', *cmd], cwd=source, text=True).strip()
    commit = git('rev-parse', 'HEAD')
    if not re.fullmatch('[a-f0-9]{40}', args.source_commit) or commit != args.source_commit or git('status', '--porcelain', '--untracked-files=no'):
        p.error('Source checkout tracked files must be clean at --source-commit')
    # Load the base harness from the same committed source as the APK.
    sys.path.insert(0, str(source / 'tools'))
    pc = importlib.import_module('profile_check')
    if Path(pc.__file__).resolve().parent != source / 'tools':
        p.error('Wrong base harness source')
    apk = source / 'android/probe/build/outputs/apk/diagnostics/debug/probe-diagnostics-debug.apk'
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    (out / '.gitignore').write_text('*\n')
    d = pc.Device(args.adb, args.serial, 90)
    users = [0, args.secondary]
    m = dict(format=1, run_id=uuid.uuid4().hex, user=0, target_user=args.secondary,
             full_profile=False, phases={}, started_at=pc.utc(), started_epoch=time.time(),
             installed_release_tag=TAG, source_commit=commit, apk_sha256=hashlib.sha256(apk.read_bytes()).hexdigest())
    h = pc.Harness(out, m, d, apk)
    reports, primers, checks, errors, switches = [], [], [], [], []
    selections, before, original_permissions = [], None, None

    def permission_state():
        text = d.shell('dumpsys package ' + pc.PACKAGE).stdout
        grants = {}
        for match in re.finditer(r'^    User (\d+):.*?(?=^    User |^\S|\Z)', text, re.M | re.S):
            grants[match[1]] = {name: dict(granted=value == 'true', flags=flags.strip())
                for name, value, flags in re.findall(r'(android\.permission\.[A-Z_]+): granted=(true|false), flags=\[([^\]]*)\]', match[0])}
        modes = {}
        for user in users:
            text = d.shell(f'cmd appops get --user {user} {pc.PACKAGE}', check=False).stdout
            modes[str(user)] = sorted(re.findall(r'(?m)^\s*((?:Uid mode: )?[A-Z_]+): ([a-z]+)', text))
        return dict(grants=grants, appop_modes=modes)

    def audit():
        raw, index = d.index()
        if any(f'{u}/{pc.PACKAGE}' in index['entries'] for u in users):
            raise ValueError('EXISTING_PROBE_BINDING')
        def files(command):
            rows = d.root(command).stdout.splitlines()
            entries = {}
            for line in rows:
                sha, path = line.split(None, 1)
                if not re.fullmatch('[a-f0-9]{64}', sha):
                    raise ValueError('AUDIT_HASH_INVALID')
                entries[hashlib.sha256(path.strip().encode()).hexdigest()] = sha
            return dict(count=len(entries), files=entries, sha256=pc.digest(entries))
        return dict(production_entry_count=len(index['entries']), index_sha256=pc.digest(index),
            index_bytes_sha256=hashlib.sha256(raw.encode()).hexdigest(),
            entry_hashes={hashlib.sha256(k.encode()).hexdigest(): pc.digest(v) for k, v in index['entries'].items()},
            published_bodies=files(f'find {pc.STATE} -type f ! -name index.json -exec sha256sum {{}} +'),
            publisher_shares=files(f'find {pc.ROOT}/publishers -type f -exec sha256sum {{}} +'),
            module_files=files('find /data/adb/modules/cyclone_cloak -type f -exec sha256sum {} +'),
            binding_stores=files('for u in /data/user/[0-9]*; do f="$u/dev.cyclone.cloak/no_backup/cloak-bindings-v1.json"; if [ -f "$f" ]; then sha256sum "$f"; fi; done'))

    def capture(name, user, cold, require_warm=False):
        m['active_capture'] = name
        h.checkpoint()
        item = select_for_user(selections, user)
        key = pc.digest([m['run_id'], user])
        published = d.root(f'sha256sum {pc.STATE}/{key}/profile.json').stdout.split()[0]
        _, index = d.index()
        entry = index['entries'][f'{user}/{pc.PACKAGE}']
        if entry.get('cloakProfileId') != item['profile_id']:
            raise ValueError('INDEX_PROFILE_REFERENCE_MISMATCH')
        for target in users:
            index['entries'].pop(f'{target}/{pc.PACKAGE}', None)
        if pc.digest(index) != before['index_sha256']:
            raise ValueError('PRODUCTION_ENTRIES_CHANGED')
        m['active_published_sha256'] = published
        report = h.capture(item, cold=cold)
        report['run'] = name
        pc.save(out / (name + '.json'), report)
        try:
            check = check_capture(report, item, published, cold, require_warm)
            if any(row['status'] in {'FAIL', 'MISSING'} for row in report['rows']):
                raise ValueError('UNEXPECTED_PROBE_FAILURE')
        except (ValueError, TypeError, KeyError) as failure:
            pc.save(out / ('failed-' + name + '.json'), pc.failed_report_evidence(report,
                type(failure).__name__, dict(phase=name, request_id=report.get('request_id'),
                    android_user=user, fixture=item['fixture'], profile_sha256=item['sha256'],
                    published_sha256=published)))
            raise
        check['run'] = name
        checks.append(check)
        pc.save(out / 'fixture-hash-checks.json', checks)
        print(name, json.dumps(pc.classify([report])), flush=True)
        return report

    try:
        app = d.shell('dumpsys package dev.cyclone.cloak').stdout
        props = dict(line.split('=', 1) for line in d.root('cat /data/adb/modules/cyclone_cloak/module.prop').stdout.splitlines() if '=' in line)
        if not re.search(r'versionCode=41\b', app) or not re.search(r'versionName=0\.11\.0-alpha\.17\s', app) or props.get('version') != TAG or props.get('versionCode') != '41':
            raise ValueError('PRODUCTION_VERSION_MISMATCH')
        inventory = d.shell('cmd user list -v').stdout
        if not re.search(rf'\bid={args.secondary}, .*type=full.SECONDARY,', inventory):
            raise ValueError('SECONDARY_NOT_EXISTING_FULL_USER')
        pc.save(out / 'existing-users.json', [dict(android_user=int(v[0]), type=v[1]) for v in re.findall(r'\bid=(\d+), name=.*?, type=([^,]+),', inventory)])
        for user in users:
            if d.shell(f'am get-started-user-state {user}', check=False).stdout.strip() != 'RUNNING_UNLOCKED':
                raise ValueError('USER_NOT_UNLOCKED_NO_FORCE_ALLOWED')
        if int(d.shell('am get-current-user').stdout.strip()) != 0:
            raise ValueError('OWNER_NOT_FOREGROUND')
        original_permissions = permission_state()
        pc.save(out / 'permissions-before.json', original_permissions)
        print('Full production before-audit...', flush=True)
        before = audit()
        pc.save(out / 'audit-before.json', before)
        pc.save(out / 'environment.json', h.environment())
        # Existing diagnostics installs are required; no per-user installs/permissions are created.
        for user in users:
            if not d.shell(f'pm path --user {user} {pc.PACKAGE}').stdout.strip():
                raise ValueError('DIAGNOSTICS_NOT_INSTALLED_FOR_USER')
        aapt = Path(args.adb).resolve().parents[1] / 'build-tools/35.0.0/aapt.exe'
        badging = subprocess.check_output([str(aapt), 'dump', 'badging', str(apk)], text=True)
        if not re.search(r"^package: name='dev\.cyclone\.cloak\.probe\.diag'", badging, re.M):
            raise ValueError('NOT_DIAGNOSTICS_APK')
        result = d.run('install', '--user', 0, '-r', apk, timeout=90)
        if 'Success' not in result.stdout:
            raise ValueError('DIAGNOSTICS_INSTALL_FAILED')
        m['installed_apk_hashes'] = {}
        for user in users:
            path = d.shell(f'pm path --user {user} {pc.PACKAGE}').stdout.splitlines()[0].removeprefix('package:')
            sha = d.root('sha256sum ' + shlex.quote(path)).stdout.split()[0]
            m['installed_apk_hashes'][str(user)] = sha
            if sha != m['apk_sha256']:
                raise ValueError('INSTALLED_DIAGNOSTICS_HASH_MISMATCH')
        if permission_state() != original_permissions:
            raise ValueError('INSTALL_CHANGED_PERMISSION_STATE')
        for user, fixture in [(0, 'a'), (args.secondary, 'b')]:
            item = dict(kind='fixture', fixture=fixture, user=user, target_count=1)
            body = h.body(item)
            item.update(sha256=pc.digest(body), profile_id=body['id'])
            selections.append(item)
        m['selections'] = selections
        m['overlay_active'] = True
        h.checkpoint()
        pc.save(out / 'overlay.json', h.overlay())
        for name, user, cold in phases:
            if int(d.shell('am get-current-user').stdout.strip()) != user:
                switches.append(h.activate(user))
                pc.save(out / 'switches.json', switches)
            if not cold:
                # A warm request requires a proven live process. Preserve each primer as evidence.
                primers.append(capture(name + '-primer', user, False))
            reports.append(capture(name, user, cold, require_warm=not cold))
    except Exception as error:
        reason = str(error) if re.fullmatch('[A-Z_]+', str(error)) else type(error).__name__
        errors.append(reason)
        m['failed_phase'] = m.get('active_capture', 'preflight')
        print('STOP:', reason, flush=True)
    finally:
        try:
            if int(d.shell('am get-current-user').stdout.strip()) != 0:
                switches.append(h.activate(0))
        except Exception as error:
            errors.append('RETURN_TO_OWNER_FAILED:' + type(error).__name__)
        try:
            m['cleanup'] = cleanup(h, selections, users, pc.PACKAGE)
            proofs = {}
            _, index = d.index()
            for user in users:
                key = pc.digest([m['run_id'], user])
                proofs[str(user)] = dict(binding_removed=f'{user}/{pc.PACKAGE}' not in index['entries'],
                    body_directory_removed=d.root(f'test ! -e {pc.STATE}/{key}', check=False).returncode == 0,
                    private_files_removed=d.shell(f'run-as {pc.PACKAGE} --user {user} sh -c "test ! -e files/expected-profile.json && test ! -e files/report.json"', check=False).returncode == 0)
            proofs['scratch_removed'] = d.root(f'test ! -e /data/local/tmp/cloak-profile-check-{m["run_id"]} && test ! -e {pc.STATE}/index.harness-{m["run_id"]}', check=False).returncode == 0
            pc.save(out / 'cleanup-proof.json', proofs)
            if not proofs['scratch_removed'] or not all(all(proofs[str(u)].values()) for u in users):
                errors.append('CLEANUP_PROOF_FAILED')
            print('Full production after-audit...', flush=True)
            after = audit()
            pc.save(out / 'audit-after.json', after)
            m['preservation'] = {k: before[k] == after[k] for k in before} if before else {}
            if before and not all(m['preservation'].values()):
                errors.append('PRODUCTION_AUDIT_CHANGED')
            restored = permission_state()
            pc.save(out / 'permissions-after.json', restored)
            m['permissions_unchanged'] = restored == original_permissions
            if original_permissions and not m['permissions_unchanged']:
                errors.append('PERMISSION_STATE_CHANGED')
            pc.save(out / 'crash-evidence.json', h.crash_log())
        except Exception as error:
            errors.append('CLEANUP_OR_AUDIT_FAILED:' + type(error).__name__)
        comparisons = []
        for left, right in zip(reports, reports[1:]):
            differences = pc.compare(left, right)
            comparisons.extend(differences)
            pc.save(out / (left['run'] + '-to-' + right['run'] + '.json'), differences)
        verdict = pc.classify(reports, comparisons, phase_failures=len(errors))
        if len(reports) != len(phases):
            verdict['result'] = 'INCOMPLETE' if not errors else 'FAIL'
        pc.save(out / 'classification.json', verdict)
        m.update(errors=errors, finished_at=pc.utc(), duration_seconds=round(time.time() - m['started_epoch'], 2),
                 completed_captures=[r['run'] for r in reports], primer_captures=[r['run'] for r in primers])
        h.checkpoint()
        pc.save(out / 'build-provenance.json', dict(installed_release_tag=TAG, commit=commit, clean_checkout=str(source),
            apk_path=str(apk), apk_sha256=m['apk_sha256'], installed_apk_hashes=m.get('installed_apk_hashes'),
            build_command='$env:JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot"; $env:ANDROID_HOME="C:/Users/Agent/android-sdk"; & "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain',
            orchestration_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest()))
        lines = ['# Alpha.17 cross-user diagnostics', '', 'Result: ' + verdict['result'],
                 f'Duration: {m["duration_seconds"]} seconds', 'Errors: ' + json.dumps(errors), '',
                 '| Phase | User | Fixture | PASS | OBSERVED | BLOCKED | FAIL | MISSING | Reused process |',
                 '|---|---:|---|---:|---:|---:|---:|---:|---|']
        for r in reports:
            counts = pc.classify([r])['statuses']
            lines.append(f'| {r["run"]} | {r["android_user"]} | {r["fixture"]} | ' + ' | '.join(str(counts.get(s, 0)) for s in ['PASS', 'OBSERVED', 'BLOCKED', 'FAIL', 'MISSING']) + f' | {r["process_reused"]} |')
        lines += ['', 'Paired classification: ' + json.dumps(verdict), 'Preservation: ' + json.dumps(m.get('preservation')),
                  'Permissions/app-op modes unchanged: ' + str(m.get('permissions_unchanged')), '',
                  'Each warm capture has a retained primer and proves process reuse. Cold captures prove replacement. Primers are excluded from the five-phase aggregate. Only diagnostics APK and temporary probe overlay were written. Normal user switches may run existing system/profile apps. No permissions changed, forced unlocks, user creation, production app/module installation or reboot. Other locked users private stores remain outside the accessible-file audit. Installed-file digest is not in-process module identity proof. External rows remain observations; denied reads are BLOCKED. No platform verdict.']
        (out / 'summary.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')
        shutil.copyfile(__file__, out / 'run_user_transition.py')
        with ZipFile(out / 'evidence-bundle.zip', 'w', ZIP_DEFLATED) as z:
            for path in sorted(out.iterdir()):
                if path.is_file() and path.suffix in {'.json', '.md', '.log', '.py'}:
                    z.write(path, path.name)
        print('EVIDENCE:', out, flush=True)
        print('RESULT:', json.dumps(verdict), flush=True)
    return 1 if errors else 0


if __name__ == '__main__':
    raise SystemExit(main())
