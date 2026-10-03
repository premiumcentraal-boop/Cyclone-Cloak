package dev.cyclone.cloak

import com.cyclone.connector.client.CycloneConnector

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import kotlin.concurrent.thread

/** Marker for Cyclone discovery. Cyclone never binds this; it reads the manifest. */
class CycloneConnect : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}

/** Cyclone pokes this when new profile events wait. Pull them and keep the cursor. */
class CycloneWake : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        thread {
            try {
                val prefs = context.getSharedPreferences("cloak", Context.MODE_PRIVATE)
                CycloneConnector.connect(context).use { cyclone ->
                    val page = cyclone.events(prefs.getLong("since", 0))
                    prefs.edit().putLong("since", page.optLong("next", 0L)).apply()
                }
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}

