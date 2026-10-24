package dev.cyclone.cloak.hook;

import android.content.pm.PackageInfo;
import android.hardware.Sensor;
import android.os.Parcel;
import org.json.JSONObject;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

/** Instantiated only inside a bound process. Callbacks capture one immutable process snapshot. */
final class RuntimeEnvelopeHooks {
    static volatile RuntimeEnvelope current = RuntimeEnvelope.parse("");
    private RuntimeEnvelopeHooks() { }

    interface Read { Object value(Pine.CallFrame frame) throws Exception; }
    static MethodHook guarded(Read read) {
        return new MethodHook() {
            @Override public void afterCall(Pine.CallFrame frame) {
                // Never turn a denied/unavailable host API into a successful fabricated result.
                if (frame.hasThrowable()) return;
                try { frame.setResult(read.value(frame)); }
                catch (RuntimeEnvelope.Unavailable missing) { frame.setThrowable(missing); }
                catch (Throwable implementationError) {
                    android.util.Log.i("CloakModule", "envelope reader implementation error");
                }
            }
        };
    }
    static MethodHook scoped(RuntimeEnvelope envelope, Read read) {
        final MethodHook callback = guarded(read);
        return new MethodHook() {
            @Override public void afterCall(Pine.CallFrame frame) throws Throwable {
                if (envelope.active) callback.afterCall(frame);
            }
        };
    }

    static void install(String profile, String privateDirectory) {
        final RuntimeEnvelope envelope = RuntimeEnvelope.parse(profile);
        current = envelope;
        if (!envelope.active) return;
        hook("android.hardware.SensorManager", "getSensorList", scoped(envelope, frame -> {
            List<?> source = (List<?>) frame.getResult();
            if (source == null) return null;
            java.util.Set<Integer> allowed = envelope.sensorTypes();
            List<Sensor> result = new ArrayList<>();
            java.util.Set<Integer> seen = new java.util.HashSet<>();
            // Filter only real visible objects. Never construct a sensor or change registration.
            for (Object item : source) {
                Sensor sensor = (Sensor) item;
                if (!allowed.contains(sensor.getType())) continue;
                if (seen.add(sensor.getType())) result.add(sensor);
            }
            return java.util.Collections.unmodifiableList(result);
        }), int.class);
        sensor(envelope, "getName", "name");
        sensor(envelope, "getMinDelay", "min_delay_us");
        sensor(envelope, "getMaxDelay", "max_delay_us");
        sensor(envelope, "getMaximumRange", "range");
        sensor(envelope, "getResolution", "resolution");
        MethodHook defaultSensor = scoped(envelope, frame -> {
            if (!(frame.getResult() instanceof Sensor)) return frame.getResult();
            try { envelope.sensor(((Sensor) frame.getResult()).getType()); }
            catch (RuntimeEnvelope.Unavailable absent) { return null; }
            return frame.getResult();
        });
        hook("android.hardware.SensorManager", "getDefaultSensor", defaultSensor, int.class);
        hook("android.hardware.SensorManager", "getDefaultSensor", defaultSensor, int.class, boolean.class);

        hook("android.app.ApplicationPackageManager", "getPackageInfo", packageHook(envelope), String.class, int.class);
        hook("android.app.ApplicationPackageManager", "getInstalledPackages", packageListHook(envelope), int.class);
        // API 33 overload delegates on some builds; install independently where it exists.
        try {
            hook("android.app.ApplicationPackageManager", "getPackageInfo", packageHook(envelope),
                String.class, Class.forName("android.content.pm.PackageManager$PackageInfoFlags"));
            hook("android.app.ApplicationPackageManager", "getInstalledPackages", packageListHook(envelope),
                Class.forName("android.content.pm.PackageManager$PackageInfoFlags"));
        } catch (ClassNotFoundException ignored) { /* API limitation. */ }
        hook("android.webkit.WebView", "getCurrentWebViewPackage", scoped(envelope, frame -> {
            PackageInfo host = (PackageInfo) frame.getResult();
            if (host == null) throw new RuntimeEnvelope.Unavailable();
            return packageCopy(host, envelope.webProvider(host.packageName));
        }));
        hook("android.webkit.WebSettings", "getDefaultUserAgent", scoped(envelope, frame -> userAgent(envelope)), android.content.Context.class);
        hook("android.opengl.GLES20", "glGetString", scoped(envelope, frame -> {
            // Keep GL error/no-context behavior, version/extensions/vendor and actual rendering intact.
            if (!Integer.valueOf(0x1F01).equals(frame.args[0])) return frame.getResult();
            Object value = frame.getResult();
            return value == null || "".equals(value) ? value : envelope.gpu();
        }), int.class);
        java.util.Map<Object, Boolean> filesystems = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
        MethodHook trackFilesystem = scoped(envelope, frame -> {
            trackFilesystem(filesystems, frame.thisObject, privateDirectory, (String) frame.args[0]);
            return frame.getResult();
        });
        try { Pine.hook(android.os.StatFs.class.getDeclaredConstructor(String.class), trackFilesystem); }
        catch (Throwable error) { android.util.Log.i("CloakModule", "envelope filesystem constructor unavailable"); }
        hook("android.os.StatFs", "restat", trackFilesystem, String.class);
        hook("android.os.StatFs", "getAvailableBytes", scoped(envelope, frame -> {
            Boolean scoped = filesystems.get(frame.thisObject);
            if (scoped == null) throw new RuntimeEnvelope.Unavailable();
            if (!scoped) return frame.getResult();
            android.os.StatFs fs = (android.os.StatFs) frame.thisObject;
            return envelope.availableBytes(fs.getTotalBytes(), fs.getBlockSizeLong());
        }));
        hook("android.os.StatFs", "getAvailableBlocksLong", scoped(envelope, frame -> {
            Boolean scoped = filesystems.get(frame.thisObject);
            if (scoped == null) throw new RuntimeEnvelope.Unavailable();
            if (!scoped) return frame.getResult();
            android.os.StatFs fs = (android.os.StatFs) frame.thisObject;
            long blockSize = fs.getBlockSizeLong();
            if (blockSize <= 0) throw new RuntimeEnvelope.Unavailable();
            return envelope.availableBytes(fs.getTotalBytes(), blockSize) / blockSize;
        }));
        hook("android.telephony.TelephonyManager", "getSimState", scoped(envelope, frame -> envelope.simState(0)));
        hook("android.telephony.TelephonyManager", "getSimState", scoped(envelope, frame -> envelope.simState((Integer) frame.args[0])), int.class);
        hook("android.media.MediaDrm", "getPropertyString", scoped(envelope, frame -> {
            if (!"securityLevel".equals(frame.args[0])) return frame.getResult();
            return envelope.drmLevel((String) frame.getResult());
        }), String.class);
        hook("android.view.Display", "getRealSize", scoped(envelope, frame -> {
            if (frame.args[0] instanceof android.graphics.Point) {
                android.graphics.Point point = (android.graphics.Point) frame.args[0];
                int[] size = envelope.displaySize();
                point.set(size[0], size[1]);
            }
            return frame.getResult();
        }), android.graphics.Point.class);
    }

    private static void sensor(RuntimeEnvelope envelope, String method, String field) {
        hook("android.hardware.Sensor", method, scoped(envelope, frame ->
            envelope.sensorValueWithinHost(((Sensor) frame.thisObject).getType(), field, frame.getResult())));
    }

    static boolean privatePath(String root, String path) throws java.io.IOException {
        if (root == null || root.isEmpty() || path == null) throw new RuntimeEnvelope.Unavailable();
        String base = new java.io.File(root).getCanonicalPath();
        String candidate = new java.io.File(path).getCanonicalPath();
        return candidate.equals(base) || candidate.startsWith(base + java.io.File.separator);
    }
    static void trackFilesystem(java.util.Map<Object, Boolean> scopes, Object instance, String root, String path) throws java.io.IOException {
        // A successful restat must never retain the previous path's scope if canonicalization fails.
        scopes.remove(instance);
        scopes.put(instance, privatePath(root, path));
    }

    static String userAgent(RuntimeEnvelope envelope) {
        PackageInfo visible = android.webkit.WebView.getCurrentWebViewPackage();
        if (visible == null) throw new RuntimeEnvelope.Unavailable();
        envelope.webProvider(visible.packageName);
        return envelope.userAgent();
    }

    static MethodHook packageHook(RuntimeEnvelope envelope) {
        return scoped(envelope, frame -> {
            if (!(frame.getResult() instanceof PackageInfo)) return frame.getResult();
            PackageInfo host = (PackageInfo) frame.getResult();
            String requested = frame.args != null && frame.args.length > 0 && frame.args[0] instanceof String
                ? (String) frame.args[0] : null;
            if (requested == null || !requested.equals(host.packageName) || !RuntimeEnvelope.allowlisted(requested)) return host;
            return declaredOrHost(envelope, host, requested);
        });
    }

    static MethodHook packageListHook(RuntimeEnvelope envelope) {
        return scoped(envelope, frame -> {
            if (!(frame.getResult() instanceof List)) return frame.getResult();
            List<PackageInfo> result = new ArrayList<>();
            for (Object entry : (List<?>) frame.getResult()) {
                PackageInfo host = (PackageInfo) entry;
                result.add(RuntimeEnvelope.allowlisted(host.packageName) && host.packageName != null
                    ? declaredOrHost(envelope, host, host.packageName) : host);
            }
            return result;
        });
    }

    /**
     * An allowlisted package without a declared version is not a contradiction: the host value is
     * already correct for this phone. Returning it keeps the real list working instead of throwing.
     */
    static PackageInfo declaredOrHost(RuntimeEnvelope envelope, PackageInfo host, String name) {
        try {
            JSONObject version = envelope.packageVersion(name);
            return version == null ? host : packageCopy(host, version);
        }
        catch (RuntimeEnvelope.Unavailable absent) { return host; }
    }

    static PackageInfo packageCopy(PackageInfo host, JSONObject version) {
        Parcel parcel = Parcel.obtain();
        try {
            host.writeToParcel(parcel, 0); parcel.setDataPosition(0);
            PackageInfo copy = PackageInfo.CREATOR.createFromParcel(parcel);
            copy.versionName = version.getString("version_name");
            copy.setLongVersionCode(version.getLong("version_code"));
            return copy;
        } catch (org.json.JSONException invalid) { throw new RuntimeEnvelope.Unavailable(); }
        finally { parcel.recycle(); }
    }

    private static void hook(String owner, String name, MethodHook callback, Class<?>... params) {
        try {
            Method method = Class.forName(owner, false, RuntimeEnvelopeHooks.class.getClassLoader()).getDeclaredMethod(name, params);
            Pine.hook(method, callback);
        } catch (Throwable error) {
            android.util.Log.i("CloakModule", "envelope hook unavailable: " + owner + "." + name);
        }
    }
}
