package dev.cyclone.cloak.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Phones Cloak can make identities from: the built-in catalog (the `phones.json` asset, shared with the Python
 * forge) plus the owner's own phones, one JSON file each. Stored as plain JSON; the forge layer gives them meaning.
 */
object PhoneStore {
    private const val CATALOG_ASSET = "phones.json"

    private fun dir(context: Context) = JsonDirectory(File(context.filesDir, "phones"))

    fun catalogText(context: Context): String =
        context.assets.open(CATALOG_ASSET).bufferedReader().use { it.readText() }

    fun userPhones(context: Context): List<JSONObject> = dir(context).all().map { it.second }

    fun save(context: Context, phone: JSONObject) = dir(context).write(phone.getString("id"), phone)

    fun delete(context: Context, id: String): Boolean = dir(context).delete(id)
}
