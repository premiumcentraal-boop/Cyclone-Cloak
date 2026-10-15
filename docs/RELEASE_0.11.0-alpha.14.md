# Cyclone Cloak 0.11.0-alpha.14 — publication budget and companion isolation

App and module packaging version code: **38**.

## Fixes

- Keep the 30-second privilege approval preflight. Once granted, publication uses a shared 120-second work deadline anchored to that grant; subsequent commands do not reset it. The prior per-call timeout increase alone could not override the batch session's 30-second deadline.
- Merge key directories in one copy operation per publisher rather than one per app. Main precedence, whole-key replacement, the existing kernel lock, atomic swap and interrupted-publication recovery remain intact.
- Report a timeout after confirmed root access as a publication failure, rather than claiming an approval window expired. Slow granted shell work does not emit misleading approval-wait notifications.
- Exclude the exact companion package `dev.cyclone.cloak` from runtime hooks in every Android user, even when an existing index contains a self-binding. Stored bindings and published records are preserved. The separate probe package remains eligible.

## Compatibility and upgrade

Install the signed APK and matching module ZIP through the normal update flow, retaining app data. Follow the module manager's restart requirement for the updated native module; this release run performs no device updates or restarts. No profile/binding schema, storage layout, identity semantics, connector protocol or permission changes. Existing binding history remains readable. **Local compatibility evidence is not a platform verdict.**

## Verification and limitations

Local checks passed: **522 app tests, 12 module tests, 229 forge tests, 33 schema tests**, debug APK, ARM64/ARMv7 module packaging, metadata verification and `git diff --check`. Android suites had zero failures, errors or skips with WSL shell coverage enabled; no compiler warnings were reported. Regression tests exercise 327 targets plus another publisher, bounded copy counts, Main override cleanup, session deadlines and timeout classification. Compile-time native assertions and a source integration guard cover companion exclusion; they do not establish device stability.

**Device verification is UNVERIFIED for this artifact.** The alpha.13 evidence shows successful privilege preflight followed by a roughly 30-second publication timeout. Removing its self-binding stopped an observed Health SIGSEGV, which supports the exclusion fix but does not prove causality for every native crash. Recheck large user-17 publication, preservation of Main/other publisher entries, publication completion, Health stability and run-history evidence after installing the matching pair. A 120-second budget is finite; very slow devices may still time out. Earlier evidence does not automatically cover these changed paths.

Assets: `app-release.apk`, `cyclone-cloak-0.11.0-alpha.14.zip`.
