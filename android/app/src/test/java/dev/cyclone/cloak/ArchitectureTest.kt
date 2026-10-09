package dev.cyclone.cloak

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the layers one-way: `forge` (pure identity rules) and `data` (storage) depend on nothing of Cloak's;
 * `root` on those; `cyclone` on those and `root`; `ui` on anything below it.
 */
class ArchitectureTest {
    private val allowed = mapOf(
        "forge" to emptySet(),
        "data" to emptySet(),
        "root" to setOf("data", "forge"),
        "cyclone" to setOf("data", "forge", "root"),
        "ui" to setOf("data", "forge", "root", "cyclone"),
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
