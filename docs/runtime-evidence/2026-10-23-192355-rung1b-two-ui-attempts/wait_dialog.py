"""Bounded permission-dialog observation only; never performs an input action."""
import importlib.util
from pathlib import Path
import sys
import time
import xml.etree.ElementTree as ET

O=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('ops',O/'run_ops.py')
o=importlib.util.module_from_spec(spec);spec.loader.exec_module(o)
attempt=int(sys.argv[1]);started=time.monotonic();observations=[]
while True:
    o.b.shell('uiautomator dump /data/local/tmp/cloak-rung1b-final-ui.xml')
    raw=o.b.shell('cat /data/local/tmp/cloak-rung1b-final-ui.xml')
    elapsed=time.monotonic()-started
    path=f'attempt-{attempt}-hierarchy-{len(observations):02}.xml'
    (O/path).write_text(raw,encoding='utf8')
    nodes=list(ET.fromstring(raw).iter('node'))
    pending=any(n.get('resource-id')=='com.android.permissioncontroller:id/permission_allow_foreground_only_button' for n in nodes)
    observations.append(dict(elapsed_seconds=round(elapsed,2),hierarchy=path,location_pending=pending))
    if not pending or elapsed>=60:
        break
    time.sleep(min(10,max(0,60-elapsed)))
focus=o.b.shell('dumpsys window displays')
o.b.save(f'attempt-{attempt}-wait.json',dict(location_cleared=not pending,
    elapsed_seconds=round(time.monotonic()-started,2),observations=observations,
    focus_lines=[l for l in focus.splitlines() if 'mCurrentFocus=' in l or 'mFocusedApp=' in l]))
print('LOCATION_CLEARED' if not pending else 'LOCATION_STILL_PENDING',attempt,round(time.monotonic()-started,2),flush=True)
