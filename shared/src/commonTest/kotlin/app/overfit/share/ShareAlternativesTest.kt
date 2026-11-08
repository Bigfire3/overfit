package app.overfit.share

import app.overfit.domain.Exercise
import app.overfit.domain.ExerciseSource
import app.overfit.domain.PlannedSet
import app.overfit.domain.Template
import app.overfit.domain.TemplateExercise
import app.overfit.domain.TrackingType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Alternatives (A/B) in the share format: key `alt`, one level deep. */
class ShareAlternativesTest {
    private fun ex(id: String, name: String, custom: Boolean = false, tracking: TrackingType = TrackingType.WEIGHT_REPS) = Exercise(
        id, name, if (custom) ExerciseSource.CUSTOM else ExerciseSource.WORKOUT_GUIDE, null, emptyList(), emptyList(), null, tracking, null,
    )

    private val bench = ex("wg:bench-press", "Bench Press")
    private val incline = ex("wg:incline-press", "Incline Press")
    private val own = ex("custom:7", "Landmine Press", custom = true)
    private val squat = ex("wg:squat", "Squat")
    private val catalog = listOf(bench, incline, squat, own)

    private val template = Template(
        0, "AB", null,
        listOf(
            TemplateExercise(
                bench, 120, "A", listOf(PlannedSet(60.0, 8)),
                alternatives = listOf(
                    TemplateExercise(incline, 90, "B", listOf(PlannedSet(50.0, 10), PlannedSet(50.0, 9))),
                    TemplateExercise(own, null, null, listOf(PlannedSet(20.0, 12))),
                ),
            ),
            TemplateExercise(squat, null, null, listOf(PlannedSet(80.0, 5))),
        ),
    )

    @Test
    fun jsonRoundTripWithAlt() {
        val shared = ShareMapper.fromTemplate(template)
        assertEquals(2, shared.exercises[0].alternatives.size)
        // The own exercise of the alternative is shared as custom with a ref.
        assertEquals(listOf("Landmine Press"), shared.custom.map { it.name })
        val json = ShareFormat.toJson(shared)
        assertTrue(json.contains("\"alt\":["), json)
        assertEquals(ShareParse.Ok(shared), ShareFormat.fromJson(json))
        assertEquals(ShareParse.Ok(shared), ShareFormat.parseInput(ShareFormat.toLink(shared)))
        // Without alternatives there is no `alt` key at all (smaller QR, older apps unaffected).
        assertFalse(ShareFormat.toJson(shared.copy(exercises = shared.exercises.drop(1))).contains("\"alt\""))
    }

    @Test
    fun jsonWithoutAltStillParses() {
        val parsed = assertIs<ShareParse.Ok>(
            ShareFormat.fromJson("""{"v":1,"name":"Alt","exercises":[{"id":"wg:squat","sets":[{"kg":80,"reps":5}]}]}"""),
        ).training
        assertTrue(parsed.exercises.single().alternatives.isEmpty())
        val preview = ShareMapper.resolve(parsed, catalog)
        assertTrue(preview.exercises.single().alternatives.isEmpty())
    }

    @Test
    fun altIsOnlyOneLevelDeepAndLimited() {
        val alts = (1..8).joinToString(",") { """{"name":"Alt $it","alt":[{"name":"Deep"}]}""" }
        val parsed = assertIs<ShareParse.Ok>(
            ShareFormat.fromJson("""{"v":1,"name":"X","exercises":[{"id":"wg:squat","alt":[$alts]}]}"""),
        ).training
        val a = parsed.exercises.single().alternatives
        assertEquals(ShareFormat.MAX_ALTERNATIVES, a.size)
        assertTrue(a.all { it.alternatives.isEmpty() })
    }

    @Test
    fun resolveAndImportKeepAlternatives() {
        val parsed = assertIs<ShareParse.Ok>(ShareFormat.fromJson(ShareFormat.toJson(ShareMapper.fromTemplate(template)))).training
        val preview = ShareMapper.resolve(parsed, catalog)
        assertTrue(preview.newExerciseNames.isEmpty())
        assertEquals(listOf("wg:incline-press", "custom:7"), preview.exercises[0].alternatives.map { it.exercise?.id })
        val back = ShareMapper.toTemplate(preview, emptyMap())
        assertEquals(template.exercises.map { it.exercise.id }, back.exercises.map { it.exercise.id })
        assertEquals(template.exercises[0].alternatives.map { it.exercise.id }, back.exercises[0].alternatives.map { it.exercise.id })
        assertEquals(template.exercises[0].alternatives.map { it.sets }, back.exercises[0].alternatives.map { it.sets })
        assertEquals(listOf(90, null), back.exercises[0].alternatives.map { it.restSec })
        assertEquals("B", back.exercises[0].alternatives[0].note)
    }

    @Test
    fun newCustomExerciseAsAlternative_isPending() {
        val parsed = assertIs<ShareParse.Ok>(ShareFormat.fromJson(ShareFormat.toJson(ShareMapper.fromTemplate(template)))).training
        // The receiver does not have the own exercise: it becomes a new one.
        val preview = ShareMapper.resolve(parsed, catalog - own)
        val alt = preview.exercises[0].alternatives[1]
        assertNull(alt.exercise)
        assertEquals("Landmine Press", alt.newExercise?.name)
        assertEquals(listOf("Landmine Press"), preview.newExerciseNames)
        val pending = ShareMapper.pendingInputs(preview)
        assertEquals(setOf(ShareMapper.PENDING_PREFIX + "landmine press"), pending.keys)
        val draft = ShareMapper.toDraftTemplate(preview)
        assertEquals(ShareMapper.PENDING_PREFIX + "landmine press", draft.exercises[0].alternatives[1].exercise.id)
        val created = ShareMapper.toTemplate(preview, mapOf("landmine press" to "custom:new"))
        assertEquals("custom:new", created.exercises[0].alternatives[1].exercise.id)
    }
}
