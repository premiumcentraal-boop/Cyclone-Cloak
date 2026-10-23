package dev.cyclone.cloak.probe;

/** Invoke the genuine SDK, which is packaged in diagnostics only, on the probe worker. */
final class AdvertisingIdRead {
    private AdvertisingIdRead() { }

    static Object read(ClassLoader loader, Class<?> contextType, Object context) throws Exception {
        Class<?> client = loader.loadClass("com.google.android.gms.ads.identifier.AdvertisingIdClient");
        Object info = client.getMethod("getAdvertisingIdInfo", contextType).invoke(null, context);
        return info.getClass().getMethod("getId").invoke(info);
    }
}
