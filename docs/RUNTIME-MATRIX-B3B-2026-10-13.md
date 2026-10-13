# Runtime measurements

Generated from `f87b218e5763a2084093b817c42eaee632390e7d1cb72a686851a469e4e3c4b9`. PASS in a raw report means value equality only. VERIFIED below also requires distinct profiles, repeat stability and restoration of the unbound control.

Requested phases: bound-a. Missing phases are not run; partial runs cannot establish VERIFIED proof.

Phase availability: unbound-before=not run, bound-a=run, bound-a-repeat=not run, bound-b=not run, unbound-after=not run.

| Read | Profile A | Profile B | Proof | Reason |
| --- | --- | --- | --- | --- |
| `build.MANUFACTURER` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `build.BRAND` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `build.MODEL` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `build.DEVICE` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `build.PRODUCT` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `build.HARDWARE` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `build.FINGERPRINT` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `build.ID` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `build.BOOTLOADER` | OBSERVED | not run | INCOMPLETE | INCOMPLETE, NO_PROFILE_EXPECTATION |
| `build.SERIAL` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `build.getSerial` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `build.TIME` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `build.TYPE` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `build.TAGS` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `version.SDK_INT` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `version.RELEASE` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `version.INCREMENTAL` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `version.SECURITY_PATCH` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `build.getRadioVersion` | OBSERVED | not run | INCOMPLETE | INCOMPLETE, NO_PROFILE_EXPECTATION |
| `properties.java.ro.product.model` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `properties.native.ro.product.model` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `properties.java.ro.build.version.sdk` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `properties.native.ro.build.version.sdk` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `properties.java.ro.build.fingerprint` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `properties.native.ro.build.fingerprint` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `properties.java.ro.serialno` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `properties.native.ro.serialno` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `properties.java.ro.sf.lcd_density` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `properties.native.ro.sf.lcd_density` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `properties.java.persist.sys.timezone` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `properties.native.persist.sys.timezone` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `telephony.imei0` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `telephony.imei1` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `telephony.deviceId` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `telephony.simSerial` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `telephony.phoneCount` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `telephony.activeModemCount` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `telephony.networkType` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `telephony.dataNetworkType` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `operator.networkNumeric` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `operator.simNumeric` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `operator.networkName` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `operator.simName` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `operator.networkCountry` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `operator.simCountry` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `telephony.subscriberId` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `settings.secure.androidId` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `settings.provider.androidId` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `settings.unrelatedControl` | OBSERVED | not run | INCOMPLETE | INCOMPLETE, NO_PROFILE_EXPECTATION |
| `wifi.objectMac` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `wifi.serviceMac` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `wifi.ssid` | OBSERVED | not run | INCOMPLETE | INCOMPLETE, NO_PROFILE_EXPECTATION |
| `bluetooth.address` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `bluetooth.name` | OBSERVED | not run | INCOMPLETE | INCOMPLETE, NO_PROFILE_EXPECTATION |
| `advertising.id` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `drm.deviceUniqueId` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `display.resources.width` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `display.resources.height` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `display.resources.density` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `display.real.width` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `display.real.height` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `display.real.density` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `display.refresh` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `camera.characteristics` | FAIL | not run | INCOMPLETE | INCOMPLETE, VALUE_MISMATCH |
| `sensors.list` | OBSERVED | not run | INCOMPLETE | INCOMPLETE, NO_PROFILE_EXPECTATION |
| `locale.default.language` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `locale.default.country` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `locale.resources.language` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `locale.resources.country` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
| `timezone.default` | PASS | not run | INCOMPLETE | INCOMPLETE, MATCH |
