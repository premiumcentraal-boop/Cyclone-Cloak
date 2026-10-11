package dev.cyclone.cloak.data

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.os.ParcelFileDescriptor

/** A new local Downloads document stays invisible until its complete bytes have been written. */
object LocalDocumentExport {
    fun <T> pending(create: () -> T, write: (T) -> Unit, publish: (T) -> Unit, discard: (T) -> Unit): T {
        val target = create()
        try { write(target); publish(target); return target }
        catch (failure: Exception) {
            try { discard(target) } catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }
    fun write(context: Context, name: String, bytes: ByteArray) {
        val resolver = context.contentResolver
        pending(create = {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/CycloneCloak")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Could not create the local Downloads file.")
        }, write = { uri ->
            val descriptor = resolver.openFileDescriptor(uri, "w") ?: error("Could not write the local Downloads file.")
            ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { stream -> stream.write(bytes); stream.fd.sync() }
        }, publish = { uri ->
            check(resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) == 1) {
                "Could not commit the local Downloads file."
            }
        }, discard = { uri -> resolver.delete(uri, null, null); Unit })
    }
}
