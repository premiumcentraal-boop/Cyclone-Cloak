package dev.cyclone.cloak

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the layers one-way: diagnostics is a leaf; forge remains pure and storage may emit diagnostics;
 * `root` on those; `cyclone` on those and `root`; `ui` on anything below it.
 */
class ArchitectureTest {
    private val allowed = mapOf(
        "forge" to emptySet(),
        "diagnostics" to emptySet(),
        "data" to setOf("diagnostics"),
        "root" to setOf("data", "forge", "diagnostics"),
        "cyclone" to setOf("data", "forge", "root", "diagnostics"),
        // playevidence is the generated pure envelope parser, not the diagnostics APK.
        "play" to setOf("data", "forge", "playevidence"),
        "ui" to setOf("data", "forge", "root", "cyclone", "diagnostics", "play"),
    )

    @Test
    fun layersOnlyDependDownwards() {
        val base = File("src/main/java/dev/cyclone/cloak")
        assertTrue("run from the app module", base.isDirectory)
        val problems = allowed.flatMap { (layer, may) ->
            File(base, layer).listFiles { f -> f.name.endsWith(".kt") }!!.flatMap { file ->
                Regex("^import dev\\.cyclone\\.cloak\\.(\\w+)", RegexOption.MULTILINE).findAll(file.readText())
                    .map { it.groupValues[1] }
                    .filter { it != layer && it !in may }
                    .map { "${file.name} ($layer) imports $it" }
                    .toList()
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
