# Runtime measurements

Generated from `2ec607f9563e9f2ac47ab4245041ca10420d2d36d530e040f862a9c231c5a528`. PASS in a raw report means value equality only. VERIFIED below also requires distinct profiles, repeat stability and restoration of the unbound control.

| Read | Profile A | Profile B | Proof | Reason |
| --- | --- | --- | --- | --- |
| `build.MANUFACTURER` | PASS | PASS | VERIFIED | MATCH |
| `build.BRAND` | PASS | PASS | VERIFIED | MATCH |
| `build.MODEL` | PASS | PASS | VERIFIED | MATCH |
| `build.DEVICE` | PASS | PASS | VERIFIED | MATCH |
| `build.PRODUCT` | PASS | PASS | VERIFIED | MATCH |
| `build.HARDWARE` | PASS | PASS | VERIFIED | MATCH |
| `build.FINGERPRINT` | PASS | PASS | VERIFIED | MATCH |
| `build.ID` | PASS | PASS | VERIFIED | MATCH |
| `build.BOOTLOADER` | OBSERVED | PASS | FAIL | MATCH, NO_PROFILE_EXPECTATION |
| `build.SERIAL` | PASS | PASS | VERIFIED | MATCH |
| `build.getSerial` | PASS | PASS | VERIFIED | MATCH |
| `build.TIME` | PASS | PASS | VERIFIED | MATCH |
| `build.TYPE` | PASS | PASS | MATCH_ONLY | MATCH |
| `build.TAGS` | PASS | PASS | MATCH_ONLY | MATCH |
| `version.SDK_INT` | PASS | PASS | VERIFIED | MATCH |
| `version.RELEASE` | PASS | PASS | VERIFIED | MATCH |
| `version.INCREMENTAL` | PASS | PASS | VERIFIED | MATCH |
| `version.SECURITY_PATCH` | PASS | PASS | VERIFIED | MATCH |
| `build.getRadioVersion` | OBSERVED | PASS | FAIL | MATCH, NO_PROFILE_EXPECTATION |
| `properties.java.ro.product.model` | PASS | PASS | VERIFIED | MATCH |
| `properties.native.ro.product.model` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `properties.java.ro.build.version.sdk` | PASS | PASS | VERIFIED | MATCH |
| `properties.native.ro.build.version.sdk` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `properties.java.ro.build.fingerprint` | PASS | PASS | VERIFIED | MATCH |
| `properties.native.ro.build.fingerprint` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `properties.java.ro.serialno` | PASS | PASS | VERIFIED | MATCH |
| `properties.native.ro.serialno` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `properties.java.ro.sf.lcd_density` | PASS | PASS | MATCH_ONLY | MATCH |
| `properties.native.ro.sf.lcd_density` | PASS | PASS | MATCH_ONLY | MATCH |
| `properties.java.persist.sys.timezone` | PASS | PASS | VERIFIED | MATCH |
| `properties.native.persist.sys.timezone` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `telephony.imei0` | PASS | PASS | VERIFIED | MATCH |
| `telephony.imei1` | PASS | PASS | VERIFIED | MATCH |
| `telephony.deviceId` | PASS | PASS | VERIFIED | MATCH |
| `telephony.simSerial` | PASS | PASS | VERIFIED | MATCH |
| `telephony.phoneCount` | PASS | PASS | VERIFIED | MATCH |
| `telephony.activeModemCount` | PASS | PASS | VERIFIED | MATCH |
| `telephony.networkType` | PASS | PASS | VERIFIED | MATCH |
| `telephony.dataNetworkType` | PASS | PASS | VERIFIED | MATCH |
| `operator.networkNumeric` | PASS | PASS | VERIFIED | MATCH |
| `operator.simNumeric` | PASS | PASS | VERIFIED | MATCH |
| `operator.networkName` | PASS | PASS | VERIFIED | MATCH |
| `operator.simName` | PASS | PASS | VERIFIED | MATCH |
| `operator.networkCountry` | PASS | PASS | VERIFIED | MATCH |
| `operator.simCountry` | PASS | PASS | VERIFIED | MATCH |
| `telephony.subscriberId` | PASS | PASS | VERIFIED | MATCH |
| `settings.secure.androidId` | PASS | PASS | VERIFIED | MATCH |
| `settings.provider.androidId` | PASS | PASS | VERIFIED | MATCH |
| `settings.unrelatedControl` | OBSERVED | OBSERVED | UNMAPPED | NO_PROFILE_EXPECTATION |
| `wifi.objectMac` | PASS | PASS | VERIFIED | MATCH |
| `wifi.serviceMac` | BLOCKED | BLOCKED | BLOCKED | SecurityException |
| `wifi.ssid` | BLOCKED | BLOCKED | BLOCKED | SecurityException |
| `bluetooth.address` | PASS | PASS | VERIFIED | MATCH |
| `bluetooth.name` | BLOCKED | BLOCKED | BLOCKED | SecurityException |
| `advertising.id` | PASS | PASS | VERIFIED | MATCH |
| `drm.deviceUniqueId` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `display.resources.width` | PASS | PASS | MATCH_ONLY | MATCH |
| `display.resources.height` | PASS | FAIL | FAIL | MATCH, VALUE_MISMATCH |
| `display.resources.density` | PASS | PASS | MATCH_ONLY | MATCH |
| `display.real.width` | PASS | PASS | MATCH_ONLY | MATCH |
| `display.real.height` | PASS | FAIL | FAIL | MATCH, VALUE_MISMATCH |
| `display.real.density` | PASS | PASS | MATCH_ONLY | MATCH |
| `display.refresh` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `camera.characteristics` | OBSERVED | OBSERVED | UNMAPPED | NO_PROFILE_EXPECTATION |
| `sensors.list` | OBSERVED | OBSERVED | UNMAPPED | NO_PROFILE_EXPECTATION |
| `locale.default.language` | PASS | PASS | VERIFIED | MATCH |
| `locale.default.country` | PASS | PASS | VERIFIED | MATCH |
| `locale.resources.language` | PASS | PASS | VERIFIED | MATCH |
| `locale.resources.country` | PASS | PASS | VERIFIED | MATCH |
| `timezone.default` | PASS | PASS | VERIFIED | MATCH |
