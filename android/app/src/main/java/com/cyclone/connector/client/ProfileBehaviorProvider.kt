package com.cyclone.connector.client

import android.content.Context
import android.os.Binder
import com.cyclone.connector.IProfileBehaviorProvider
import com.cyclone.connector.IProfileBehaviorResult
import dev.cyclone.cloak.CloakStateLayout
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Runs on a Binder worker; answer promptly (Cyclone's deadline is 250 ms). */
abstract class ProfileBehaviorProvider(protected val context: Context) : IProfileBehaviorProvider.Stub() {
    abstract fun beforeLaunch(event: JSONObject): JSONObject?

    final override fun beforeLaunch(event: String?, result: IProfileBehaviorResult?) {
        val uid = Binder.getCallingUid()
        if (context.packageManager.getPackagesForUid(uid)?.toList() != listOf(CycloneConnector.CYCLONE_PACKAGE)) return
        val answer = runCatching {
            val body = JSONObject(event ?: "")
            require(body.opt("version") == 1 && body.optString("contract") == "cyclone.profile-startup/1")
            require(android.os.SystemClock.elapsedRealtime() < body.getLong("deadlineElapsedRealtimeMs"))
            beforeLaunch(body) ?: JSONObject().put("version", 1).put("configRef", JSONObject.NULL).put("state", "ready")
        }.getOrElse { JSONObject().put("version", 1).put("state", "failed").put("configRef", JSONObject.NULL) }
        runCatching { result?.complete(answer.toString()) }
    }

    companion object {
        /** Connector-owned private storage. Stable across updates; removed by uninstall/clear data. */
        fun stateDirectory(context: Context, profileId: String, androidUserId: Int, packageName: String): File {
            CloakStateLayout.validateInputs(profileId, androidUserId, packageName)
            val key = CloakStateLayout.key(profileId, androidUserId, packageName)
            return File(context.noBackupFilesDir, "${CloakStateLayout.ROOT_DIR}/$key").also { check(it.isDirectory || it.mkdirs()) }
        }
    }
}
