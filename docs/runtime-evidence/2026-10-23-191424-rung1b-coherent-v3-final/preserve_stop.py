"""Collect bounded, target-scoped stop evidence before authorized cleanup."""
import importlib.util
import json
from pathlib import Path

O = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('ops', O / 'run_ops.py')
o = importlib.util.module_from_spec(spec)
spec.loader.exec_module(o)
dump = o.b.shell('dumpsys input')
selected = []
for line in dump.splitlines():
    if any(v in line for v in ('DispatchEnabled:', 'DispatchFrozen:', 'InputFilterEnabled:',
        'FocusedDisplayId:', 'FocusedWindows:', 'FocusedApplications:', "result='NO_WINDOW'",
        'com.google.android.permissioncontroller/com.android.permissioncontroller.permission.ui.GrantPermissionsActivity')):
        selected.append(line)
o.b.save('permission-input-state.json', dict(lines=selected,
    scope='Selected dispatcher state and permission-controller window only',
    interpretation='WindowManager has focused permission activity; InputDispatcher reports no focused window / NO_WINDOW. No root cause inferred.'))
o.b.save('diagnostics-permissions-at-stop.json', o.sp.package_info(o.sp.PACKAGE))
for user in (0,17):
    report, visibility = o.sp.read_evidence(user, 'report.json')
    o.b.save(f'ui-setup-{user}-last-report.json', dict(report=o.sp.redact(report) if report else None, visibility=visibility))
    markers = {}
    for part in ('start','end'):
        marker, seen = o.sp.read_evidence(user, 'capture-'+part+'.json')
        markers[part] = o.sp.safe_marker(marker)
        markers[part+'_visibility'] = seen
    o.b.save(f'ui-setup-{user}-markers.json', markers)
for pid in (15193,15666):
    (O / f'target-{pid}-logcat.log').write_text(o.b.root(f'logcat -d -b all -t 2000 -v threadtime --pid={pid}', check=False),encoding='utf8')
o.b.save('module-at-stop.json', dict(files=o.b.module_files()))
o.b.save('stop-gate.json', dict(result='NOT_VALIDATED',phase='user-17-permission-setup',
    reason='PERMISSION_UI_NOT_INTERACTIVE',
    location_dialog_remained=True,normal_ui_tap_attempts=1,alternate_taps=0,
    forced_close=0,shell_grants=0,user17_measured_captures=0,
    permission_setup_not_time_bounded=True,
    gateway_control='DEVICE_DISCONNECTED while ADB remains reachable; no alternate control mutation attempted',
    no_code_or_fixture_change_in_resume=True))
o.b.cmd('shell','rm -f /data/local/tmp/cloak-rung1b-final-ui.xml')
print('STOP_EVIDENCE_PRESERVED')
