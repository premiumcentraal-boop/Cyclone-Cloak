package dev.cyclone.cloak.probe;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.ArrayList;
import java.util.List;

/** Wait for permission resolution before measuring; denial remains explicit. */
public final class DiagnosticsProbeActivity extends ProbeActivity {
    private Runnable pendingReads;
    private final List<String> required = new ArrayList<>();

    @Override protected void prepareReads(Runnable ready) {
        required.add(Manifest.permission.ACCESS_FINE_LOCATION);
        required.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33) required.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        if (Build.VERSION.SDK_INT >= 31) required.add(Manifest.permission.BLUETOOTH_CONNECT);
        // Headless harness never waits on a dialog or grants permissions silently.
        if (getIntent().getBooleanExtra("no_permission_prompt", false)) { ready.run(); return; }
        List<String> missing = new ArrayList<>();
        for (String permission : required) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) missing.add(permission);
        }
        // Android 12+ requires requesting the pair together, even if approximate
        // access was already granted and only precise access remains missing.
        if (missing.contains(Manifest.permission.ACCESS_FINE_LOCATION)
                && !missing.contains(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            missing.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (missing.isEmpty()) { ready.run(); return; }
        pendingReads = ready;
        requestPermissions(missing.toArray(new String[0]), 1);
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 1 && pendingReads != null) {
            Runnable ready = pendingReads;
            pendingReads = null;
            ready.run();
        }
    }

    @Override protected String permissionFailure(String id) {
        for (String permission : required) {
            boolean affects = id.equals("bluetooth.name")
                ? permission.equals(Manifest.permission.BLUETOOTH_CONNECT)
                : (id.equals("wifi.serviceMac") || id.equals("wifi.ssid") || id.equals("wifi.bssid"))
                    && !permission.equals(Manifest.permission.BLUETOOTH_CONNECT);
            if (affects && checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return "PERMISSION_DENIED:" + permission;
            }
        }
        return null;
    }
}
