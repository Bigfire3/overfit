package app.overfit.ui

import app.overfit.ui.components.ExerciseImages
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Which files belong to an exercise image (still + animation frames) and in which order they play. */
class ExerciseFramesTest {
    private val files = File("src/commonMain/composeResources/files")

    @Test fun framesOfAWorkoutGuideVector() {
        assertEquals(
            listOf("catalog/wg/bench-press.svg", "catalog/wg/bench-press-2.svg", "catalog/wg/bench-press-3.svg"),
            ExerciseImages.frameRefs("catalog/wg/bench-press.svg"),
        )
    }

    @Test fun otherImagesAreSingleFramesOrNone() {
        assertEquals(emptyList(), ExerciseImages.frameRefs(null))
        assertEquals(listOf("catalog/x.png"), ExerciseImages.frameRefs("catalog/x.png"))
    }

    @Test fun sequencePingPongs_andStillImageHasOneStep() {
        assertEquals(listOf(0, 1, 2, 1), ExerciseImages.sequence(3))
        assertEquals(listOf(0, 1), ExerciseImages.sequence(2))
        assertEquals(listOf(0), ExerciseImages.sequence(1))
        assertEquals(listOf(0), ExerciseImages.sequence(0))
    }

    @Test fun everyCatalogImageHasAllBundledFrames() {
        val json = File(files, "catalog/exercises.json").readText()
        val refs = Regex("\"image\"\\s*:\\s*\"([^\"]+)\"").findAll(json).map { it.groupValues[1] }.toList()
        assertTrue(refs.size > 300, "catalog images: ${refs.size}")
        for (ref in refs) for (frame in ExerciseImages.frameRefs(ref)) {
            val f = File(files, frame)
            assertTrue(f.isFile && f.length() > 0, "missing frame $frame")
            assertTrue(f.readText().startsWith("<svg"), "not an svg: $frame")
        }
    }
}
