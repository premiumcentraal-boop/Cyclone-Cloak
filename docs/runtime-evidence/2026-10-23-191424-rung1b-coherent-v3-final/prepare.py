from pathlib import Path
O = Path(__file__).resolve().parent
P = O.parent / '2026-10-23-185155-rung1b-coherent-v3'
s = (P / 'run_ops.py').read_text()
s = s.replace("O=Path(Path(os.environ['TEMP']+'/cloak-rung1b-out.txt').read_text(encoding='utf-8-sig').strip())", "O=Path(__file__).resolve().parent")
s = s.replace('for u in (17,):', 'for u in (0,17):')
s = s.replace("u==17 and not (O/'ui-permissions-completed.json').exists()", "not (O/f'ui-permissions-completed-{u}.json').exists()")
(O / 'run_ops.py').write_text(s, encoding='utf8')
for name in ('fixture-validation.json', 'profile-c-coherent.json', 'authoring-provenance.json', 'test-counts.json'):
    (O / name).write_bytes((P / name).read_bytes())
