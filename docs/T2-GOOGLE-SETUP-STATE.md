# T2 state: Google setup is blocked on owner UI steps

Written 2026-10-24. Code paths are fine; the remaining work is account and console setup that
only the owner can do.

## Done

- Google Cloud SDK 590.0.1 installed at
  `C:\Users\Agent\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd`.
- ADC saved for `giftswappy@gmail.com` at
  `C:\Users\Agent\AppData\Roaming\gcloud\application_default_credentials.json`.
- ADC verified live: `print-access-token` returns a 253-character token.
- `integrity_backend.py --self-test` passes, 8 checks.
- Project creation request accepted; the API returns a valid operation.

## Blocked

### 1. Terms of Service not accepted (hard blocker)

Creating `cyclone-cloak-integrity` ends `done: true` with error 9:

```
Callers must accept Terms of Service  (type TOS, subject cloud)
```

Until that is accepted, no project can exist for this account, so nothing downstream is possible.
Accept at <https://console.cloud.google.com/> on first visit with this account.

### 2. `playintegrity` scope refused by the gcloud OAuth client

`gcloud auth application-default login --scopes=...,playintegrity` fails at the consent screen with
`Error 400: invalid_scope`. The client does not publish that scope.

Two ways round it, in preference order:

- **Service account key** once a project exists. Broad scope, no consent screen, and it is the
  normal backend pattern. Needs the Play Integrity API enabled on the project first.
- Extend the token scope by hand and point `GOOGLE_APPLICATION_CREDENTIALS` at a private file. More
  moving parts.

ADC as it stands is fine for Cloud project and API work. It is not usable for
`decodeIntegrityToken` yet, confirmed by a live 403 `ACCESS_TOKEN_SCOPE_INSUFFICIENT`.

## Remaining owner steps in order

1. Accept the Cloud Terms of Service.
2. Re-run project creation (the request body is below) or create it in the console.
3. Enable the Play Integrity API on it.
4. Play Console: link the app to that Cloud project and upload the signing certificate
   fingerprint. Both are UI-only.
5. Create a release track and put the evidence APK on it. A sideloaded APK generally gets
   unevaluated verdicts, so this decides whether a verdict is even meaningful.
6. Note the numeric project number. The evidence app asks for it as a typed value.

## Project creation request, ready to repeat

```powershell
$p="C:\Users\Agent\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd"
$tok = (& $p auth application-default print-access-token 2>$null | Out-String).Trim()
$body = '{"projectId":"cyclone-cloak-integrity","name":"Cyclone Cloak Integrity"}'
Invoke-WebRequest -Uri "https://cloudresourcemanager.googleapis.com/v1/projects" -Method Post `
  -Headers @{Authorization="Bearer $tok"; "Content-Type"="application/json"} -Body $body
```

Confirm success by reading
`https://cloudresourcemanager.googleapis.com/v1/projects/cyclone-cloak-integrity` until
`lifecycleState` is `ACTIVE`; the response carries the `projectNumber` to record.

## Do not

- Do not commit the ADC file or the signing key.
- Do not treat a captured token as a verdict; without a decode the row stays
  `BACKEND_VERIFICATION_REQUIRED`.
