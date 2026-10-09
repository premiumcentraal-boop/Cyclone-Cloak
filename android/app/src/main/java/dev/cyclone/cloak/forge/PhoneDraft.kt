package dev.cyclone.cloak.forge

import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The phone builder's form: every field of a phone as text, so the owner can type freely and the validator says
 * what doesn't fit. [autoFingerprint] composes the build fingerprint from its parts (brand, product, device,
 * release, build id, incremental), which is how a real one is made, so the most common mismatch can't happen.
 */
data class PhoneDraft(
    val id: String,
    val label: String = "",
    val manufacturer: String = "",
    val brand: String = "",
    val model: String = "",
    val product: String = "",
    val device: String = "",
    val hardware: String = "",
    val versionRelease: String = "",
    val sdkInt: String = "",
    val securityPatch: String = "",
    val buildId: String = "",
    val versionIncremental: String = "",
    val buildDateUtc: String = "",
    val firstApiLevel: String = "",
    val bootloader: String = "",
    val baseband: String = "",
    val fingerprint: String = "",
    val autoFingerprint: Boolean = true,
    val carrierName: String = "",
    val mcc: String = "",
    val mnc: String = "",
    val networkType: String = "5G",
    val simSlots: String = "1",
    val egressHint: String = "",
    val width: String = "",
    val height: String = "",
    val density: String = "",
    val refreshRate: String = "",
    val screenClass: String = "large",
    val language: String = "",
    val country: String = "",
    val timezone: String = "",
) {
    /** The fingerprint a real build of these parts carries. */
    val composedFingerprint: String
        get() = "$brand/$product/$device:$versionRelease/$buildId/$versionIncremental:user/release-keys"

    val effectiveFingerprint: String get() = if (autoFingerprint) composedFingerprint else fingerprint

    /** Picking an Android version also sets the SDK level it implies. */
    fun withRelease(release: String): PhoneDraft {
        val sdk = ProfileValidator.RELEASE_TO_SDK[release]
        return copy(versionRelease = release, sdkInt = sdk?.toString() ?: sdkInt)
    }

    fun toTemplate(): PhoneTemplate {
        val sdk = sdkInt.trim().toIntOrNull()
        val deviceJson = JSONObject()
            .put("manufacturer", manufacturer.trim())
            .put("brand", brand.trim())
            .put("model", model.trim())
            .put("product", product.trim())
            .put("device", device.trim())
            .put("hardware", hardware.trim())
            .put("fingerprint", effectiveFingerprint.trim())
            .put("version_release", versionRelease.trim())
            .put("sdk_int", number(sdkInt))
            .put("security_patch", securityPatch.trim())
            .put("build_id", buildId.trim())
            .put("version_incremental", versionIncremental.trim())
            // A build is made on or after its patch date: without a known build date, use the patch day.
            .put("build_date_utc", buildDateUtc.trim().toLongOrNull() ?: patchEpoch() ?: buildDateUtc.trim())
            .put("first_api_level", firstApiLevel.trim().toIntOrNull() ?: sdk ?: firstApiLevel.trim())
            .put("bootloader", bootloader.trim())
            .put("baseband", baseband.trim())
        val telephony = JSONObject()
            .put("sim_slot_count", number(simSlots))
            .put("carrier_name", carrierName.trim())
            .put("mcc", mcc.trim())
            .put("mnc", mnc.trim())
            .put("network_type", networkType.trim())
        val display = JSONObject()
            .put("width", number(width))
            .put("height", number(height))
            .put("density", number(density))
            .put("refresh_rate_hz", number(refreshRate))
            .put("screen_size_class", screenClass.trim())
        val locale = JSONObject()
        language.trim().takeIf { it.isNotEmpty() }?.let { locale.put("language", it) }
        country.trim().takeIf { it.isNotEmpty() }?.let { locale.put("country", it) }
        timezone.trim().takeIf { it.isNotEmpty() }?.let { locale.put("timezone", it) }
        val network = JSONObject()
        egressHint.trim().takeIf { it.isNotEmpty() }?.let { network.put("egress_hint", it) }
        return PhoneTemplate(id, label.trim().ifBlank { model.trim().ifBlank { "New phone" } }, deviceJson, telephony, network, display, locale)
    }

    /** Findings per field path ("device.model"), for showing next to each field. */
    fun findings(): List<ProfileValidator.Finding> = ProfileValidator.validatePhone(toTemplate())

    private fun patchEpoch(): Long? = runCatching {
        LocalDate.parse(securityPatch.trim()).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
    }.getOrNull()

    /** A whole number stays a number; anything else stays text so the validator names the field. */
    private fun number(text: String): Any = text.trim().toIntOrNull() ?: text.trim()

    companion object {
        fun from(phone: PhoneTemplate): PhoneDraft {
            val d = phone.device
            val t = phone.telephony
            val s = phone.display
            val l = phone.locale
            fun JSONObject.text(key: String) = opt(key)?.takeIf { it != JSONObject.NULL }?.toString().orEmpty()
            val draft = PhoneDraft(
                id = phone.id,
                label = phone.label,
                manufacturer = d.text("manufacturer"),
                brand = d.text("brand"),
                model = d.text("model"),
                product = d.text("product"),
                device = d.text("device"),
                hardware = d.text("hardware"),
                versionRelease = d.text("version_release"),
                sdkInt = d.text("sdk_int").takeIf { it != "0" }.orEmpty(),
                securityPatch = d.text("security_patch"),
                buildId = d.text("build_id"),
                versionIncremental = d.text("version_incremental"),
                buildDateUtc = d.text("build_date_utc").takeIf { it != "0" }.orEmpty(),
                firstApiLevel = d.text("first_api_level").takeIf { it != "0" }.orEmpty(),
                bootloader = d.text("bootloader"),
                baseband = d.text("baseband"),
                fingerprint = d.text("fingerprint"),
                carrierName = t.text("carrier_name"),
                mcc = t.text("mcc"),
                mnc = t.text("mnc"),
                networkType = t.text("network_type").ifBlank { "5G" },
                simSlots = t.text("sim_slot_count").ifBlank { "1" },
                egressHint = phone.network.text("egress_hint"),
                width = s.text("width"),
                height = s.text("height"),
                density = s.text("density"),
                refreshRate = s.text("refresh_rate_hz"),
                screenClass = s.text("screen_size_class").ifBlank { "large" },
                language = l.text("language"),
                country = l.text("country"),
                timezone = l.text("timezone"),
            )
            // Keep a real fingerprint as it is; switch to composing only when it already matches its parts.
            return draft.copy(autoFingerprint = draft.fingerprint.isEmpty() || draft.fingerprint == draft.composedFingerprint)
        }

        /** A blank phone with sensible defaults for what most phones share. */
        fun blank(id: String) = PhoneDraft(
            id = id, networkType = "5G", simSlots = "1", screenClass = "large", refreshRate = "60",
            language = "en", country = "US", timezone = "America/New_York",
        )
    }
}
