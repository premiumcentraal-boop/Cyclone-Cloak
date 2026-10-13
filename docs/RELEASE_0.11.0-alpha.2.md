Cyclone Cloak v0.11.0-alpha.2 — WebView user agent fix

Fixes the user agent hook that silently failed on device since RC3: the hook
targeted the abstract `android.webkit.WebSettings.getUserAgentString` declaration,
which cannot be instrumented. The hook now attaches when an app first requests its
settings object and resolves the concrete declaration on the WebView provider's
runtime class, walking the class chain until it finds a hookable method. The
rendered user agent value itself is unchanged (profile `ua.value`, unchanged
schema 0.3).

Also included:
- Regression test for the concrete-method resolver (abstract/missing cases).
- Module unit suite now 12 tests, all green.

Device note: RC3 logs showed `Cannot hook abstract methods` for this exact hook;
this release removes that error path. Verified against the unit suite only; the
on-device log line to look for is `webview ua hooked on <provider class>`.

Artifacts: signed companion APK and module zip are attached by CI from this tag.
