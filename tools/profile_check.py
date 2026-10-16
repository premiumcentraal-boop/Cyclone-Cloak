"""Local evidence harness. Production bindings/shares are audited, never written.

Only the dedicated diagnostics probe receives a temporary index overlay, using
the existing publication inode lock and compare-and-swap. No module installation,
permission grants, log clearing, reboot, connector calls or profile-store writes.
"""
from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import tempfile
import time
import uuid
from zipfile import ZipFile, ZIP_DEFLATED

REPO = Path(__file__).resolve().parents[1]
PACKAGE = 'dev.cyclone.cloak.probe.diag'
ROOT = '/data/adb/cyclone_cloak'
STATE = ROOT + '/state-v1'
PHASES = ('environment', 'bind', 'publish', 'launch', 'probes', 'switch',
          'post-switch-probes', 'diff', 'crash-log', 'summary')
ARTIFACTS = dict(zip(PHASES, ('phase-01-environment.json', 'phase-02-bind.json',
    'phase-03-publish.json', 'phase-04-launch.json', 'phase-05-probes.json',
    'phase-06-switch.json', 'phase-07-post-switch-probes.json', 'phase-08-diff.json',
    'phase-09-crash.log', 'summary.md')))
STATUSES = {'PASS', 'FAIL', 'BLOCKED', 'OBSERVED', 'MISSING'}


def encoded(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=True).encode()


def digest(value):
    return hashlib.sha256(encoded(value)).hexdigest()


def utc():
    return datetime.now(timezone.utc).isoformat()


def save(path, value):
    """Atomic host evidence write; no profile storage involved."""
    path = Path(path)
    tmp = path.with_suffix(path.suffix + '.tmp')
    tmp.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')
    os.replace(tmp, path)


def parse_report(value):
    if isinstance(value, str):
        value = json.loads(value)
    if not isinstance(value, dict) or not isinstance(value.get('rows'), list) or not value['rows']:
        raise ValueError('REPORT_ROWS_MISSING')
    if value.get('error'):
        raise ValueError('PROBE_COLLECTION_FAILED')
    ids = set()
    for row in value['rows']:
        if not isinstance(row, dict) or not isinstance(row.get('id'), str) or row['id'] in ids:
            raise ValueError('INVALID_OR_DUPLICATE_ROW_ID')
        ids.add(row['id'])
        if row.get('status') not in STATUSES or not isinstance(row.get('source'), str):
            raise ValueError('INVALID_ROW_STATUS_OR_SOURCE')
        if not all(k in row for k in ('expected', 'actual', 'detail', 'duration_ms')):
            raise ValueError('INCOMPLETE_ROW')
        if not isinstance(row['detail'], str) or type(row['duration_ms']) not in (int, float) or row['duration_ms'] < 0:
            raise ValueError('INVALID_ROW_DETAIL_OR_DURATION')
    return value


def redact_report(report):
    """Retain comparison hashes, never host identifiers, expected bodies or seeds."""
    result = json.loads(json.dumps(parse_report(report)))
    for row in result['rows']:
        row['actual_sha256'] = digest(row['actual'])
        row['has_expectation'] = row.get('has_expectation', row['expected'] is not None)
        row['expected_sha256'] = digest(row['expected']) if row['has_expectation'] else None
        row['actual'] = row['expected'] = None
    return result


def token(row, name):
    return row.get(name + '_sha256') or digest(row.get(name))


def compare(before, after):
    """Expectations, not an arbitrary list of fields, determine expected changes."""
    before, after = parse_report(before), parse_report(after)
    left = {r['id']: r for r in before['rows']}
    right = {r['id']: r for r in after['rows']}
    findings = []
    for key in sorted(left.keys() | right.keys()):
        a, b = left.get(key), right.get(key)
        reason, severity = 'UNCHANGED', 'INFO'
        changed = None
        expected_change = None
        if a is None or b is None or a['status'] in {'BLOCKED', 'MISSING'} or b['status'] in {'BLOCKED', 'MISSING'}:
            reason, severity = 'BLOCKED_OR_MISSING', 'WARNING'
        else:
            changed = token(a, 'actual') != token(b, 'actual')
            known = a.get('has_expectation', a.get('expected') is not None) and b.get('has_expectation', b.get('expected') is not None)
            expected_change = token(a, 'expected') != token(b, 'expected') if known else None
            if a['status'] == 'FAIL' or b['status'] == 'FAIL':
                reason, severity = 'PROFILE_VALUE_MISMATCH', 'FAIL'
            if expected_change and not changed:
                reason, severity = 'EXPECTED_CHANGE_NOT_OBSERVED', 'FAIL'
            elif changed and expected_change is False:
                reason, severity = 'UNEXPECTED_CHANGE', 'FAIL'
            elif changed and expected_change is None:
                reason, severity = 'UNMAPPED_CHANGE', 'WARNING'
            elif changed and expected_change:
                reason = 'EXPECTED_CHANGE_OBSERVED'
        findings.append(dict(id=key, changed=changed, expected_change=expected_change, reason=reason, severity=severity))
    return findings


def classify(reports, differences=(), phase_failures=0):
    statuses = Counter(r['status'] for report in reports for r in parse_report(report)['rows'])
    failed = {r['id'] for report in reports for r in report['rows'] if r['status'] == 'FAIL'}
    failed.update(r['id'] for r in differences if r['severity'] == 'FAIL')
    warned = {r['id'] for report in reports for r in report['rows'] if r['status'] in {'BLOCKED', 'MISSING', 'OBSERVED'}}
    warned.update(r['id'] for r in differences if r['severity'] == 'WARNING')
    blocked = {r['id'] for report in reports for r in report['rows'] if r['status'] in {'BLOCKED', 'MISSING'}}
    failures, warnings = len(failed) + phase_failures, len(warned)
    result = 'FAIL' if failures else 'DEGRADED' if warnings or not reports else 'PASS'
    return dict(result=result, critical_failures=failures, warnings=warnings,
                blocked_rows=len(blocked), statuses=dict(statuses))


def selection(index, user, full):
    if not isinstance(index, dict) or index.get('schemaVersion') != 2 or not isinstance(index.get('entries'), dict):
        raise ValueError('UNSUPPORTED_INDEX')
    entries = {k: v for k, v in index['entries'].items() if k.startswith(f'{user}/') and k != f'{user}/{PACKAGE}'}
    if not full:
        return dict(kind='fixture', fixture='a', user=user, target_count=1)
    keys = {v.get('key') for v in entries.values()}
    profiles = {v.get('cloakProfileId') for v in entries.values()}
    if not entries or len(profiles) != 1 or not all(isinstance(k, str) and re.fullmatch('[a-f0-9]{64}', k) for k in keys):
        raise ValueError('FULL_PROFILE_MISSING_OR_AMBIGUOUS')
    return dict(kind='published', key=sorted(keys)[0], user=user, target_count=len(entries),
                profile_id=next(iter(profiles)), packages=sorted(k.split('/', 1)[1] for k in entries))


def crash_classes(text, started):
    own = []
    for line in text.splitlines():
        match = re.match(r'\s*(\d+\.\d+)', line)
        if not match or float(match[1]) < started:
            continue
        if re.search(r'>>> dev\.cyclone\.cloak(?:\.probe\.diag)? <<<|Process: dev\.cyclone\.cloak(?:\.probe\.diag)?,', line):
            own.append('CLOAK_PROCESS_CRASH')
    return own


class Device:
    def __init__(self, adb, serial, timeout):
        self.adb, self.serial, self.timeout = adb, serial, timeout

    def run(self, *args, check=True, data=None, timeout=None, binary=False):
        p = subprocess.run([self.adb, '-s', self.serial, *map(str, args)], input=data,
            capture_output=True, **({} if binary else dict(text=True, encoding='utf-8', errors='replace')), timeout=timeout or self.timeout)
        if check and p.returncode:
            raise RuntimeError('ADB_COMMAND_FAILED')  # raw commands/output can contain private values
        return p

    def shell(self, command, **kwargs):
        return self.run('shell', command, **kwargs)

    def root(self, command, **kwargs):
        return self.shell('su --mount-master -c ' + shlex.quote('set -eu; ' + command), **kwargs)

    def index(self):
        raw = self.root(f'cat {STATE}/index.json', binary=True).stdout.decode('utf-8')
        value = json.loads(raw)
        if value.get('schemaVersion') != 2 or not isinstance(value.get('entries'), dict):
            raise ValueError('UNSUPPORTED_INDEX')
        return raw, value


class Harness:
    def __init__(self, output, manifest, device, apk):
        if not re.fullmatch('[a-f0-9]{32}', manifest.get('run_id', '')):
            raise ValueError('INVALID_RUN_ID')
        self.output, self.m, self.d, self.apk = Path(output), manifest, device, Path(apk)

    def checkpoint(self):
        save(self.output / 'manifest.json', self.m)

    def body(self, item):
        if type(item.get('user')) is not int or item['user'] < 0:
            raise ValueError('INVALID_ANDROID_USER')
        if item['kind'] == 'fixture':
            if item.get('fixture') not in ('a','b'):
                raise ValueError('INVALID_FIXTURE')
            value = json.loads((REPO / f"android/probe/src/main/assets/profile-{item['fixture']}.json").read_text(encoding='utf-8'))
        else:
            if item['kind'] != 'published' or not re.fullmatch('[a-f0-9]{64}', item.get('key','')):
                raise ValueError('INVALID_PUBLISHED_KEY')
            value = json.loads(self.d.root(f"cat {STATE}/{item['key']}/profile.json").stdout)
        if not isinstance(value, dict) or not isinstance(value.get('device'), dict) or not isinstance(value.get('identifiers'), dict):
            raise ValueError('EXPECTED_PROFILE_MALFORMED')
        if item.get('sha256') and digest(value) != item['sha256']:
            raise ValueError('EXPECTED_PROFILE_CHANGED')
        return value

    def environment(self):
        current = int(self.d.shell('am get-current-user').stdout.strip())
        self.m.setdefault('original_user', current)
        self.m.setdefault('user', current)
        self.m.setdefault('target_user', self.m['user'])
        _, index = self.d.index()
        props = self.d.root('cat /data/adb/modules/cyclone_cloak/module.prop').stdout
        artifact = self.d.root('sha256sum /data/adb/modules/cyclone_cloak/zygisk/arm64-v8a.so').stdout.split()[0]
        value = dict(current_user=current, user=self.m['user'], target_user=self.m['target_user'],
            api=self.d.shell('getprop ro.build.version.sdk').stdout.strip(),
            module_properties=props.splitlines(), installed_module_sha256=artifact,
            module_digest_scope='installed file; not proof of the in-process binary',
            published_entries=len(index['entries']), index_sha256=digest(index))
        self.m['artifact'] = artifact
        return value

    def bind(self):
        if 'user' not in self.m:
            self.environment()
        _, index = self.d.index()
        before = selection(index, self.m['user'], self.m['full_profile'])
        after = dict(kind='fixture', fixture='b', user=self.m['target_user'], target_count=1) if self.m['target_user'] != self.m['user'] else dict(before)
        for item in (before, after):
            body = self.body(item)
            item.update(sha256=digest(body), profile_id=body.get('id', 'not recorded'))
        self.m['selections'] = [before, after]
        return dict(mode='existing binding audit / dedicated probe overlay only', selections=[
            {k: v for k, v in item.items() if k != 'key'} for item in self.m['selections']], production_bindings_modified=False)

    def overlay(self, remove=False):
        """Kernel lock + raw-byte CAS; preserve concurrent publishers and never unlink the lock."""
        from run_runtime_probe import edited_index
        owned = {digest([self.m['run_id'], item['user']]) for item in self.m['selections']}
        with tempfile.TemporaryDirectory(prefix='cloak-profile-check-') as local:
            local = Path(local)
            stage = '/data/local/tmp/cloak-profile-check-' + self.m['run_id']
            self.d.shell(f'mkdir -p {stage}; chmod 700 {stage}')
            try:
                for attempt in range(3):
                    raw, index = self.d.index()
                    updated = index
                    copies = []
                    for item in self.m['selections']:
                        key = digest([self.m['run_id'], item['user']])
                        existing = updated['entries'].get(f"{item['user']}/{PACKAGE}")
                        if remove and existing is not None and existing.get('key') not in owned:
                            raise ValueError('PROBE_BINDING_CHANGED_KEEPING_FOREIGN_ENTRY')
                        updated = edited_index(updated, item['user'], None if remove else key,
                            item['profile_id'], owned, PACKAGE)
                        if not remove:
                            body = local / (key + '.json')
                            body.write_bytes(encoded(self.body(item)))
                            self.d.run('push', body, stage + '/' + body.name)
                            copies.append(f'mkdir -p {STATE}/{key}; cp {stage}/{body.name} {STATE}/{key}/profile.json; chmod 700 {STATE}/{key}; chmod 600 {STATE}/{key}/profile.json; ')
                    patch = local / 'index.json'
                    patch.write_bytes(encoded(updated))
                    self.d.run('push', patch, stage + '/index.json')
                    expected = hashlib.sha256(raw.encode('utf-8')).hexdigest()
                    # Keep root-owned temporary file on the same filesystem as the final rename.
                    script = f'(exec 0>> {ROOT}/publication.lock; flock -n 0 || exit 73; '
                    script += f'current=$(sha256sum {STATE}/index.json); [ "${{current%% *}}" = {expected} ] || exit 74; '
                    script += ''.join(copies)
                    script += f'cp {stage}/index.json {STATE}/index.harness-{self.m["run_id"]}; chmod 600 {STATE}/index.harness-{self.m["run_id"]}; mv {STATE}/index.harness-{self.m["run_id"]} {STATE}/index.json; '
                    if remove:
                        for key in owned:
                            if not any(v.get('key') == key for v in updated['entries'].values()):
                                script += f'rm -f {STATE}/{key}/profile.json; if [ -d {STATE}/{key} ]; then rmdir {STATE}/{key}; fi; '
                    script += ')'
                    result = self.d.root(script, check=False)
                    if result.returncode == 0:
                        self.m['overlay_active'] = not remove
                        self.checkpoint()
                        return dict(mode='temporary probe index overlay; NOT app publication', production_entries_preserved=True,
                                    entries=len(updated['entries']), index_sha256=digest(updated), removed=remove)
                    if result.returncode != 74:
                        raise RuntimeError('PUBLICATION_LOCKED' if result.returncode == 73 else 'PROBE_OVERLAY_FAILED')
                raise RuntimeError('CONCURRENT_INDEX_CHANGE')
            finally:
                # Only this run's known scratch files. Never touch the lock inode or publisher shares.
                names = ' '.join(stage + '/' + name for name in ['index.json'] + [k + '.json' for k in owned])
                self.d.shell(f'rm -f {names}; rmdir {stage}', check=False)

    def publish(self):
        _, index = self.d.index()
        audit = dict(mode='published state audit, not normal app publication', target_count=1)
        if self.m['full_profile']:
            item = self.m['selections'][0]
            entries = {k: v for k, v in index['entries'].items() if k.startswith(str(item['user']) + '/') and not k.endswith('/' + PACKAGE)}
            paths = [STATE + '/' + v['key'] + '/profile.json' for v in entries.values()]
            # Keep each adb command below Windows CreateProcess's command-line limit.
            hashes = []
            for start in range(0, len(paths), 32):
                hashes.extend(self.d.root('sha256sum ' + ' '.join(paths[start:start + 32])).stdout.splitlines())
            if len(hashes) != item['target_count'] or len({line.split()[0] for line in hashes}) != 1:
                raise RuntimeError('PUBLISHED_PROFILE_BODY_MISMATCH')
            audit.update(target_count=len(paths), all_published_profile_files_identical=True)
        self.m['overlay_active'] = True  # checkpoint intent before any uncertain device mutation
        self.checkpoint()
        result = self.overlay()
        result['production_audit'] = audit
        return result

    def activate(self, user):
        self.d.shell(f'am start-user -w {user}')
        self.d.shell(f'am switch-user {user}')
        deadline = time.monotonic() + self.d.timeout
        while time.monotonic() < deadline:
            current = self.d.shell('am get-current-user').stdout.strip()
            state = self.d.shell(f'am get-started-user-state {user}', check=False).stdout.strip()
            if current == str(user) and 'RUNNING_UNLOCKED' in state:
                return dict(android_user=user, state='RUNNING_UNLOCKED', transport='Android am; not Cyclone bootstrap acknowledgement')
            time.sleep(1)
        raise RuntimeError('USER_NOT_ACTIVE_OR_UNLOCKED')

    def launch(self):
        if not self.apk.is_file():
            raise ValueError('DIAGNOSTICS_APK_MISSING')
        # Refuse any APK other than the side-by-side debug diagnostics probe.
        candidates = list(Path(self.d.adb).resolve().parents[1].glob('build-tools/*/aapt*'))
        aapt = next((p for p in sorted(candidates, reverse=True) if p.name in ('aapt', 'aapt.exe')), None)
        if aapt is None:
            raise ValueError('AAPT_MISSING')
        badging = subprocess.run([str(aapt), 'dump', 'badging', str(self.apk.resolve())], capture_output=True, text=True, check=True).stdout
        if not re.search(r"^package: name='dev\.cyclone\.cloak\.probe\.diag'", badging, re.M) or 'dev.cyclone.cloak.probe.DiagnosticsProbeActivity' not in badging:
            raise ValueError('NOT_DIAGNOSTICS_PROBE_APK')
        for user in sorted({self.m['user'], self.m['target_user']}):
            installed = self.d.run('install', '--user', user, '-r', self.apk.resolve())
            if 'Success' not in installed.stdout:
                raise RuntimeError('PROBE_INSTALL_FAILED')
        self.activate(self.m['user'])
        self.d.shell(f'am force-stop --user {self.m["user"]} {PACKAGE}')
        return dict(package=PACKAGE, apk_sha256=hashlib.sha256(self.apk.read_bytes()).hexdigest(),
                    android_user=self.m['user'], permissions_granted_by_harness=False)

    def capture(self, item, cold=False):
        user = item['user']
        artifact = self.d.root('sha256sum /data/adb/modules/cyclone_cloak/zygisk/arm64-v8a.so').stdout.split()[0]
        if artifact != self.m['artifact']:
            raise RuntimeError('MODULE_ARTIFACT_CHANGED')
        _, index = self.d.index()
        if index['entries'].get(f'{user}/{PACKAGE}', {}).get('key') != digest([self.m['run_id'], user]):
            raise RuntimeError('PROBE_OVERLAY_LOST')
        if int(self.d.shell('am get-current-user').stdout.strip()) != user:
            raise RuntimeError('WRONG_FOREGROUND_USER')
        request = uuid.uuid4().hex
        body = encoded(self.body(item)).decode('utf-8')
        self.d.shell(f'run-as {PACKAGE} --user {user} sh -c '+shlex.quote('mkdir -p files; cat > files/expected-profile.json'), data=body)
        before_pid = self.d.shell(f'pidof {PACKAGE}', check=False).stdout.strip()
        if cold:
            self.d.shell(f'am force-stop --user {user} {PACKAGE}')
        command = f'am start --user {user} -n {PACKAGE}/dev.cyclone.cloak.probe.DiagnosticsProbeActivity --activity-clear-top --activity-single-top --ez external_profile true --ez no_permission_prompt true --ei expected_user {user} --es request_id {request}'
        result = self.d.shell(command)
        if re.search(r'Error|Exception', result.stdout + result.stderr):
            raise RuntimeError('PROBE_LAUNCH_FAILED')
        deadline = time.monotonic() + self.d.timeout
        while time.monotonic() < deadline:
            output = self.d.shell(f'run-as {PACKAGE} --user {user} cat files/report.json', check=False)
            try:
                report = json.loads(output.stdout)
                if report.get('request_id') != request:
                    time.sleep(.3)
                    continue
                if report.get('uid', -1) // 100000 != user or report.get('package') != PACKAGE or report.get('profile_sha256') != item['sha256']:
                    raise ValueError('REPORT_PROFILE_OR_USER_MISMATCH')
                if cold and str(report.get('pid')) in before_pid.split():
                    raise RuntimeError('COLD_RESTART_NOT_OBSERVED')
                report = redact_report(report)
                report.update(captured_at=utc(), android_user=user, cold_restart_requested=cold,
                              artifact=self.m['artifact'], request_id=request, process_reused=str(report.get('pid')) in before_pid.split())
                return report
            except json.JSONDecodeError:
                pass
            time.sleep(.3)
        raise RuntimeError('PROBE_REPORT_TIMEOUT')

    def probes(self):
        return self.capture(self.m['selections'][0])

    def switch(self):
        return self.activate(self.m['target_user'])

    def post_switch_probes(self):
        save(self.output / ARTIFACTS['post-switch-probes'], dict(status='STARTED'))
        item = self.m['selections'][1]
        warm = self.capture(item)
        save(self.output / ARTIFACTS['post-switch-probes'], dict(after_switch=warm))
        cold = self.capture(item, cold=True)
        return dict(after_switch=warm, after_cold_restart=cold)

    def diff(self):
        before = json.loads((self.output / ARTIFACTS['probes']).read_text(encoding='utf-8'))
        post = json.loads((self.output / ARTIFACTS['post-switch-probes']).read_text(encoding='utf-8'))
        changed = compare(before, post['after_switch'])
        restarted = compare(post['after_switch'], post['after_cold_restart'])
        return dict(before_to_after=changed, after_to_cold_restart=restarted,
                    **classify([before, *post.values()], changed + restarted))

    def crash_log(self):
        # Bounded and scoped: no global dumps, bugreport, buffers cleared, or foreign app content.
        crash = self.d.run('logcat', '-b', 'crash', '-d', '-t', '200', '-v', 'epoch').stdout
        # Keep only fixed evidence classes; raw native traces can include private paths/register contents.
        safe = crash_classes(crash, self.m['started_epoch'])
        (self.output / ARTIFACTS['crash-log']).write_text('\n'.join(safe) + '\n', encoding='utf-8')
        events = self.d.run('logcat', '-d', '-t', '1000', '-v', 'brief', 'CloakProbeDiagnostics:I', '*:S').stdout
        clean = []
        for line in events.splitlines():
            try:
                event = json.loads(line[line.index('{'):])
                if event.get('timestamp', 0) < self.m['started_epoch'] * 1000:
                    continue
                clean.append(json.dumps({k: event[k] for k in ('timestamp', 'process', 'thread', 'field', 'status') if k in event}))
            except (ValueError, json.JSONDecodeError):
                pass
        (self.output / 'phase-10-logcat.log').write_text('\n'.join(clean) + '\n', encoding='utf-8')
        return dict(crash_classes=sorted(set(safe)), scope='bounded historical crash buffer; correlation only', log_events=len(clean))

    def available_reports(self):
        reports = []
        for name in ('probes','post-switch-probes'):
            path = self.output / ARTIFACTS[name]
            if not path.is_file() or self.m['phases'].get(name,{}).get('status') not in ('COMPLETED','FAILED'):
                continue
            def visit(value):
                if isinstance(value,dict) and isinstance(value.get('rows'),list):
                    try: reports.append(parse_report(value))
                    except ValueError: pass
                elif isinstance(value,dict):
                    for key in ('after_switch','after_cold_restart','partial'):
                        if key in value: visit(value[key])
            visit(json.loads(path.read_text(encoding='utf-8')))
        return reports

    def summary(self):
        phases = self.m['phases']
        diff = self.output / ARTIFACTS['diff']
        verdict = json.loads(diff.read_text(encoding='utf-8')) if diff.is_file() and phases.get('diff', {}).get('status') == 'COMPLETED' else classify(self.available_reports())
        failed = [name + ':' + p.get('reason', 'FAILED') for name, p in phases.items() if p['status'] == 'FAILED']
        missing = [name for name in PHASES[:-1] if phases.get(name, {}).get('status') != 'COMPLETED']
        if self.m['phases'].get('crash-log', {}).get('own_process_crash'):
            failed.append('crash-log:CLOAK_PROCESS_CRASH')
        if failed:
            verdict['result'] = 'FAIL'
        elif missing:
            verdict['result'] = 'INCOMPLETE'
        ended = datetime.fromisoformat(self.m['finished_at']).timestamp() if self.m.get('finished_at') else time.time()
        duration = round(ended - self.m['started_epoch'], 1)
        selections = self.m.get('selections', [])
        bad_ids = sorted({r['id'] for report in self.available_reports() for r in report['rows'] if r['status'] == 'FAIL'})
        for group in ('before_to_after', 'after_to_cold_restart'):
            bad_ids += [r['id'] for r in verdict.get(group, []) if r['severity'] == 'FAIL']
        bad_ids = sorted(set(bad_ids))
        detail = ('; rows: ' + ', '.join(bad_ids[:6])) if bad_ids else ''
        text = '\n'.join([
            f'Scenario: {self.m["scenario"]}', f'Result: {verdict["result"]}', f'Duration: {duration}s',
            'Profile: ' + (str(selections[0].get('profile_id')) if selections else 'not selected'),
            f'Android user: {self.m.get("user", "not recorded")} -> {self.m.get("target_user", "not recorded")}',
            f'Critical failures: {verdict.get("critical_failures", 0) + len(failed)}' + ('; ' + ', '.join(failed) if failed else '') + detail,
            f'Warnings: {verdict.get("warnings", 0)}' + ('; missing phases: ' + ', '.join(missing) if missing else ''),
            f'Blocked rows: {verdict.get("blocked_rows", 0)}',
            'Artifacts: manifest.json; phase-*.json/log; evidence-bundle.zip',
            'Scope: local probe evidence; existing production bindings audited, not rewritten; no platform verdict.',
            ''])
        (self.output / 'summary.md').write_text(text, encoding='utf-8')
        self.m['result'] = verdict['result']
        return verdict

    def phase(self, name):
        started = time.monotonic()
        try:
            # Earlier phases must have actually completed, not merely left a file behind.
            required = {'publish': ['bind'], 'launch': ['publish'], 'probes': ['launch'],
                        'switch': ['probes'], 'post-switch-probes': ['switch'], 'diff': ['probes', 'post-switch-probes']}
            for dependency in required.get(name, []):
                record = self.m['phases'].get(dependency, {})
                if record.get('status') != 'COMPLETED' or record.get('artifact_sha256') != hashlib.sha256((self.output / ARTIFACTS[dependency]).read_bytes()).hexdigest():
                    raise ValueError('PREREQUISITE_MISSING_OR_CHANGED:' + dependency)
            value = getattr(self, name.replace('-', '_'))()
            for later in PHASES[PHASES.index(name)+1:]:
                if later in self.m['phases']:
                    self.m['phases'][later]['status'] = 'STALE'
            if name not in ('crash-log', 'summary'):
                save(self.output / ARTIFACTS[name], value)
            self.m['phases'][name] = dict(status='COMPLETED', duration_ms=round((time.monotonic()-started)*1000),
                artifact_sha256=hashlib.sha256((self.output / ARTIFACTS[name]).read_bytes()).hexdigest(), checked_at=utc())
        except (Exception, KeyboardInterrupt) as error:
            reason = str(error) if isinstance(error, (ValueError, RuntimeError)) and re.fullmatch('[A-Z_]+(?::[a-z-]+)?', str(error)) else type(error).__name__
            self.m['phases'][name] = dict(status='FAILED', reason=reason, checked_at=utc())
            if name not in ('crash-log', 'summary'):
                failure = dict(status='FAILED', reason=reason)
                partial = self.output / ARTIFACTS[name]
                if name == 'post-switch-probes' and partial.is_file():
                    failure['partial'] = json.loads(partial.read_text(encoding='utf-8'))
                save(partial, failure)
            elif name == 'summary':
                self.m['result'] = 'FAIL'
                (self.output/'summary.md').write_text('Scenario: '+self.m['scenario']+'\nResult: FAIL\nCritical failures: SUMMARY_GENERATION_FAILED\n', encoding='utf-8')
            else:
                (self.output/ARTIFACTS[name]).write_text('CRASH_LOG_COLLECTION_FAILED\n', encoding='utf-8')
                (self.output/'phase-10-logcat.log').write_text('LOG_COLLECTION_FAILED\n', encoding='utf-8')
        if name == 'crash-log' and 'value' in locals():
            self.m['phases'][name]['own_process_crash'] = bool(value['crash_classes'])
        self.checkpoint()
        return self.m['phases'][name]['status'] == 'COMPLETED'

    def bundle(self):
        tmp = self.output / 'evidence-bundle.zip.tmp'
        with ZipFile(tmp, 'w', ZIP_DEFLATED) as z:
            for name in ['manifest.json', *ARTIFACTS.values(), 'phase-10-logcat.log']:
                file = self.output / name
                if file.is_file():
                    z.write(file, name)
        os.replace(tmp, self.output / 'evidence-bundle.zip')


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--scenario', required=True)
    parser.add_argument('--phase', choices=('all', *PHASES), default='all')
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial')
    parser.add_argument('--user', type=int)
    parser.add_argument('--target-user', type=int)
    parser.add_argument('--cleanup', action='store_true')
    parser.add_argument('--full-profile', action='store_true', default=None)
    parser.add_argument('--timeout', type=int, default=90)
    parser.add_argument('--apk', type=Path, default=REPO/'android/probe/build/outputs/apk/diagnostics/debug/probe-diagnostics-debug.apk')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args(argv)
    if not re.fullmatch('[a-z][a-z0-9-]{0,63}', args.scenario) or any(v is not None and v < 0 for v in (args.user, args.target_user)):
        parser.error('Invalid scenario or Android user')
    args.adb = shutil.which(args.adb) or args.adb
    output = args.output or REPO/'docs/runtime-evidence'/f'{datetime.now():%Y-%m-%d}-{args.scenario}'
    if output.resolve() == REPO or output.resolve() in REPO.parents:
        parser.error('Output must be a dedicated evidence directory')
    allowed = {'manifest.json','.gitignore','evidence-bundle.zip','phase-10-logcat.log',*ARTIFACTS.values()}
    if output.exists() and any(p.name not in allowed and not p.name.endswith('.tmp') for p in output.iterdir()):
        parser.error('Output contains unrelated files; choose a dedicated evidence directory')
    output.mkdir(parents=True, exist_ok=True)
    (output/'.gitignore').write_text('*\n', encoding='utf-8')  # local raw evidence is never staged accidentally
    path = output/'manifest.json'
    manifest = json.loads(path.read_text(encoding='utf-8')) if path.exists() else dict(format=1,
        run_id=uuid.uuid4().hex, scenario=args.scenario, started_at=utc(), started_epoch=time.time(), phases={})
    if manifest['scenario'] != args.scenario or (args.phase == 'all' and manifest.get('finished_at')):
        parser.error('Evidence already belongs to a run; choose another --output or scenario')
    for name, value in (('user', args.user), ('target_user', args.target_user), ('full_profile', args.full_profile), ('serial', args.serial)):
        if name in manifest and value is not None and manifest[name] != value:
            parser.error('Run configuration changed; choose another output directory')
        if value is not None:
            manifest[name] = value
    manifest.setdefault('full_profile', False)
    if not manifest.get('serial'):
        found = subprocess.run([args.adb, 'devices'], capture_output=True, text=True, check=True).stdout
        devices = re.findall(r'^(\S+)\s+device$', found, re.M)
        if len(devices) != 1:
            parser.error('Select exactly one attached device using --serial')
        manifest['serial'] = devices[0]
    harness = Harness(output, manifest, Device(args.adb, manifest['serial'], args.timeout), args.apk)
    try:
        if args.phase == 'all':
            for name in PHASES[:-2]:
                if not harness.phase(name):
                    for skipped in PHASES[PHASES.index(name)+1:-2]:
                        manifest['phases'][skipped] = dict(status='NOT_RUN', reason='EARLIER_PHASE_FAILED')
                        save(output/ARTIFACTS[skipped], manifest['phases'][skipped])
                    break
        elif args.phase not in ('summary', 'crash-log'):
            harness.phase(args.phase)
    finally:
        if args.phase == 'all' or args.cleanup:
            if manifest.get('overlay_active'):
                try:
                    manifest['cleanup'] = harness.overlay(remove=True)
                except Exception:
                    manifest['phases']['cleanup'] = dict(status='FAILED', reason='PROBE_CLEANUP_FAILED_RETRY_REQUIRED')
            if 'original_user' in manifest:
                try:
                    manifest['restore_user'] = harness.activate(manifest['original_user'])
                except Exception:
                    manifest['phases']['restore-user'] = dict(status='FAILED', reason='ORIGINAL_USER_RESTORE_FAILED')
            manifest['finished_at'] = utc()
        if args.phase in ('all', 'crash-log'):
            harness.phase('crash-log')
        harness.phase('summary')
        harness.bundle()
    print((output/'summary.md').read_text(encoding='utf-8'))
    print('Evidence: ' + str(output))
    return int(manifest.get('result') in ('FAIL', 'INCOMPLETE')) if args.phase == 'all' else int(manifest['phases'][args.phase]['status'] != 'COMPLETED')


if __name__ == '__main__':
    raise SystemExit(main())
