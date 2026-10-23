# Runner repair complete — S8 may proceed

The `--expected-version` blocker is fixed. The earlier edit had left two
fragments: an unterminated regex string and a duplicated dangling block after
the `main()` entry point. Both are removed and the branch logic is now intact.

## What changed

- `tools/run_probe_reversibility.py` only. Commit `1d43c96` on main, pushed.
- The preflight now reads, cleanly: `--expected-version` accepts exactly
  `v1.0.0-rc.1` and then expects module/app code 60; without the flag, the
  built-in alpha.27 and alpha.18 expectations apply unchanged.
- Ledger updated (`d2016e1`). Working tree clean.

## Verified locally

```
uv run -- python -m py_compile tools/run_probe_reversibility.py   # passes
uv run -- python tools/run_probe_reversibility.py --help          # usage shows --expected-version
```

No device actions, installs, or releases were performed by this repair run.

## Retry instruction

Re-run the S8 lifecycle smoke exactly as previously instructed, adding:

```
--expected-version v1.0.0-rc.1
```

Stop conditions unchanged: any `FOUND` in a masked row, any
`INSTALLED_VERSION_MISMATCH`, or any other guard failure stops the run and
returns to engineering. Produce the evidence bundle and `summary.md` under
`docs/runtime-evidence/` as before. No source changes, no guard bypasses.
