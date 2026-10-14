package dev.cyclone.cloak.cyclone

enum class PresenceCheck(val label: String) { YES("yes"), NO("no"), UNKNOWN("not verified") }
enum class CompanionState(val label: String) {
    READY("ready"), MISSING("missing"), NOT_LAUNCHABLE("not launchable"),
    STORAGE_UNAVAILABLE("storage unavailable"), NOT_VERIFIED("not verified")
}

data class CompanionFacts(
    val installed: PresenceCheck = PresenceCheck.UNKNOWN,
    val unlocked: PresenceCheck = PresenceCheck.UNKNOWN,
    val launchable: PresenceCheck = PresenceCheck.UNKNOWN,
    val storageExists: PresenceCheck = PresenceCheck.UNKNOWN,
    val storageWritable: PresenceCheck = PresenceCheck.UNKNOWN,
)

data class CompanionPresence(val profileId: String, val label: String, val user: Int?, val facts: CompanionFacts) {
    val state: CompanionState get() = when {
        user == null -> CompanionState.NOT_VERIFIED
        facts.installed == PresenceCheck.NO -> CompanionState.MISSING
        facts.unlocked == PresenceCheck.NO || facts.launchable == PresenceCheck.NO -> CompanionState.NOT_LAUNCHABLE
        facts.storageExists == PresenceCheck.NO || facts.storageWritable == PresenceCheck.NO -> CompanionState.STORAGE_UNAVAILABLE
        facts == CompanionFacts(PresenceCheck.YES, PresenceCheck.YES, PresenceCheck.YES, PresenceCheck.YES, PresenceCheck.YES) -> CompanionState.READY
        else -> CompanionState.NOT_VERIFIED
    }
    val reason: String get() = when {
        user == null -> "Cyclone has not assigned an Android user."
        facts.installed == PresenceCheck.NO -> "Open this profile through Cyclone to provision Cloak, or add it through the profile app selection."
        facts.unlocked == PresenceCheck.NO -> "Start and unlock this profile in Cyclone before opening Cloak. A stopped or locked user can hide its launcher."
        facts.launchable == PresenceCheck.NO -> "Android could not resolve Cloak's launcher. Check whether the profile copy is enabled."
        facts.storageExists == PresenceCheck.NO -> "The private files directory is not accessible. Open Cloak in this unlocked profile and check again."
        facts.storageWritable == PresenceCheck.NO -> "This profile's Cloak cannot write its private files directory."
        state == CompanionState.READY -> "This profile copy can access its own private files directory."
        facts.storageWritable == PresenceCheck.UNKNOWN && facts.storageExists == PresenceCheck.YES -> "Directory presence confirmed. Open Health in this profile's Cloak to verify app-process write access."
        else -> "Check companion copies with root access to inspect other users. Unavailable checks remain unverified."
    }
}

/** Lookup uses Cyclone's explicit Android user assignment, never a profile ID or a fallback to user 0. */
object CompanionPresenceLookup {
    const val PACKAGE = "dev.cyclone.cloak"
    fun inspect(profiles: List<CycloneProfile>, probe: (Int) -> CompanionFacts): List<CompanionPresence> {
        val cache = mutableMapOf<Int, CompanionFacts>()
        return profiles.map { profile ->
            val user = profile.androidUserId?.takeIf { it >= 0 }
            CompanionPresence(profile.id, profile.label, user, user?.let { cache.getOrPut(it) {
                runCatching { probe(it) }.getOrDefault(CompanionFacts())
            } } ?: CompanionFacts())
        }
    }

    /** Parse only a complete, fixed-marker response; command errors must never mean missing or ready. */
    fun parse(output: String): CompanionFacts {
        val lines = output.lineSequence().map(String::trim).toList()
        if (lines.count { it == "CLOAK_PRESENCE_END" } != 1) return CompanionFacts()
        fun value(key: String): PresenceCheck {
            val values = lines.filter { it.startsWith("CLOAK_$key=") }.map { it.substringAfter('=') }
            return when (values.singleOrNull()) { "YES" -> PresenceCheck.YES; "NO" -> PresenceCheck.NO; else -> PresenceCheck.UNKNOWN }
        }
        return CompanionFacts(value("INSTALLED"), value("UNLOCKED"), value("LAUNCHABLE"), value("STORAGE"))
    }
}
