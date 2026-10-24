# Tonight's plan: from rc.2 to a measured Play verdict

Written 2026-10-24 after the rc.2 publish. Rung 2 is closed and published. This plan covers
Rung 3 and the small Rung 2 leftovers, in strict order.

## The one hard prerequisite

Rung 3 cannot start without owner-side Google setup. Nothing in the repo substitutes for it:

1. A Google Cloud project linked to the Play Console app, with the Play Integrity API enabled.
2. Its **project number** (numeric, not the ID). The evidence app asks for it in a text field.
3. An authorized ADC identity on this machine for the decode backend
   (`gcloud auth application-default login`). No ADC file exists yet.
4. An external private RSA key for the exchange, with its public half provisioned as a trust pin
   in the app's private config. No provider is trusted by default.

Until 1-4 exist, the run can capture a token but the verdict stays
`BACKEND_VERIFICATION_REQUIRED`, which is a NOT_RUN, not a result. Do this first; everything else
below is parallel filler while it is being set up.

## Ordered sprints

### T1. WebView UA exception (uncensored, small)

`envelope.webview.defaultUa` and `instanceUa` throw `AndroidRuntimeException` on the capture worker
thread while the `userAgent()` hook is live. WebView wants a Looper thread; the capture runs on a
plain `Thread`. Fix by giving those two readers a Looper-backed context, or by capturing the UA on
the main thread and passing it in. Add a probe test. Deliverable: both rows PASS or a documented
ceiling with the exact exception.

### T2. Google setup (owner, blocking)

Steps 1-4 above. Deliverable: project number recorded, ADC present, keypair generated, public pin
provisioned. Verify with one decode call against a throwaway token before trusting the setup.

### T3. Integrity measurement (uncensored)

One bound profile on user 17, one cold launch, one explicit token request, private export, one
backend decode. Deliverable: one verdict record with request hash, response fields and response
hash, bound to profile digest and artifact digest. Measurement only, no tuning in the same sprint.

### T4. Verdict tuning loop (uncensored, max three)

Only if T3 is not PASS. Adjust the profile fields the failed response names, nothing else. Hard cap
three loops, then stop and document the ceiling with the exact failing field.

### T5. Real-application observation (delegated)

Pick one neutral app that renders many device surfaces, bind it, capture its own displayed values
and compare field-by-field to the published body. Deliverable: MATCH/MISMATCH table. The delegated
agent can run this as soon as T1 lands; it does not need the verdict.

### T6. One banking app, one cold launch (uncensored, observation only)

Does it reach its normal non-error state. No account, no claim beyond the observation.

### T7. Release (delegated)

Only if T3 or T4 produced a verdict worth shipping. Otherwise ship the T1 fix as rc.3 and keep the
verdict work open.

## Split

Uncensored: T1, T3, T4, T6. Delegated: T5, T7, plus evidence packaging for T3.

## Stop rules

- Stop at the first failed gate and report; do not retry past it.
- A token without a decode is NOT_RUN, never PASS.
- Never promote OBSERVED, MISSING or BLOCKED to VERIFIED.
- No platform verdict claim from local facts.

## Realistic outcome tonight

With the Google setup done early: T1, T3 and probably one tuning loop. Without it: T1, T5 and the
setup itself, with the verdict slipping to tomorrow. The setup is the only thing that decides which
night this is.
