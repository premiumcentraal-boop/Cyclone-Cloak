import ast,json,sys
from pathlib import Path
p=Path(sys.argv[1]);s=Path('docs/runtime-evidence/2026-10-18-212333-alpha24-display-parity/alpha24_lifecycle_evidence_runner.py').read_text()
s=s.replace('import argparse','import argparse\nfrom datetime import datetime, timedelta, timezone\nimport focused_observer as observer',1)
tree=ast.parse(Path('tools/run_probe_reversibility.py').read_text());node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='probe_failure_evidence');s=s.replace('\ndef phases(users):','\n'+ast.unparse(node)+'\n\ndef phases(users):',1)
s=s.replace('sequence = phases(users)',"sequence = [('bound-cold', 0, True), ('post-removal-warm', 0, False)]")
a=s.index('        comparisons = []',s.index('def run_cycle'));b=s.index('\n    except Exception as error:',a)
s=s[:a]+"        m['restoration'] = {'scope': 'NOT_RUN_FOCUSED_WARM_ONLY'}\n"+s[b:]
s=s.replace("        request = uuid.uuid4().hex", "        if mode == 'post-removal-warm': observer.start(d, prior_pid, user, phase, out)\n        request = uuid.uuid4().hex\n        started_at = float(d.shell('date +%s').stdout.strip())\n        utc_offset = d.shell('date +%z').stdout.strip()")
s=s.replace('        while time.monotonic()<deadline:\n            output', '''        next_exit_check = time.monotonic()
        while time.monotonic()<deadline:
            if time.monotonic() >= next_exit_check:
                next_exit_check = time.monotonic()+2
                failure=probe_failure_evidence(d.shell('dumpsys activity exit-info '+PACKAGE,check=False).stdout,user,started_at,None if cold else prior_pid,utc_offset)
                if failure['reason_code']=='PROBE_CRASHED':
                    pc.save(out/('failed-'+phase+'-exit.json'),dict(failure,request_metadata=context))
                    observer.crashed()
                    raise ValueError('PROBE_CRASHED')
            output''')
s=s.replace("    raise SystemExit(main())", "    args=parse_args()\n    try: code=run_cycle(args)\n    finally: observer.finish()\n    raise SystemExit(code)")
(p/'focused_runner.py').write_text(s,encoding='utf8')
