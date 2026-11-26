# Documentation status and responsible use

Review: 2026-11-26. Source baseline: `62a05e4a966119908125016211096983cb258529`.

## Current references

- [README](../README.md): overview and limitations.
- [Current state](CURRENT-STATE.md): implementation and dated verification.
- [RC3 release notes](RELEASE_1.0.0-rc.3.md) and [changelog](../CHANGELOG.md): version-specific changes and test scope.
- [Architecture](ARCHITECTURE.md) and [runtime coverage](RUNTIME-COVERAGE.md): implementation and measured boundaries.
- [Agent guidance](../AGENTS.md): engineering practices.

RC3 documentation identifies version code 62. This review does not independently verify a signed APK, module, device or external verdict.

## Historical records

Dated plans, handoffs, ledgers and runtime evidence document past work, not current work orders. Some older plans explicitly proposed concealment, evasion of security checks or routing around agent safeguards. This review does not erase or endorse those records. Their substantive historical text is retained. Do not treat those proposals as authorized development or relabel runtime behavior to avoid scrutiny.

## Responsible use and verification

- Use authorized test devices and environments only; respect platform and service policies.
- Root-based callbacks can change selected app-visible values. Profiles are not independent hardware or platform certification.
- Never misrepresent identity or attempt to defeat security, fraud-prevention or account restrictions.
- Do not infer an external verdict from local tests, declared configuration, restricted visibility or missing evidence.
- Record implementation, local verification, physical measurement and external verdicts separately.
- Do not delete adverse findings or rewrite historical evidence to imply a stronger result.

## Public evidence and privacy

Screen logs, device dumps and reports for personal identifiers and secrets before publication. A hardware serial appeared in two properties in the alpha.13 getprop text capture; its public-copy values are redacted and its changed bytes are documented. Other copies, Git history, forks, caches and evidence formats have not been sanitized. Document redactions; do not represent a modified public copy as the original hashed artifact. Revoke or rotate exposed credentials if discovered.

This review changes documentation only, not app code, native modules, tests, build configuration, schemas, binaries or release artifacts.
