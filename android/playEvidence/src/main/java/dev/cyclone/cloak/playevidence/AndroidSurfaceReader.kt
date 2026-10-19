package dev.cyclone.cloak.playevidence

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.webkit.WebView
import com.google.android.gms.common.GoogleApiAvailabilityLight
import org.json.JSONObject

class AndroidSurfaceReader(private val context: Context) : SurfaceReader {
    private val pm get() = context.packageManager
    @Suppress("DEPRECATION")
    override fun packageInfo(name: String): JSONObject? = try { metadata(pm.getPackageInfo(name, 0)) }
        catch (_: PackageManager.NameNotFoundException) { null }
    @Suppress("DEPRECATION")
    private fun metadata(info: PackageInfo): JSONObject {
        val m = JSONObject().put("package_name", info.packageName).put("version_name", info.versionName ?: "")
            .put("version_code", info.longVersionCode).put("enabled", info.applicationInfo?.enabled ?: JSONObject.NULL)
        try {
            val installer = if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(info.packageName).installingPackageName
                else pm.getInstallerPackageName(info.packageName)
            m.put("installer", installer ?: JSONObject.NULL).put("installer_reason", "PACKAGE_OBSERVED")
        } catch (_: SecurityException) { m.put("installer", JSONObject.NULL).put("installer_reason", "PERMISSION_DENIED") }
        catch (_: PackageManager.NameNotFoundException) { m.put("installer", JSONObject.NULL).put("installer_reason", "NOT_VISIBLE_OR_MISSING") }
        return m
    }
    override fun playServicesCode() = GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(context)
    override fun webProvider(): JSONObject? = WebView.getCurrentWebViewPackage()?.let(::metadata)
    @Suppress("DEPRECATION")
    override fun settingsResolvable() = pm.resolveActivity(Intent("com.google.android.gms.settings.GOOGLE_SETTINGS").setPackage("com.google.android.gms"), 0) != null
    // Inspect presence only. Never read, stringify, persist or log an account name.
    override fun visibleGoogleAccount() = AccountManager.get(context).getAccountsByType("com.google").isNotEmpty()
    override fun androidUser() = Process.myUid() / 100000
}
