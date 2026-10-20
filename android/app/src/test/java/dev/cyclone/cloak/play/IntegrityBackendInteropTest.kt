package dev.cyclone.cloak.play

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit

/** The only key used is generated in memory by IntegrityFixtures; stdin is never logged. */
class IntegrityBackendInteropTest {
    @Test fun offlineOperationsSelfTestUsesActualAndroidVerdictParser() {
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "tools/integrity_backend.py").isFile }
        val process = ProcessBuilder("uv", "run", "--offline", "--with", "cryptography", "--", "python", "-c",
            "import json; from tools.integrity_backend import synthetic_roundtrip; print(json.dumps(synthetic_roundtrip()))")
            .directory(root).start()
        if (!process.waitFor(30, TimeUnit.SECONDS)) { process.destroyForcibly(); error("Synthetic self-test timed out") }
        assertEquals("Synthetic self-test failed", 0, process.exitValue())
        val artifact = JSONObject(process.inputStream.bufferedReader().readText())
        val pin = artifact.getJSONObject("public_pin")
        val key = java.security.KeyFactory.getInstance("RSA").generatePublic(
            java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(pin.getString("public_key"))))
        val trust = IntegrityTrust { provider, id ->
            if (provider == "synthetic.self-test" && id == "ephemeral") key else null
        }
        val verdict = IntegrityVerdicts.parse(artifact.getJSONObject("envelope").toString(), trust)
        assertEquals("synthetic.self-test", verdict.provider)
        assertEquals("UNEVALUATED", verdict.appRecognition)
        assertEquals(artifact.getString("payload_sha256"), verdict.evidenceDigest)
    }
    @Test fun pythonRs256EnvelopeIsAcceptedByExistingImporter() {
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "tools/integrity_backend.py").isFile }
        val script = """
            import sys,json,base64
            from cryptography.hazmat.primitives.serialization import load_der_private_key
            from tools.integrity_backend import sign_envelope
            data=json.load(sys.stdin)
            key=load_der_private_key(base64.b64decode(data['ephemeral']),None)
            print(json.dumps(sign_envelope(data['payload'],'test-key',key)))
        """.trimIndent()
        val process = ProcessBuilder("uv", "run", "--offline", "--with", "cryptography", "--", "python", "-c", script)
            .directory(root).start()
        val input = JSONObject().put("payload", IntegrityFixtures.payload())
            .put("ephemeral", Base64.getEncoder().encodeToString(IntegrityFixtures.pair.private.encoded))
        process.outputStream.bufferedWriter().use { it.write(input.toString()) }
        if (!process.waitFor(30, TimeUnit.SECONDS)) { process.destroyForcibly(); error("Synthetic backend timed out") }
        assertEquals("Synthetic backend failed", 0, process.exitValue())
        val envelope = process.inputStream.bufferedReader().readText()
        assertTrue(envelope.length < 32768)
        val result = IntegrityVerdicts.evaluate(ReadinessFixtures.context(), IntegrityFixtures.request(), envelope, IntegrityFixtures.trust)
        assertEquals(IntegrityState.PASS, result.state)
    }
}
