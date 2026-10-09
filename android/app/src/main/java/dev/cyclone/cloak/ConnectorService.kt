package dev.cyclone.cloak

import dev.cyclone.cloak.cyclone.*
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

/**
 * Cyclone pokes this (no data) when new profile events wait. The sync pulls them, de-duplicates by `seq`, says hello
 * again after a switch, and reconciles bindings and health. In the background it never prompts for root.
 */
class CycloneWake : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        thread(name = "cloak-cyclone-wake") {
            try {
                CycloneBridge.sync(context, foreground = false)
            } finally {
                pending.finish()
            }
        }
    }
}
