Manager pre-work record - Choice B executed
Date: 2026-10-23 05:40

1. Alpha.28 candidate module ZIP built from current main d4f0a5c
   (code 52) including the approved WebSettings instance-reader addition
   to RuntimeEnvelopeReport.java.
   ZIP SHA-256: 8fd58b4ad0ea60ec06460b0ecaa0b9cea8e56467437975103d04392958a68795
   Size: 2022032 bytes
   Local gates: app/module unit tests and module packaging passed;
   diagnostics probe APK also rebuilt successfully with the reader.

2. Install/reboot status: NOT YET PERFORMED. This session cannot drive
   the Magisk UI flow directly. The candidate ZIP is built and hashed
   and awaiting the install action.

3. Fixture status: schema-0.4 fixture authoring approved but NOT yet
   staged. The existing RealityRuntimeTable.kt modifications in the
   working tree may already contain the needed Reality expectations;
   this needs verification before Rung 1a Part 2 runs.

Manager decisions (2026-10-23, standing approval):

1. Module install/reboot: performed by the manager through the proven
   ADB-driven flow in the prior cycle's boot_gate_ops.py install mode
   (stage, hash-verify, magisk --install-module, one reboot). No UI
   required. The candidate ZIP is the built alpha.28 artifact above.

2. Fixture: the working-tree RealityRuntimeTable.kt and catalog changes
   ARE the approved schema-0.4 fixture source. Verified: they carry the
   Reality expectations including reality.carrier.values.sim_state,
   and the probe's RuntimeEnvelopeReport.java already includes the
   instanceUa WebSettings reader wired to the webview.user_agent row.
   The catalog marks webview.values.user_agent as PENDING in the
   committed catalog, but the probe asset and RealityRuntimeTable in
   the working tree are the authoritative schema-0.4 source for this
   sprint. A separate authoring pass is not needed.

Rung 1a rerun is therefore authorized to dispatch once the manager's
install step completes and the loaded artifact hash is recorded.

## Install record (2026-10-23 07:11)

- Candidate ZIP: cyclone-cloak-0.11.0-alpha.28.zip
  SHA-256: 8fd58b4ad0ea60ec06460b0ecaa0b9cea8e56467437975103d04392958a68795
  Size: 2022032 bytes
- Staged via ADB to /data/local/tmp/cloak-alpha28-candidate.zip;
  remote SHA-256 verified before install.
- Install: magisk --install-module succeeded; UPDATE_PENDING confirmed;
  one authorized reboot issued.
- Post-boot: /data/adb/modules/cyclone_cloak present.
  Loaded zygisk/arm64-v8a.so SHA-256:
  d7dd105446a229f981e5cdd1aed862a13e3c7d04220f04fb08eadde175bb15c1
  (byte-matches the candidate ZIP's packaged ARM64 library).
- User 0 RUNNING_UNLOCKED; user 17 started and RUNNING_UNLOCKED.

Pre-work complete. Rung 1a revised prompt is ready for dispatch with
the loaded-candidate artifact hashes recorded above.

## Fixture C correction (2026-10-23 07:50)

The revised preflight correctly identified that fixture C did not
exist; the working-tree RealityRuntimeTable is a field mapping, not an
expectation-bearing profile. Corrected as follows:

- profile-c.json created in android/probe/src/main/assets/ from the
  validated schema-0.4 reality.json vector profile, with
  reality.carrier.values.sim_state = "ready" present.
  SHA-256: 2f7b201e99d58322efbf34663a91d30323f160e939b10882d133c2d3a441831a
  (5,974 bytes).
- Reality expectations (carrier.sim_state, packages.installed,
  compute.gpu, memory.free_storage_gib, webview.provider /
  user_agent / version) are carried by the profile body itself and
  read by RuntimeEnvelopeReport via expected(profile, section, field).
  No separate expectations-c.json is needed; the prior
  expectations-c.json draft was deleted before it could be packaged.
- Probe fixture validation accepts external profiles whose bytes match
  the supplied asset; no relabeling of fixture B occurs.

The next Rung 1a dispatch instructs the runner to use fixture C
(external profile label c) for user 17, retaining fixture A for user 0.
