package app.overfit.ui

import app.overfit.domain.Exercise
import app.overfit.domain.ExerciseSource
import app.overfit.domain.TrackingType
import app.overfit.ui.components.ExerciseImages
import app.overfit.ui.components.ExercisePoses
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Which files belong to an exercise image (list icon + start/end pose of the detail view) and how they are cropped. */
class ExerciseFramesTest {
    private val files = File("src/commonMain/composeResources/files")
    private val wg = File(files, "catalog/wg")
    private val pairs = ExercisePoses.parse(File(files, "catalog/poses.json").readBytes())

    private fun exercise(ref: String?) =
        Exercise("x", "X", ExerciseSource.WORKOUT_GUIDE, ref, emptyList(), emptyList(), null, TrackingType.WEIGHT_REPS, null)

    private fun catalogRefs(): List<String> {
        val json = File(files, "catalog/exercises.json").readText()
        return Regex("\"image\"\\s*:\\s*\"([^\"]+)\"").findAll(json).map { it.groupValues[1] }.toList()
    }

    @Test fun frameFilesAndSlug() {
        assertEquals("catalog/wg/bench-press.svg", ExerciseImages.frameRef("catalog/wg/bench-press.svg", 1))
        assertEquals("catalog/wg/bench-press-3.svg", ExerciseImages.frameRef("catalog/wg/bench-press.svg", 3))
        assertEquals("bench-press", ExerciseImages.slug("catalog/wg/bench-press.svg"))
        assertNull(ExerciseImages.slug("catalog/x.png"))
        assertNull(ExerciseImages.slug(null))
    }

    /** Spot checks of the start/end order decided from the rendered contact sheets (see DECISIONS.md). */
    @Test fun startAndEndPose() {
        fun order(slug: String) = ExerciseImages.poses(exercise("catalog/wg/$slug.svg"), pairs)!!.map { it.ref }
        assertEquals(listOf("catalog/wg/reverse-lunge-3.svg", "catalog/wg/reverse-lunge.svg"), order("reverse-lunge")) // standing | lunge
        assertEquals(listOf("catalog/wg/pull-up-3.svg", "catalog/wg/pull-up.svg"), order("pull-up")) // hang | top
        assertEquals(listOf("catalog/wg/squat.svg", "catalog/wg/squat-3.svg"), order("squat")) // standing | deep
        assertEquals(listOf("catalog/wg/bench-press.svg", "catalog/wg/bench-press-3.svg"), order("bench-press")) // lockout | chest
    }

    @Test fun holdsStretchesAndCustomShowOneImage() {
        for (slug in listOf("plank", "dead-hang", "wall-sit", "hamstring-stretch", "butterfly-stretch")) {
            assertEquals(listOf("catalog/wg/$slug.svg"), ExerciseImages.poses(exercise("catalog/wg/$slug.svg"), pairs)!!.map { it.ref }, slug)
            assertTrue(!File(wg, "$slug-3.svg").exists(), "unused frame $slug-3.svg")
        }
        assertNull(ExerciseImages.poses(exercise(null), pairs))
        assertNull(ExerciseImages.poses(exercise("catalog/wg/squat.svg"), null))
    }

    /** Every entry: frame 1 alone or frames 1 and 3, files bundled, crops the same size and inside the 512 x 512 canvas. */
    @Test fun everyPairIsBundledAndCropsFit() {
        val refs = catalogRefs()
        assertTrue(refs.size > 300, "catalog images: ${refs.size}")
        assertTrue(pairs.count { it.value.frames.size == 2 } > 250, "pairs: ${pairs.size}")
        val slugs = refs.mapNotNull(ExerciseImages::slug).toSet()
        assertEquals(slugs, pairs.keys, "every catalog exercise has exactly one entry")
        for ((slug, p) in pairs) {
            assertTrue(p.frames == listOf(1) || p.frames.toSet() == setOf(1, 3), "$slug frames ${p.frames}")
            assertEquals(p.frames.size, p.origin.size, slug)
            val (w, h) = p.size
            assertTrue(w > 0 && h > 0 && w <= 512f && h <= 512f, "$slug size $w x $h")
            for ((x, y) in p.origin) assertTrue(x >= 0 && y >= 0 && x + w <= 512.01f && y + h <= 512.01f, "$slug crop $x,$y outside")
        }
        for (ref in refs) {
            val poses = ExerciseImages.poses(exercise(ref), pairs)
            for (r in listOf(ref) + poses.orEmpty().map { it.ref }) {
                val f = File(files, r)
                assertTrue(f.isFile && f.readText().startsWith("<svg"), "missing frame $r")
            }
        }
    }

    /** No file without a user: list icon (frame 1) for every exercise, frame 3 only where a pair needs it, no frame 2. */
    @Test fun noUnusedFrames() {
        val needed = catalogRefs().flatMap { ref ->
            listOf(ref) + ExerciseImages.poses(exercise(ref), pairs).orEmpty().map { it.ref }
        }.map { it.removePrefix("catalog/wg/") }.toSet()
        val bundled = wg.list()!!.filter { it.endsWith(".svg") }.toSet()
        assertEquals(emptySet(), bundled - needed, "unused frames")
        assertEquals(emptySet(), needed - bundled, "missing frames")
    }
}
