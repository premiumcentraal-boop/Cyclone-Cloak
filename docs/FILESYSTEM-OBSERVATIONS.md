# Filesystem observations format 1

The diagnostics probe adds an optional `filesystem_observations` report section.
The standard probe does not collect it. No permissions or runtime hooks are added.

| ID | Read-only source |
| --- | --- |
| `diagnostics.filesystem.build_prop` | `/system/build.prop` |
| `diagnostics.filesystem.proc_version` | `/proc/version` |
| `diagnostics.filesystem.proc_cpuinfo` | `/proc/cpuinfo` |
| `diagnostics.filesystem.maps_snapshot` | `/proc/self/maps` |

The reviewed definitions live in `observation_surfaces` in
`catalog/reality-runtime-fields.json`. The existing generator copies these into
the probe asset and Kotlin capability table and generates
`schema/filesystem-observations.schema.json`. They are not profile fields:
the 97 Reality contract paths, saved profiles and profile schemas are unchanged.
The existing runtime-contract schema/vector remain unchanged when regenerated.

Each successful row contains a SHA-256 of the exact bytes, byte count, line count
and duration. An unterminated final line counts as one line; an empty file has
zero lines. Content is streamed with a 1 MiB limit per file. Incomplete reads
emit no partial digest or counts. Raw content and exception messages are never
exported. Reads use only the fixed allowlist; report inputs cannot select paths.

Successful reads are always `OBSERVED` with
`OBSERVATION_ONLY_NO_EXPECTATION`. Access denial becomes `BLOCKED` /
`PERMISSION_DENIED`. Missing or unreadable files become `MISSING` /
`READ_UNAVAILABLE`; exceeding the limit becomes `MISSING` /
`READ_LIMIT_EXCEEDED`. The strict schema rejects PASS/VERIFIED, duplicate IDs,
unknown paths and raw-content additions. No observation is a parity assertion.

These reads show the caller-visible view, which may already be transformed by
an installed runtime. They do not independently establish host truth, hardware
equivalence or a platform verdict. Maps hashes can change with process layout;
they must not be used as a stable identity or a restoration expectation.

Physical evidence is pending. This work does not change the existing module
renderers or concealment functionality, and does not package a new release.
