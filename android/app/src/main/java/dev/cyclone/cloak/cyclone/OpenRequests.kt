package dev.cyclone.cloak.cyclone

import com.cyclone.connector.client.CycloneConnectorException

/** `profiles.open.request.v1` (handoff §5): a question the owner answers on Cyclone's own screen. No retry loop. */
object OpenRequests {
    const val APPROVE_LINE = "Approve Cyclone Cloak in Cyclone → Settings → Connectors in this profile"

    data class Outcome(val message: String, val refreshProfiles: Boolean = false)

    /** Profiles Cloak may ask Cyclone to open from here: ready, not the one in front. Main is offered from a profile. */
    fun targets(snapshot: ProfilesSnapshot, placement: Placement): List<CycloneProfile> = snapshot.profiles.filter { p ->
        p.state == "ready" && p.id != snapshot.current &&
            when (placement) {
                is Placement.Main -> p.kind == "profile" && CycloneProfile.PROFILE_ID.matches(p.id)
                is Placement.InProfile -> p.id != placement.profile.id &&
                    (p.isOwner || (p.kind == "profile" && CycloneProfile.PROFILE_ID.matches(p.id)))
            }
    }

    fun ask(api: CycloneApi, profile: CycloneProfile): Outcome = try {
        val answer = api.requestOpenProfile(profile.id)
        if (answer.optBoolean("requested", false)) {
            Outcome("Cyclone is asking you to open ${profile.label}. Answer on Cyclone's screen.")
        } else {
            Outcome("Cyclone didn't show the question. Try again in a moment.")
        }
    } catch (error: CycloneConnectorException) {
        outcomeFor(error.code, profile.label)
    }

    fun outcomeFor(code: String, label: String): Outcome = when (code) {
        "NO_SUCH_PROFILE" -> Outcome("$label isn't ready in Cyclone. The list is refreshed.", refreshProfiles = true)
        "ALREADY_OPEN" -> Outcome("$label is already open.")
        "BUSY" -> Outcome("Cyclone is busy; try again in a moment.")
        "RATE_LIMITED" -> Outcome("Wait a few seconds before asking again.")
        "NOT_APPROVED" -> Outcome(APPROVE_LINE)
        "SCOPE_NOT_GRANTED" -> Outcome("Allow \"Ask you to open a profile\" for Cyclone Cloak in Cyclone → Settings → Connectors.")
        "UNKNOWN_METHOD" -> Outcome("Update Cyclone to 5.0.0-alpha.122 or newer to open profiles from Cloak.")
        else -> Outcome("Cyclone couldn't answer; try again later.")
    }
}
