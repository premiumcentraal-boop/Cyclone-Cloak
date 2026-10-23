"""Run-only UI setup, deliberately without a capture timeout or shell grants."""
import importlib.util
from pathlib import Path
import sys
import uuid
import xml.etree.ElementTree as ET

O = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('ops', O / 'run_ops.py')
o = importlib.util.module_from_spec(spec)
spec.loader.exec_module(o)
u = int(sys.argv[2])
action = sys.argv[1]
if action == 'start':
    o.sp.activate_user(u, o.sp.record_switch)
    request = 'permissions-setup-' + uuid.uuid4().hex
    o.b.save(f'ui-setup-{u}-launch.json', o.sp.launch_with_evidence(u, request))
    print('PERMISSION_SETUP_LAUNCHED', u, request)
elif action == 'inspect':
    o.b.cmd('shell', 'uiautomator dump /data/local/tmp/cloak-rung1b-final-ui.xml')
    raw = o.b.shell('cat /data/local/tmp/cloak-rung1b-final-ui.xml')
    (O / f'ui-{u}-{sys.argv[3]}.xml').write_text(raw, encoding='utf8')
    for n in ET.fromstring(raw).iter('node'):
        if n.get('text'):
            print(n.get('text'), n.get('bounds'), n.get('resource-id'))
elif action == 'tap':
    raw = (O / f'ui-{u}-{sys.argv[3]}.xml').read_text()
    resource = sys.argv[4]
    allowed = {'com.android.permissioncontroller:id/permission_allow_foreground_only_button',
               'com.android.permissioncontroller:id/permission_allow_button'}
    if resource not in allowed:
        raise RuntimeError('UNAUTHORIZED_UI_ACTION')
    nodes = [n for n in ET.fromstring(raw).iter('node') if n.get('resource-id') == resource
             and n.get('enabled') == 'true' and n.get('package') == 'com.google.android.permissioncontroller']
    if len(nodes) != 1:
        raise RuntimeError('OBSERVED_PERMISSION_BUTTON_NOT_UNIQUE')
    import re
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', nodes[0].get('bounds')))
    x, y = (x1+x2)//2, (y1+y2)//2
    result = o.sp.adb_result('shell', f'input tap {x} {y}')
    o.b.save(f'ui-{u}-{sys.argv[3]}-tap.json', dict(resource=resource, x=x, y=y, result=result))
    print('OBSERVED_PERMISSION_BUTTON_TAPPED', resource, x, y)
elif action == 'finish':
    raw = (O / f'ui-{u}-{sys.argv[3]}.xml').read_text()
    if any(n.get('package') == 'com.google.android.permissioncontroller' for n in ET.fromstring(raw).iter('node')):
        raise RuntimeError('PERMISSION_DIALOG_STILL_PRESENT')
    o.b.save(f'ui-permissions-completed-{u}.json', dict(hierarchy=f'ui-{u}-{sys.argv[3]}.xml',
        package_info=o.sp.package_info(o.sp.PACKAGE), no_shell_grants=True))
    print('UI_SETUP_SETTLED', u)
