# Confirm the WebView UA fix on device (T1)

`3fc15a0` fixes the `AndroidRuntimeException` on both UA rows. The local tests cannot prove it
works, because the JVM has no Android runtime and no real WebView. Only the device can answer.

This is a narrow measurement run, not a full S8. Keep it cheap.

## What to do

1. Rebuild the diagnostics APK from `3fc15a0` and record its SHA-256.
2. Install it for users 0 and 17 on the connected phone (serial `3B171FDJH0061G`).
3. Bind one profile for user 0 as in the S8 flow so the runtime hooks are live, or reuse the
   existing binding if one is present.
4. One cold capture on user 0, then read the two rows:
   - `envelope.webview.defaultUa`
   - `envelope.webview.instanceUa`
5. Uninstall the diagnostics APK afterwards and confirm cleanup.

## Pass and fail

- **PASS**: both rows return `PASS` or `OBSERVED`. `OBSERVED` is expected when the profile declares
  no WebView user agent, which is normal for the current catalog. The fix is about the exception
  disappearing, not about the value.
- **FAIL**: either row still reports `RUNTIME_EXCEPTION:AndroidRuntimeException`, or the whole
  capture stalls. If it stalls, `MAIN_THREAD_TIMEOUT` will appear as the row reason after five
  seconds, which tells us the looper hop itself is the problem.

Do not run the full three-cycle S8 for this. One capture per row is enough.

## Context you need

- The module is unchanged by this fix, so the installed rc.2 module and its pins still apply.
  No module install, no reboot.
- Both readers now hop to the main looper with a bounded wait and fall back to the caller thread
  when no looper exists.
- Everything else about the capture is unchanged.

Report the two rows with their status and reason, plus the diagnostics hash and cleanup result.
Do not promote the rows to VERIFIED; this is still a caller-visible measurement on one artifact.
