package dev.cyclone.cloak.playevidence

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.StandardIntegrityException
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit

object IntegrityEvidence {
    internal fun receipt(token: String, requestHash: String, at: Instant): EvidenceRow {
        require(token.isNotEmpty())
        return EvidenceRow("integrity.api", EvidenceStatus.OBSERVED, "TOKEN_RECEIVED", JSONObject()
            .put("response_digest", EvidenceReport.digest(token.toByteArray(Charsets.UTF_8)))
            .put("received_at", at.toString()).put("request_hash", requestHash).put("provider_id", "public.play.integrity"))
    }
    /** Call only after the dedicated user confirmation; never from launch/background collection. */
    fun request(context: Context, cloudProject: Long, binding: EvidenceBinding, retain: ((String, Instant) -> Unit)? = null): EvidenceRow {
        require(cloudProject > 0) { "INVALID_PROJECT_NUMBER" }
        val started = System.nanoTime()
        val hash = EvidenceReport.digest("${binding.requestId}\n${binding.profileId}\n${binding.profileHash}\n${binding.androidUserId}\n${binding.artifactHash}".toByteArray())
        return try {
            val manager = IntegrityManagerFactory.createStandard(context)
            val provider = Tasks.await(manager.prepareIntegrityToken(StandardIntegrityManager.PrepareIntegrityTokenRequest.builder()
                .setCloudProjectNumber(cloudProject).build()), 90, TimeUnit.SECONDS)
            val response = Tasks.await(provider.request(StandardIntegrityManager.StandardIntegrityTokenRequest.builder()
                .setRequestHash(hash).build()), 90, TimeUnit.SECONDS)
            // Ciphertext may leave this method only through explicitly opted-in retention.
            val at = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
            retain?.invoke(response.token(), at)
            receipt(response.token(), hash, at)
                .copy(durationMs = ((System.nanoTime() - started) / 1000000).coerceIn(0, 300000))
        } catch (error: Exception) {
            val api = (error as? StandardIntegrityException) ?: (error.cause as? StandardIntegrityException)
            EvidenceRow("integrity.api", EvidenceStatus.BLOCKED, "INTEGRITY_API_ERROR",
                JSONObject().also { if (api != null) it.put("api_code", api.errorCode) },
                ((System.nanoTime() - started) / 1000000).coerceIn(0, 300000))
        }
    }
}
