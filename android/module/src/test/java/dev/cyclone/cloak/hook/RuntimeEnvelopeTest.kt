package dev.cyclone.cloak.hook

import android.content.pm.PackageInfo
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.canyie.pine.Pine
import java.util.concurrent.Executors

/** Synthetic callback tests, not ART/device verification or hardware evidence. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34, 35], manifest = Config.NONE)
class RuntimeEnvelopeTest {
    private fun fixture(id: String = "a") = javaClass.classLoader!!.getResource("envelope-$id.json")!!.readText()
    private fun envelope(id: String = "a") = RuntimeEnvelope.parse(fixture(id))
    private fun unavailable(block: () -> Any?) {
        try { block(); fail("Expected closed result") }
        catch (expected: RuntimeEnvelope.Unavailable) { assertEquals("PROFILE_SURFACE_UNAVAILABLE", expected.message) }
    }
    private fun frame(vararg args: Any?): Pine.CallFrame {
        val member = String::class.java.getMethod("valueOf", Any::class.java)
        return Pine.CallFrame(Pine.HookRecord(member, 0L), null, arrayOf(*args))
    }
    private fun host(pkg: String = "com.android.chrome") = PackageInfo().also {
        it.packageName = pkg; it.versionName = "host"; it.setLongVersionCode(999L)
    }

    @Test fun coldSnapshotsAreStableDistinctAndUnboundRestores() {
        repeat(5) {
            assertEquals("Synthetic GPU a", envelope("a").gpu())
            assertEquals("Synthetic GPU b", envelope("b").gpu())
            assertEquals(34359738368L, envelope("a").availableBytes(100000000000L))
            assertEquals(68719476736L, envelope("b").availableBytes(100000000000L))
            assertEquals("Synthetic-UA-a", envelope("a").userAgent())
            assertEquals("Synthetic-UA-b", envelope("b").userAgent())
            assertEquals(5, envelope("a").simState(0)); assertEquals(2, envelope("b").simState(1))
            assertFalse(RuntimeEnvelope.parse("").active)
            val host = host(); val unbound = frame(host.packageName, 0); unbound.setResult(host)
            RuntimeEnvelopeHooks.packageHook(RuntimeEnvelope.parse("")).afterCall(unbound)
            assertSame(host, unbound.result); assertFalse(unbound.hasThrowable())
        }
    }
    @Test fun legacyAndAbsentRealityDoNotInstallAdditionalReaders() {
        for (v in listOf("0.2", "0.3", "0.4")) assertFalse(RuntimeEnvelope.parse("{\"schema_version\":\"$v\"}").active)
        assertTrue(RuntimeEnvelope.parse("not json").active)
        unavailable { RuntimeEnvelope.parse("not json").gpu() }
    }
    @Test fun snapshotsDoNotShareMutableProfileOrReturnedInventory() {
        val a = envelope(); val b = envelope("b")
        a.sensor(1).put("name", "edited")
        assertEquals("Synthetic accelerometer a", a.sensorValue(1, "name"))
        assertEquals("Synthetic accelerometer b", b.sensorValue(1, "name"))
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = pool.invokeAll(listOf(java.util.concurrent.Callable { a.gpu() }, java.util.concurrent.Callable { b.gpu() }))
            assertNotEquals(results[0].get(), results[1].get())
        } finally { pool.shutdownNow() }
    }
    @Test fun unavailableAndMissingFieldsNeverFallBackToHost() {
        for (availability in listOf("unknown", "unavailable", "foreign")) {
            val p = JSONObject(fixture()); p.getJSONObject("reality").getJSONObject("compute").put("availability", availability)
            unavailable { RuntimeEnvelope.parse(p.toString()).gpu() }
        }
        val p = JSONObject(fixture()); p.getJSONObject("reality").getJSONObject("compute").getJSONObject("values").remove("gpu")
        unavailable { RuntimeEnvelope.parse(p.toString()).gpu() }
    }
    @Test fun sensorDescriptorsHaveExactTypesAndRejectDuplicateTypes() {
        for (id in listOf("a", "b")) {
            val e = envelope(id)
            assertTrue(e.sensorValue(1, "min_delay_us") is Int)
            assertTrue(e.sensorValue(1, "range") is Float)
            unavailable { e.sensor(99) }
            val p = JSONObject(fixture(id)); val inv = p.getJSONObject("reality").getJSONObject("sensors").getJSONObject("values").getJSONArray("inventory")
            inv.put(JSONObject(inv.getJSONObject(0).toString()))
            unavailable { RuntimeEnvelope.parse(p.toString()).sensor(1) }
        }
    }
    @Test fun invalidSensorRangeAndDelayOrderingAreClosed() {
        for (field in listOf("range", "resolution", "max_delay_us")) {
            val p = JSONObject(fixture())
            p.getJSONObject("reality").getJSONObject("sensors").getJSONObject("values").getJSONArray("inventory").getJSONObject(0).put(field, 0)
            unavailable { RuntimeEnvelope.parse(p.toString()).sensorValue(1, field) }
        }
    }
    @Test fun visiblePackageCopiesPreserveMembershipAndOriginalObject() {
        val original = host()
        for ((id, version) in listOf("a" to "101.0.0.0", "b" to "102.0.0.0")) {
            val call = frame(original.packageName, 0); call.setResult(original)
            RuntimeEnvelopeHooks.packageHook(envelope(id)).afterCall(call)
            assertFalse(call.hasThrowable())
            val copy = call.result as PackageInfo
            assertNotSame(original, copy); assertEquals(version, copy.versionName)
            assertEquals(original.packageName, copy.packageName); assertEquals("host", original.versionName)
        }
    }
    @Test fun deniedAndForeignPackagesRemainUntouched() {
        val original = host("org.example.control")
        val call = frame(original.packageName, 0); call.setResult(original)
        RuntimeEnvelopeHooks.packageHook(envelope()).afterCall(call)
        assertSame(original, call.result)
        val denied = frame("com.android.chrome", 0)
        val error = SecurityException("denied"); denied.setThrowable(error)
        RuntimeEnvelopeHooks.packageHook(envelope()).afterCall(denied)
        assertSame(error, denied.throwable)
    }
    @Test fun packageListsDoNotAddRemoveOrRenameVisiblePackages() {
        val source = listOf(host(), host("org.example.control"))
        val call = frame(0); call.setResult(source)
        RuntimeEnvelopeHooks.packageListHook(envelope()).afterCall(call)
        val result = call.result as List<*>
        assertEquals(2, result.size); assertSame(source[1], result[1])
        assertEquals("101.0.0.0", (result[0] as PackageInfo).versionName)
        assertEquals("host", source[0].versionName)
    }
    @Test fun missingAllowlistedVersionFailsClosed() {
        val call = frame("com.google.android.gms", 0); call.setResult(host("com.google.android.gms"))
        RuntimeEnvelopeHooks.packageHook(envelope()).afterCall(call)
        assertTrue(call.throwable is RuntimeEnvelope.Unavailable)
    }
    @Test fun webViewRequiresActualProviderAndConsistentPackageVersionAndUa() {
        for (id in listOf("a", "b")) {
            val e = envelope(id)
            assertEquals("com.google.android.webview", e.webProvider("com.google.android.webview").getString("package"))
            unavailable { e.webProvider("org.example.foreign") }
            val p = JSONObject(fixture(id)); p.getJSONObject("ua").put("value", "conflicting")
            unavailable { RuntimeEnvelope.parse(p.toString()).userAgent() }
            p.getJSONObject("reality").getJSONObject("webview").getJSONObject("values").put("version", "999.0.0.0")
            unavailable { RuntimeEnvelope.parse(p.toString()).webProvider("com.google.android.webview") }
        }
    }
    @Test fun availableCapacityCannotExceedVisibleFilesystemOrInventSkuMeasurement() {
        unavailable { envelope().availableBytes(1) }
        unavailable { envelope().unsupportedCapacity() }
        val p = JSONObject(fixture()); p.getJSONObject("reality").getJSONObject("memory").getJSONObject("values").put("free_storage_gib", "32")
        unavailable { RuntimeEnvelope.parse(p.toString()).availableBytes(100000000000L) }
        assertEquals(34359738368L, envelope().availableBytes(100000000000L, 4096))
        unavailable { envelope().availableBytes(100000000000L, 0) }
        p.getJSONObject("reality").getJSONObject("memory").getJSONObject("values").put("free_storage_gib", 0.1)
        unavailable { RuntimeEnvelope.parse(p.toString()).availableBytes(100000000000L, 4096) }
    }
    @Test fun displayAndSimUseBaseAuthorityAndDoNotLeakOtherSlots() {
        assertArrayEquals(intArrayOf(1080, 2400), envelope().displaySize())
        assertArrayEquals(intArrayOf(1080, 2340), envelope("b").displaySize())
        for (slot in listOf(-1, 1, 2, 999)) assertEquals(0, envelope().simState(slot))
        val p = JSONObject(fixture()); p.getJSONObject("telephony").put("sim_slot_count", 1.5)
        unavailable { RuntimeEnvelope.parse(p.toString()).simState(0) }
        val conflict = JSONObject(fixture()); conflict.getJSONObject("reality").getJSONObject("carrier").getJSONObject("values").put("slots", 2)
        unavailable { RuntimeEnvelope.parse(conflict.toString()).simState(0) }
        p.getJSONObject("telephony").put("sim_slot_count", 3)
        unavailable { RuntimeEnvelope.parse(p.toString()).simState(0) }
    }
    @Test fun drmCanOnlyReturnTheRealMatchingLevel() {
        assertEquals("L3", envelope().drmLevel("L3")); assertEquals("L1", envelope("b").drmLevel("L1"))
        unavailable { envelope().drmLevel("L1") }; unavailable { envelope("b").drmLevel("L3") }
    }
    @Test fun implementationFailureFailsOpenButDataFailureIsClosed() {
        val implementation = frame(); implementation.setResult("host")
        RuntimeEnvelopeHooks.guarded { throw ReflectiveOperationException("synthetic") }.afterCall(implementation)
        assertEquals("host", implementation.result); assertFalse(implementation.hasThrowable())
        val missing = frame(); missing.setResult("host")
        RuntimeEnvelopeHooks.guarded { throw RuntimeEnvelope.Unavailable() }.afterCall(missing)
        assertTrue(missing.throwable is RuntimeEnvelope.Unavailable)
    }
    @Test fun cameraOrdinalDoesNotDependOnReadOrderAndCannotInventCameraIds() {
        repeat(5) {
            for (source in listOf(linkedMapOf("2" to 1, "0" to 1, "1" to 0), linkedMapOf("1" to 0, "0" to 1, "2" to 1))) {
                assertEquals(0, HookBridge.cameraOccurrence("0", 1, source))
                assertEquals(1, HookBridge.cameraOccurrence("2", 1, source))
                assertEquals(0, HookBridge.cameraOccurrence("1", 0, source))
                unavailable { HookBridge.cameraOccurrence("3", 1, source) }
            }
        }
    }
    @Test fun filesystemScopeCannotIncludeOtherPackagesUsersOrParentTraversal() {
        val root = java.io.File(System.getProperty("java.io.tmpdir"), "envelope-test/user17/org.example.bound").canonicalPath
        assertTrue(RuntimeEnvelopeHooks.privatePath(root, "$root/files"))
        assertFalse(RuntimeEnvelopeHooks.privatePath(root, "$root/../org.example.other/files"))
        assertFalse(RuntimeEnvelopeHooks.privatePath(root, "$root/../../user0/org.example.bound/files"))
        assertFalse(RuntimeEnvelopeHooks.privatePath(root, root + "suffix"))
        unavailable { RuntimeEnvelopeHooks.privatePath("", root) }
    }
    @Test fun cameraCannotAdvertiseMorePixelsOrHigherHardwareCapability() {
        for (spec in listOf(intArrayOf(0, 4000, 3000), intArrayOf(1, 8000, 6000))) {
            repeat(5) { assertTrue(HookBridge.cameraSpecSupported(spec, 3, 8192, 6144)) }
            assertFalse(HookBridge.cameraSpecSupported(spec, 0, 2000, 1500))
        }
        assertFalse(HookBridge.cameraSpecSupported(intArrayOf(3, 4000, 3000), 1, 8192, 6144))
        assertFalse(HookBridge.cameraSpecSupported(null, 3, 8192, 6144))
    }
    @Test fun sensorDescriptorsCannotInventBetterPhysicalCapabilities() {
        for (id in listOf("a", "b")) {
            val e = envelope(id)
            assertTrue(e.sensorValueWithinHost(1, "range", 100f) is Float)
            assertTrue(e.sensorValueWithinHost(1, "resolution", 0.001f) is Float)
            unavailable { e.sensorValueWithinHost(1, "range", 1f) }
            unavailable { e.sensorValueWithinHost(1, "resolution", 1f) }
            unavailable { e.sensorValueWithinHost(1, "min_delay_us", 1000000) }
            unavailable { e.sensorValueWithinHost(1, "max_delay_us", 1) }
        }
    }
    @Test fun restatNeverKeepsOldScopeWhenNewPathCannotBeValidated() {
        val objectKey = Any(); val scopes = mutableMapOf<Any, Boolean>(objectKey to true)
        unavailable { RuntimeEnvelopeHooks.trackFilesystem(scopes, objectKey, "", "unknown") }
        assertFalse(scopes.containsKey(objectKey))
        val root = java.io.File(System.getProperty("java.io.tmpdir"), "private-scope-test").canonicalPath
        RuntimeEnvelopeHooks.trackFilesystem(scopes, objectKey, root, "$root/files")
        assertEquals(true, scopes[objectKey])
        RuntimeEnvelopeHooks.trackFilesystem(scopes, objectKey, root, "$root/../control")
        assertEquals(false, scopes[objectKey])
    }
}
