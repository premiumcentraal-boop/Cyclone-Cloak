# Runtime measurements

Generated from `137c9e084fd6825c2e506b0f4b4ae8078bdc742184bd886ea2d0c19610847f75`. PASS in a raw report means value equality only. VERIFIED below also requires distinct profiles, repeat stability and restoration of the unbound control.

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
| `build.BOOTLOADER` | OBSERVED | FAIL | FAIL | NO_PROFILE_EXPECTATION, VALUE_MISMATCH |
| `build.SERIAL` | PASS | PASS | VERIFIED | MATCH |
| `build.getSerial` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `build.TIME` | PASS | PASS | VERIFIED | MATCH |
| `build.TYPE` | PASS | PASS | MATCH_ONLY | MATCH |
| `build.TAGS` | PASS | PASS | MATCH_ONLY | MATCH |
| `version.SDK_INT` | PASS | PASS | VERIFIED | MATCH |
| `version.RELEASE` | PASS | PASS | VERIFIED | MATCH |
| `version.INCREMENTAL` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `version.SECURITY_PATCH` | PASS | PASS | VERIFIED | MATCH |
| `build.getRadioVersion` | OBSERVED | FAIL | FAIL | NO_PROFILE_EXPECTATION, VALUE_MISMATCH |
| `properties.java.ro.product.model` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `properties.native.ro.product.model` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `properties.java.ro.build.version.sdk` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `properties.native.ro.build.version.sdk` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `properties.java.ro.build.fingerprint` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `properties.native.ro.build.fingerprint` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `properties.java.ro.serialno` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `properties.native.ro.serialno` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `properties.java.ro.sf.lcd_density` | PASS | PASS | MATCH_ONLY | MATCH |
| `properties.native.ro.sf.lcd_density` | PASS | PASS | MATCH_ONLY | MATCH |
| `properties.java.persist.sys.timezone` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `properties.native.persist.sys.timezone` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `telephony.imei0` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `telephony.imei1` | FAIL | PASS | FAIL | MATCH, VALUE_MISMATCH |
| `telephony.deviceId` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `telephony.simSerial` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `telephony.phoneCount` | BLOCKED | BLOCKED | BLOCKED | ClassCastException |
| `telephony.activeModemCount` | BLOCKED | BLOCKED | BLOCKED | ClassCastException |
| `telephony.networkType` | BLOCKED | BLOCKED | BLOCKED | SecurityException |
| `telephony.dataNetworkType` | BLOCKED | BLOCKED | BLOCKED | SecurityException |
| `operator.networkNumeric` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `operator.simNumeric` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `operator.networkName` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `operator.simName` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `operator.networkCountry` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `operator.simCountry` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `telephony.subscriberId` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `settings.secure.androidId` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `settings.provider.androidId` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `settings.unrelatedControl` | OBSERVED | OBSERVED | UNMAPPED | NO_PROFILE_EXPECTATION |
| `wifi.objectMac` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `wifi.serviceMac` | BLOCKED | BLOCKED | BLOCKED | SecurityException |
| `wifi.ssid` | BLOCKED | BLOCKED | BLOCKED | SecurityException |
| `bluetooth.address` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `bluetooth.name` | BLOCKED | BLOCKED | BLOCKED | SecurityException |
| `advertising.id` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
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
| `locale.default.language` | PASS | FAIL | FAIL | MATCH, VALUE_MISMATCH |
| `locale.default.country` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `locale.resources.language` | PASS | FAIL | FAIL | MATCH, VALUE_MISMATCH |
| `locale.resources.country` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
| `timezone.default` | FAIL | FAIL | FAIL | VALUE_MISMATCH |
