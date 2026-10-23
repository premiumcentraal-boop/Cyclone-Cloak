import json,sys
from pathlib import Path
p=Path(sys.argv[1]); sys.path.insert(0,str(Path.cwd()/'tools')); import run_probe_reversibility as r
b=json.loads((p/'pre-update-production-audit.json').read_text());a=json.loads((p/'post-update-production-audit.json').read_text()); pb=json.loads((p/'pre-update-permissions.json').read_text());pa=json.loads((p/'post-update-permissions.json').read_text())
proof={'production_delta':r.snapshot_delta(b,a),'permission_delta':r.snapshot_delta(pb,pa),'permission_policy_passed':r.effective_permission_state(pb,pa)}
(p/'update-preservation-proof.json').write_text(json.dumps(proof,indent=2)+'\n');print(json.dumps(proof))
assert not proof['production_delta'] and proof['permission_policy_passed']
