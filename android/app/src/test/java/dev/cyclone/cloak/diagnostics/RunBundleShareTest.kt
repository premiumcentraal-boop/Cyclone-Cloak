package dev.cyclone.cloak.diagnostics

import android.app.Application
import android.content.Intent
import android.net.Uri
import dev.cyclone.cloak.ui.RunBundleShare
import dev.cyclone.cloak.R
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RunBundleShareTest {
    @Test fun shareGrantsReadOnlyAccessToOneCompleteZip() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val file = RunBundleShare.write(context, "complete ZIP bytes".toByteArray())
        val contentUri = Uri.parse("content://${context.packageName}.runbundles/binding-debug-bundles/${file.name}")
        val intent = RunBundleShare.intent(contentUri)
        assertEquals(Intent.ACTION_SEND, intent.action); assertEquals("application/zip", intent.type)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        @Suppress("DEPRECATION") val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
        assertEquals("content", uri.scheme)
        assertEquals("complete ZIP bytes", file.readText())
    }
    @Test fun exportCacheIsBoundedAndPrivateProfileStorageIsNotExposed() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        repeat(8) { RunBundleShare.write(context, byteArrayOf(1), it.toLong()) }
        assertEquals(5, File(context.cacheDir, "run-bundles").listFiles()!!.size)
        val provider = context.packageManager.resolveContentProvider("${context.packageName}.runbundles", 0)!!
        assertFalse(provider.exported); assertTrue(provider.grantUriPermissions)
        val paths = mutableListOf<Pair<String, String>>()
        context.resources.getXml(R.xml.run_bundle_paths).use { xml ->
            while (xml.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && xml.name != "paths")
                    paths += xml.name to xml.getAttributeValue(null, "path")
            }
        }
        assertEquals(listOf("cache-path" to "run-bundles/"), paths)
    }
}
