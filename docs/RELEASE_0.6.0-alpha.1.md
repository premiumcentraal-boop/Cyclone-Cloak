# Cyclone Cloak 0.6.0-alpha.1

This release extends the profile surface that reaches a scoped app: every profile identifier and the locale/timezone block now render through the module, on top of the Build, telephony, and display coverage from earlier alphas.

- **Identifiers render per profile.** The module reads the full identifier vault from each bound profile. Device serial now reaches the scoped app through the real `ro.serialno` and `ro.boot.serialno` properties, so `Build.getSerial()`-style reads return the profile value. Android ID, advertising ID, App Set ID, Wi-Fi and Bluetooth MAC, both IMEIs, SIM serial, GSF ID, and Widevine ID render under stable `cloak.*` property keys through the same hooked read surface, giving the later Java-level callbacks one consistent source.
- **Locale and timezone apply at process start.** The module rewrites the scoped app's Java-side defaults before its first frame: `Locale.getDefault()` returns the profile language and country, `TimeZone.getDefault()` resolves the profile timezone, and the matching `persist.sys.*` properties agree. Language, country, and timezone are validated by the forge for coherence before a profile can be bound.
- **Coverage ledger updated.** All eleven identifier fields and the three locale fields moved from pending to applied in the coverage ledger, with regression tests that keep the real read paths (`ro.serialno`, `persist.sys.timezone`, `user.language`, `user.country`) wired to the module source.
- **Forge unchanged in shape.** Profiles already carried the identifier vault and locale block; this release is the engine catching up to the contract. No schema change, no migration needed.

Deeper Java-API callbacks (Settings provider Android ID reads, TelephonyManager getters, WifiInfo, MediaDRM) stay on the roadmap; this release renders every identifier through the property surface and lands the locale/timezone rewrite that those callbacks will share.

## First setup or app update

1. Install this release's APK and open Cyclone Cloak.
2. Tap **Check & repair** so Root Doctor schedules the matching module, then restart when it reports **Reboot required**.
3. Open Cyclone Cloak and tap **Check again**. **Ready** confirms the matching module is active and profile state reached the module's root-only directory.
4. Bind a profile and start a bound app to exercise the route. On-device validation for identifiers, locale, and multi-profile isolation is planned for the next test session.
