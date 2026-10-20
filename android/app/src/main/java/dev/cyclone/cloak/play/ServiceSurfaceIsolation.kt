package dev.cyclone.cloak.play

import java.time.Duration

enum class ServiceIsolationState { ISOLATED, SHARED_CONFIRMED, CONFLICT, STALE, UNKNOWN, NEEDS_REVIEW }

/** The existing assignment is authoritative; the package identifies one local binding. */
data class ServiceSurfaceBinding(val assignment: GoogleSurfaceRecord, val packageName: String, val artifactHash: String)
data class ServiceSurfaceEvidence(val binding: ServiceSurfaceBinding, val observation: GoogleSurfaceObservation)
data class ServiceIsolationResult(val state: ServiceIsolationState, val reasons: List<String>) {
    val permitsReady: Boolean get() = state in setOf(ServiceIsolationState.ISOLATED, ServiceIsolationState.SHARED_CONFIRMED)
    val messages: List<String> get() = reasons.map { code -> when (code) {
        "SSI_MATCH" -> "Fresh local evidence matches this app's service assignment."
        "SSI_SHARED_CONFIRMED" -> "Shared slot use is confirmed for the current assignment group."
        "SSI_STALE" -> "Service isolation evidence has expired; collect and review it again."
        "SSI_FUTURE" -> "Service isolation evidence is dated in the future."
        "SSI_BINDING_MISMATCH" -> "Service evidence belongs to a different profile, app, Android user or artifact."
        "SSI_ASSIGNMENT_CHANGED" -> "Service expectations changed; review this app's assignment again."
        "SSI_REVIEW_REQUESTED" -> "This app's service assignment requires explicit review."
        "SSI_STORE_UNREADABLE" -> "Local service isolation records are unreadable; existing records were preserved."
        "SSI_NO_BINDING" -> "No enabled app binding is available for service isolation review."
        "SSI_NO_EVIDENCE" -> "App-scoped service isolation evidence is not recorded."
        else -> IsolationCheck("service", IsolationCheckState.UNKNOWN, code).message
    } + " ($code)" }
}

/** Local configuration/evidence only. Does not enforce Android service isolation. */
object ServiceSurfaceIsolation {
    internal fun validate(binding: ServiceSurfaceBinding) {
        GoogleSurfaceCodec.validate(binding.assignment)
        require(binding.packageName.length <= 200 && GoogleSurfaceCodec.packageName.matches(binding.packageName))
        require(GoogleSurfaceCodec.digest.matches(binding.artifactHash))
    }

    fun evaluate(binding: ServiceSurfaceBinding, context: PlayReadinessContext,
        inventory: List<GoogleSurfaceRecord>, evidence: ServiceSurfaceEvidence? = null,
        sharedConfirmed: Boolean = false, reviewRequested: Boolean = false,
        readable: Boolean = true): ServiceIsolationResult {
        fun result(state: ServiceIsolationState, code: String) = ServiceIsolationResult(state, listOf(code))
        if (!readable) return result(ServiceIsolationState.UNKNOWN, "SSI_STORE_UNREADABLE")
        try { validate(binding); evidence?.let { validate(it.binding) } }
        catch (_: Exception) { return result(ServiceIsolationState.UNKNOWN, "GS_MALFORMED_RECORD") }
        val record = binding.assignment
        if (record.profileId != context.profileId || record.profileHash != context.profileHash ||
            record.androidUserId != context.androidUserId || binding.artifactHash != context.artifactHash)
            return result(ServiceIsolationState.NEEDS_REVIEW, "SSI_BINDING_MISMATCH")
        // Configuration conflicts must remain visible even without sufficient observations.
        val configuration = GoogleSurfaceIsolation.evaluate(record, context.profileId, context.androidUserId,
            context.profileHash, inventory, sharedReviewConfirmed = sharedConfirmed)
        if (configuration.state in setOf(IsolationState.CONFLICT, IsolationState.NEEDS_PACKAGE_REVIEW))
            return ServiceIsolationResult(ServiceIsolationState.CONFLICT, configuration.checks.filter {
                it.state == IsolationCheckState.FAIL }.map { it.reason }.distinct())
        if (configuration.checks.any { it.reason in setOf("GS_MALFORMED_RECORD", "GS_INVENTORY_UNREADABLE") })
            return ServiceIsolationResult(ServiceIsolationState.UNKNOWN, configuration.checks.map { it.reason })
        val allowed = record.account.getJSONArray("allowed_packages")
        if ((0 until allowed.length()).none { allowed.getString(it) == binding.packageName } ||
            record.packages.any { it.packageName == binding.packageName && it.requirement == PackageRequirement.FORBIDDEN })
            return result(ServiceIsolationState.CONFLICT, "GS_PACKAGE_REVIEW")
        if (reviewRequested) return result(ServiceIsolationState.NEEDS_REVIEW, "SSI_REVIEW_REQUESTED")
        if (evidence == null) return result(ServiceIsolationState.UNKNOWN, "SSI_NO_EVIDENCE")
        val original = evidence.binding
        if (original.packageName != binding.packageName || original.artifactHash != context.artifactHash ||
            original.assignment.profileId != record.profileId || original.assignment.androidUserId != record.androidUserId ||
            original.assignment.profileHash != record.profileHash)
            return result(ServiceIsolationState.NEEDS_REVIEW, "SSI_BINDING_MISMATCH")
        if (GoogleSurfaceCodec.encode(original.assignment) != GoogleSurfaceCodec.encode(record))
            return result(ServiceIsolationState.NEEDS_REVIEW, "SSI_ASSIGNMENT_CHANGED")
        val observed = evidence.observation
        if (observed.profileId != record.profileId || observed.androidUserId != record.androidUserId || observed.profileHash != record.profileHash)
            return result(ServiceIsolationState.NEEDS_REVIEW, "SSI_BINDING_MISMATCH")
        if (observed.checkedAt > context.now) return result(ServiceIsolationState.NEEDS_REVIEW, "SSI_FUTURE")
        if (Duration.between(observed.checkedAt, context.now) > context.freshness)
            return result(ServiceIsolationState.STALE, "SSI_STALE")
        val review = GoogleSurfaceIsolation.evaluate(record, context.profileId, context.androidUserId,
            context.profileHash, inventory, observed, sharedConfirmed)
        val state = when (review.state) {
            IsolationState.READY -> ServiceIsolationState.ISOLATED
            IsolationState.SHARED_ACCOUNT_CONFIRMED -> ServiceIsolationState.SHARED_CONFIRMED
            IsolationState.CONFLICT, IsolationState.NEEDS_PACKAGE_REVIEW -> ServiceIsolationState.CONFLICT
            IsolationState.NEEDS_ACCOUNT -> ServiceIsolationState.NEEDS_REVIEW
            IsolationState.UNKNOWN -> ServiceIsolationState.UNKNOWN
        }
        val reasons = if (state == ServiceIsolationState.ISOLATED) listOf("SSI_MATCH")
            else if (state == ServiceIsolationState.SHARED_CONFIRMED) listOf("SSI_SHARED_CONFIRMED")
            else review.checks.filter { it.state != IsolationCheckState.PASS }.map { it.reason }.distinct().sorted()
        return ServiceIsolationResult(state, reasons)
    }

    /** The gate only reduces readiness; isolation never supplies missing Play evidence. */
    fun gate(verdict: String, results: List<ServiceIsolationResult>): String = when {
        verdict == "BLOCKED" -> verdict
        results.any { it.state in setOf(ServiceIsolationState.CONFLICT, ServiceIsolationState.STALE, ServiceIsolationState.NEEDS_REVIEW) } -> "NEEDS REVIEW"
        verdict == "NEEDS REVIEW" -> verdict
        results.isEmpty() || results.any { !it.permitsReady } -> "UNKNOWN"
        else -> verdict
    }
}
