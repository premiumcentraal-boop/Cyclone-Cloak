package dev.cyclone.cloak.cyclone

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RegistryCacheStoreTest {
    @Test fun cacheSurvivesNewStoreAndDoesNotChangeExistingPreferences() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("cloak", 0)
        prefs.edit().clear().putLong("since", 7).commit()
        val cache = CachedRegistry(ProfilesSnapshot(null, listOf(CycloneProfile("owner", "Main", "owner", "ready", 0, null))), System.currentTimeMillis())
        RegistryCache(app).save(cache)
        assertEquals(cache, RegistryCache(app).read())
        assertEquals(7L, prefs.getLong("since", 0))
    }

    @Test fun brokenPreferencesAreHiddenWithoutRemovingExistingRecords() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("cloak", 0)
        prefs.edit().clear().putString("registrySnapshot", "corrupt").putLong("registrySeenAt", 10).putString("cyclonePushed", "[]").commit()
        assertNull(RegistryCache(app).read())
        assertEquals("[]", prefs.getString("cyclonePushed", null))
    }
}
