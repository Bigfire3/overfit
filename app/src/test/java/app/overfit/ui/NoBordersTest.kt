package app.overfit.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Design rule "no frames": UI code must not draw borders, outlines or shadows (only fills separate surfaces). */
class NoBordersTest {
    // SaunaRing is a symbol (a ring glyph), not a frame.
    private val allowed = setOf("photos/PhotoScreens.kt")

    @Test
    fun uiCodeHasNoBordersOrShadows() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "shared/src/commonMain/kotlin/app/overfit/ui") }.first { it.isDirectory }
        val pattern = Regex("""\.border\(|BorderStroke|\.shadow\(|\.dropShadow\(|\bOutlined""")
        val hits = root.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            val rel = f.relativeTo(root).path
            f.readLines().mapIndexedNotNull { i, line ->
                val code = line.substringBefore("//")
                if (pattern.containsMatchIn(code) && !(rel in allowed && "SAUNA_MARKER_TAG" in code)) "$rel:${i + 1}: ${line.trim()}" else null
            }
        }.toList()
        assertTrue("Frames/shadows are not allowed:\n" + hits.joinToString("\n"), hits.isEmpty())
    }
}
