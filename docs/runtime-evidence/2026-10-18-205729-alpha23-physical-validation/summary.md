# Alpha.23 physical validation — stopped

Tag: v0.11.0-alpha.23; commit: 0d028a947b37013e0ad89a98d1455a79c33bfd3d
Installed companion/module: 0.11.0-alpha.23 / 47
APK SHA-256: 8b605d985e97e31af32cce6263fc7cb4a94bb936f83b8b077d88be714d61c3cd
Module ZIP SHA-256: 7bde12d4780c7398175a9c57819ed0831ae25ca0d4e9b1c17a26e6f067ac5ba0

Result: STOPPED before probe bindings/captures.
Failing phase: post-update production-preservation audit.
Exact delta: accessible binding stores increased from 1 to 2 after Android user 17 started/unlocked. The new file has path hash 4c489272062feb8619282bd0a565fb756cc6ffcbc711bcd7c4933e318d1cfb44.
Existing published entries, bodies, publisher shares, module files, and previously accessible store hashes are unchanged. Permissions/AppOps delta: none.
The before-audit saw user 17 stopped; its store was inaccessible. Its unchanged contents cannot be proved from that baseline. This is an evidence completeness failure, not a confirmed binding mutation.

Actions completed: exact release download/hash/signature/module verification; companion update; normal Magisk module update; one reboot; diagnostics build (107 tests pass); diagnostics-only APK update.
No probe capture, temporary binding, profile-body overlay, connector call, permission change, production binding write, or lifecycle cycle was run.
Cleanup: install scratch removed; no temporary probe entries/bodies/private files created.
Physical linked parity and three-cycle validation remain NOT RUN. No platform verdict.

Release attachments named build-provenance.json / evidence-bundle.zip / summary.md are historical alpha18 evidence; they are not alpha23 physical results.
No CURRENT-STATE or RUNTIME-COVERAGE success claim, commit, push or release was made.
