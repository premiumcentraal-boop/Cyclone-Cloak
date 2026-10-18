package dev.cyclone.cloak.forge

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Descriptive local evidence only. No Android services, storage or runtime hooks. */
object DeviceReality {
    val rules: JSONObject get() = JSONObject(RealityRules.json)
    val sections: List<String> get() = rules.getJSONObject("properties").keys().asSequence().filter { it != "inventory_id" }.toList()
    val uniquePaths = listOf("reality.inventory_id", "reality.radio.values.wifi_name", "reality.radio.values.bluetooth_name")
    fun at(profile: JSONObject, path: String): Any? = path.split('.').fold(profile as Any?) { value, key -> (value as? JSONObject)?.opt(key) }.let { if (it == JSONObject.NULL) null else it }
    fun token(seed: String, field: String): String = MessageDigest.getInstance("SHA-256")
        .digest("cloak-reality-v1:$seed:$field".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun userAgent(template: JSONObject, ua: String): String {
        val version = at(template, "webview.values.version") as? String
        return if (at(template, "webview.availability") == "known" && version != null && Regex("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+").matches(version))
            ua.replace(Regex("Chrome/[0-9.]+"), "Chrome/$version") else ua
    }
    fun generate(template: JSONObject, seed: String, ua: String): JSONObject {
        val reality = JSONObject(template.toString()).put("inventory_id", token(seed, "inventory"))
        if (at(reality, "radio.availability") != "unavailable") {
            val values = at(reality, "radio.values")
            if (values == null || values is JSONObject) reality.put("radio", known(JSONObject((values ?: JSONObject()).toString())
                .put("wifi_name", "Cloak-" + token(seed, "wifi").take(12)).put("bluetooth_name", "Cloak-" + token(seed, "bluetooth").take(12)),
                "Local configured names only; no radio state or broadcast is changed."))
        }
        if (at(reality, "webview.availability") != "unavailable") {
            val values = at(reality, "webview.values")
            if (values == null || values is JSONObject) {
                val web = JSONObject((values ?: JSONObject()).toString()).put("user_agent", ua)
                reality.put("webview", known(web, if (!web.has("version") && !web.has("provider"))
                    "User agent derived from this profile; installed provider/version remains unknown."
                    else "Provider/version retained from template; user agent derived from profile."))
            }
        }
        return reality
    }
    private fun known(values: JSONObject, reason: String) = JSONObject().put("availability", "known").put("source", "derived").put("reason", reason).put("values", values)

    private fun canonical(value: Any?): String = when (value) {
        is JSONObject -> value.keys().asSequence().toSet().sorted().joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + canonical(value.opt(it)) }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.opt(it)) }
        is Number -> JSONObject.numberToString(value)
        is String -> JSONObject.quote(value)
        else -> value.toString()
    }
    fun validate(profile: JSONObject): List<ProfileValidator.Finding> {
        if (!profile.has("reality")) return emptyList()
        val findings = mutableListOf<ProfileValidator.Finding>()
        fun add(code: String, path: String) { findings += ProfileValidator.Finding(ProfileValidator.ERROR, code, "$path: ${RealityRules.messages.getString(code)}", path) }
        if (profile.opt("schema_version") != "0.4") add("REALITY_SCHEMA", "reality")
        fun check(value: Any?, rule: JSONObject, path: String) {
            val kind = rule.getString("type")
            val ok = when (kind) {
                "object" -> value is JSONObject; "array" -> value is JSONArray; "string" -> value is String
                "integer" -> value is Int || value is Long
                "number" -> value is Number && value.toDouble().isFinite(); "boolean" -> value is Boolean
                else -> false
            }
            if (!ok) { add("REALITY_TYPE", path); return }
            rule.optJSONArray("enum")?.let { allowed -> if ((0 until allowed.length()).none { allowed.get(it) == value }) add("REALITY_ENUM", path) }
            when (value) {
                is JSONObject -> {
                    val props = rule.getJSONObject("properties")
                    value.keys().asSequence().filter { !props.has(it) }.forEach { add("REALITY_FIELD", path) }
                    rule.optJSONArray("required")?.let { a -> for (i in 0 until a.length()) if (!value.has(a.getString(i))) add("REALITY_REQUIRED", "$path.${a.getString(i)}") }
                    props.keys().asSequence().toSet().sorted().filter(value::has).forEach { check(value.opt(it), props.getJSONObject(it), "$path.$it") }
                }
                is JSONArray -> {
                    val max = rule.getInt("maxItems")
                    if (value.length() > max) add("REALITY_RANGE", path)
                    if (rule.optBoolean("uniqueItems") && (0 until value.length()).map { canonical(value.get(it)) }.distinct().size != value.length()) add("REALITY_DUPLICATE", path)
                    for (i in 0 until minOf(max, value.length())) check(value.opt(i), rule.getJSONObject("items"), "$path[$i]")
                }
                is String -> {
                    if (value.codePointCount(0, value.length) !in rule.optInt("minLength", 0)..rule.optInt("maxLength", 100000)) add("REALITY_RANGE", path)
                    if (rule.has("pattern") && !Regex(rule.getString("pattern")).matches(value)) add("REALITY_FORMAT", path)
                }
                is Number -> if (value.toDouble() < rule.getDouble("minimum") || value.toDouble() > rule.getDouble("maximum")) add("REALITY_RANGE", path)
            }
        }
        check(profile.opt("reality"), rules, "reality")
        if (findings.isNotEmpty()) return findings
        val reality = profile.getJSONObject("reality")
        fun values(section: String) = reality.optJSONObject(section)?.optJSONObject("values") ?: JSONObject()
        fun issue(code: String, section: String) = add(code, "reality.$section")
        for (section in sections) reality.optJSONObject(section)?.let {
            if ((it.getString("availability") == "known") != (it.getJSONObject("values").length() > 0)) issue("REALITY_AVAILABILITY", section)
        }
        RealityRules.links.keys().forEach { link ->
            val section = link.substringBefore('.'); val key = link.substringAfter('.')
            val value = values(section).opt(key)
            if (value != null && canonical(value) != canonical(at(profile, RealityRules.links.getString(link)))) issue("REALITY_LINK", "$section.values.$key")
        }
        val compute = values("compute"); val soc = compute.optString("soc"); val hardware = at(profile, "device.hardware")
        val sdk = (at(profile, "device.sdk_int") as? Number)?.toInt() ?: 0
        if (soc == "Google Tensor G2" && (hardware != "gs201" || sdk < 33)) issue("REALITY_SOC", "compute")
        if (soc == "Snapdragon 8 Gen 2 for Galaxy" && (hardware !in setOf("qcom", "sm8550") || sdk < 33)) issue("REALITY_SOC", "compute")
        if ((soc.startsWith("Google Tensor") && !(at(profile, "device.manufacturer") as? String).equals("Google", ignoreCase = true)) || (soc == "Snapdragon 8 Gen 2 for Galaxy" && !(at(profile, "device.manufacturer") as? String).equals("Samsung", ignoreCase = true))) issue("REALITY_SOC", "compute")
        val gpu = compute.optString("gpu")
        if (gpu.isNotEmpty() && ((soc.startsWith("Google Tensor") && !gpu.startsWith("Mali")) || (soc.startsWith("Snapdragon") && !gpu.startsWith("Adreno")))) issue("REALITY_SOC", "compute")
        val memory = values("memory")
        if (memory.has("storage_gib") && memory.optDouble("free_storage_gib", 0.0) > memory.getDouble("storage_gib")) issue("REALITY_STORAGE", "memory")
        if (at(profile, "device.model") == "Pixel 7" && (memory.optInt("ram_gib", 8) != 8 || memory.optInt("storage_gib", 128) !in setOf(128, 256))) issue("REALITY_STORAGE", "memory")
        val power = values("power")
        if (at(profile, "device.model") == "Pixel 7" && power.optInt("design_capacity_mah", 4355) != 4355) issue("REALITY_POWER", "power")
        if ((power.optString("battery_state") == "full" && power.optInt("battery_percent", 100) != 100) || (power.optString("battery_state") == "charging" && power.optString("charging_source") == "none")) issue("REALITY_POWER", "power")
        val panel = values("panel")
        panel.optJSONArray("modes")?.let { modes -> if ((0 until modes.length()).none { i ->
            val mode = modes.getJSONObject(i); listOf("width", "height", "refresh_hz").all { canonical(mode.opt(it)) == canonical(panel.opt(it)) }
        }) issue("REALITY_MODE", "panel") }
        values("sensors").optJSONArray("inventory")?.let { a -> if ((0 until a.length()).any { i ->
            val s = a.getJSONObject(i); (s.optInt("max_delay_us", 0) > 0 && s.optInt("min_delay_us", 0) > s.getInt("max_delay_us")) || s.optDouble("resolution", 0.0) > s.optDouble("range", 1000000.0)
        }) issue("REALITY_SENSOR", "sensors") }
        val camera = values("cameras")
        camera.optJSONArray("inventory")?.let { a -> if (camera.optInt("count", a.length()) != a.length() || (0 until a.length()).map { a.getJSONObject(it).getString("id") }.distinct().size != a.length()) issue("REALITY_CAMERA", "cameras") }
        val base = at(profile, "camera.cameras") as? JSONArray
        if (base != null && camera.optInt("count", base.length()) != base.length()) issue("REALITY_CAMERA", "cameras")
        val inventory = camera.optJSONArray("inventory")
        if (base != null && inventory != null) {
            val facing = mapOf(0 to "front", 1 to "back", 2 to "external")
            if ((0 until base.length()).map { facing[base.getJSONObject(it).optInt("facing", -1)] ?: "unknown" }.sorted() !=
                (0 until inventory.length()).map { inventory.getJSONObject(it).getString("facing") }.sorted()) issue("REALITY_CAMERA", "cameras")
        }
        val carrier = values("carrier")
        if (carrier.optInt("slots", -1) == 0 && carrier.optString("sim_state") == "ready") issue("REALITY_LINK", "carrier")
        val web = values("webview"); val version = web.optString("version"); val ua = web.optString("user_agent", at(profile, "ua.value") as? String ?: "")
        if (version.isNotEmpty() && !ua.contains("Chrome/$version ")) issue("REALITY_WEBVIEW", "webview")
        values("packages").optJSONArray("installed")?.let { a ->
            if ((0 until a.length()).map { a.getJSONObject(it).getString("package") }.distinct().size != a.length()) issue("REALITY_DUPLICATE", "packages")
            if ((0 until a.length()).any { i -> val p = a.getJSONObject(i); p.getString("package") == "android" && p.getString("version_name") != at(profile, "device.version_release").toString() }) issue("REALITY_LINK", "packages")
            if ((0 until a.length()).any { i -> val p = a.getJSONObject(i); p.getString("package") == web.optString("provider") && version.isNotEmpty() && p.getString("version_name") != version }) issue("REALITY_WEBVIEW", "webview")
        }
        return findings
    }
}
