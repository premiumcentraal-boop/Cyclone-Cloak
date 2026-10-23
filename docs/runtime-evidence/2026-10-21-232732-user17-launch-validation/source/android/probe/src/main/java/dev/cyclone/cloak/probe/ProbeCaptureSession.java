package dev.cyclone.cloak.probe;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** One worker's owned inputs/data. Never reads the Activity's changing intent. */
class ProbeCaptureSession {
    final long generation;
    final String requestId;
    final String requestedFixture;
    final boolean external;
    final int expectedUser;
    final JSONArray rows = new JSONArray();
    JSONObject profile;
    String profileDigest;
    JSONObject diagnosticExpectations = new JSONObject();
    private boolean started;

    ProbeCaptureSession(long generation, String requestId, String fixture, boolean external, int user) {
        this.generation = generation;
        this.requestId = requestId;
        this.requestedFixture = fixture;
        this.external = external;
        this.expectedUser = user;
    }

    synchronized boolean startOnce() {
        if (started) return false;
        started = true;
        return true;
    }

    static final Set<String> EXPECTED_IDS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
        "build.MANUFACTURER",
        "build.BRAND",
        "build.MODEL",
        "build.DEVICE",
        "build.PRODUCT",
        "build.HARDWARE",
        "build.FINGERPRINT",
        "build.ID",
        "build.BOOTLOADER",
        "build.SERIAL",
        "build.getSerial",
        "build.TIME",
        "build.TYPE",
        "build.TAGS",
        "version.SDK_INT",
        "version.RELEASE",
        "version.INCREMENTAL",
        "version.SECURITY_PATCH",
        "build.getRadioVersion",
        "properties.java.ro.product.model",
        "properties.native.ro.product.model",
        "properties.java.ro.product.manufacturer",
        "properties.native.ro.product.manufacturer",
        "properties.java.ro.product.brand",
        "properties.native.ro.product.brand",
        "properties.java.ro.product.device",
        "properties.native.ro.product.device",
        "properties.java.ro.product.name",
        "properties.native.ro.product.name",
        "properties.java.ro.build.version.sdk",
        "properties.native.ro.build.version.sdk",
        "properties.java.ro.build.fingerprint",
        "properties.native.ro.build.fingerprint",
        "properties.java.ro.serialno",
        "properties.native.ro.serialno",
        "properties.java.ro.sf.lcd_density",
        "properties.native.ro.sf.lcd_density",
        "properties.java.persist.sys.timezone",
        "properties.native.persist.sys.timezone",
        "properties.nativeCallback.ro.product.model",
        "telephony.simState",
        "telephony.phoneType",
        "telephony.roaming",
        "telephony.imei0",
        "telephony.imei1",
        "telephony.deviceId",
        "telephony.simSerial",
        "telephony.phoneCount",
        "telephony.activeModemCount",
        "telephony.networkType",
        "telephony.dataNetworkType",
        "operator.networkNumeric",
        "operator.simNumeric",
        "operator.networkName",
        "operator.simName",
        "operator.networkCountry",
        "operator.simCountry",
        "telephony.subscriberId",
        "settings.secure.androidId",
        "settings.provider.androidId",
        "settings.unrelatedControl",
        "wifi.objectMac",
        "wifi.serviceMac",
        "wifi.bssid",
        "wifi.linkSpeed",
        "wifi.ssid",
        "bluetooth.address",
        "bluetooth.name",
        "advertising.id",
        "drm.deviceUniqueId",
        "display.resources.width",
        "display.resources.height",
        "display.resources.density",
        "display.real.width",
        "display.real.height",
        "display.real.density",
        "display.refresh",
        "camera.characteristics",
        "sensors.list",
        "locale.default.language",
        "locale.default.country",
        "locale.resources.language",
        "locale.resources.country",
        "timezone.default",
        "process.uid",
        "process.androidUser",
        "packages.visible",
        "runtime.loadedLibraries",
        "runtime.javaBridgeVisible"
    )));

    static final class InvalidCapture extends Exception {
        private static final long serialVersionUID = 1L;
        final String reason;
        final String rowId;
        InvalidCapture(String reason, String rowId) {
            super(reason);
            this.reason = reason;
            this.rowId = rowId;
        }
    }

    void validateComplete() throws Exception {
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            Object id = row == null ? null : row.opt("id");
            if (!(id instanceof String) || ((String) id).trim().isEmpty())
                throw new InvalidCapture("ROW_ID_EMPTY", "");
            if (!EXPECTED_IDS.contains(id)) throw new InvalidCapture("UNEXPECTED_ROW_ID", "");
            if (!seen.add((String) id)) throw new InvalidCapture("DUPLICATE_ROW_ID", (String) id);
            if (!row.has("status") || !row.has("reason") || !row.has("source")
                    || !row.has("expected") || !row.has("actual") || !row.has("detail") || !row.has("duration_ms"))
                throw new InvalidCapture("ROW_INCOMPLETE", (String) id);
        }
        if (!seen.equals(EXPECTED_IDS)) throw new InvalidCapture("REPORT_INCOMPLETE", "");
    }
}
