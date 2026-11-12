package app.overfit.coach

import app.overfit.chat.ChatContext
import app.overfit.data.WorkoutRecord
import app.overfit.domain.Equipment
import app.overfit.domain.Exercise
import app.overfit.domain.ExerciseSource
import app.overfit.domain.Language
import app.overfit.domain.PlannedSet
import app.overfit.domain.Template
import app.overfit.domain.TemplateExercise
import app.overfit.domain.TrackingType
import app.overfit.domain.WeightUnit
import app.overfit.domain.Workout
import app.overfit.i18n.chatStringsFor
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fabian 8.11.: the coach called 0 kg "forgotten" and 70 kg on the machine biceps curl "wrong" and made up its own
 * numbers. The prompt now says how to read weights, and the data marks equipment and bodyweight sets.
 */
class WeightRulesTest {
    private fun exercise(id: String, name: String, equipment: Equipment?) =
        Exercise(id, name, ExerciseSource.WORKOUT_GUIDE, null, emptyList(), emptyList(), equipment, TrackingType.WEIGHT_REPS, 90)

    private val curl = exercise("wg:machine-curl", "Biceps Curl Machine", Equipment.MACHINE)
    private val pushdown = exercise("wg:pushdown", "Triceps Pushdown", Equipment.CABLE)
    private val dips = exercise("wg:dips", "Dips", Equipment.BODYWEIGHT)
    private val bench = exercise("wg:bench", "Bench Press", Equipment.BARBELL)
    private val tpl = Template(
        1, "Arme", null,
        listOf(
            TemplateExercise(curl, 90, null, List(2) { PlannedSet(70.0, 10) }),
            TemplateExercise(pushdown, 90, null, List(1) { PlannedSet(35.0, 12) }),
            TemplateExercise(dips, 90, null, List(2) { PlannedSet(0.0, 12) }),
            TemplateExercise(bench, 90, null, List(1) { PlannedSet(60.0, 8) }),
        ),
    )

    private fun record(): WorkoutRecord {
        var w = Workout.fromTemplate(tpl, 1_000L, id = "w1")
        w.exercises.forEach { e -> e.sets.forEach { w = w.completeSet(it.id, 2_000L) } }
        return WorkoutRecord(w, endedAt = 3_600_000L, feeling = null, updatedAt = 0)
    }

    private val sources get() = AttachmentSources(listOf(record()), listOf(tpl), WeightUnit.KG, Language.DE, 10_000_000L, TimeZone.UTC)

    @Test
    fun everyCoachPromptCarriesTheWeightRules() {
        val chat = CoachChatPrompt.build(ChatPromptParts("ROLE", "PROFILE", "", emptyList()), emptyList(), "Wie lief es?")
        assertTrue(chat.staticSystem.contains(WeightRules.TEXT), chat.staticSystem)
        listOf("never converted", "Never call a logged weight wrong, forgotten", "0 kg (\"BW\" in the data) is a valid entry",
            "only relative to earlier sets of the same exercise", "steps the user already uses").forEach {
            assertTrue(WeightRules.TEXT.contains(it), it)
        }
        // Default instructions (German and English) say the same in short.
        assertTrue(app.overfit.i18n.coachStringsFor(Language.DE).defaultInstructions.contains("0 kg ist gültig"))
        assertTrue(app.overfit.i18n.coachStringsFor(Language.EN).defaultInstructions.contains("0 kg is valid"))
    }

    @Test
    fun trainingDataMarksMachinesCablesAndBodyweight() {
        val text = ChatAttachmentText.build(listOf(ChatAttachment.Training("w1", "Arme")), sources, chatStringsFor(Language.DE))
        assertTrue(text.contains("Biceps Curl Machine (machine): 70×10, 70×10"), text)
        assertTrue(text.contains("Triceps Pushdown (cable): 35×12"), text)
        assertTrue(text.contains("Dips (bodyweight): BW×12, BW×12"), text)
        assertTrue(text.contains("Bench Press (free weight): 60×8"), text)
        assertFalse(text.contains("0×12"), text)
    }

    @Test
    fun planAndExerciseAttachmentsAreMarkedToo() {
        val plan = ChatAttachmentText.build(listOf(ChatAttachment.Plan(1, "Arme")), sources, chatStringsFor(Language.DE))
        assertTrue(plan.contains("- Biceps Curl Machine (machine) [wg:machine-curl]: 2 sets (70×10, 70×10)"), plan)
        assertTrue(plan.contains("- Dips (bodyweight) [wg:dips]: 2 sets (BW×12, BW×12)"), plan)
        val ex = ChatAttachmentText.build(listOf(ChatAttachment.Exercise("wg:machine-curl", "Curl")), sources, chatStringsFor(Language.DE))
        assertTrue(ex.startsWith("## Attached exercise: Biceps Curl Machine (machine) [wg:machine-curl]"), ex)
    }

    @Test
    fun copiedContextExplainsTheWeights() {
        val input = app.overfit.chat.ContextInput(
            app.overfit.domain.Profile("Fabi"), emptyList(), null, listOf(record()), Language.EN,
            kotlinx.datetime.LocalDate(2026, 11, 8), TimeZone.UTC,
        )
        val text = ChatContext.status(input, chatStringsFor(Language.EN), null)
        assertTrue(text.contains("Weights are exactly what I set on the machine"), text)
        assertTrue(text.contains("Dips (bodyweight): BW×12"), text)
    }

    @Test
    fun reviewPromptMarksBodyweightAndEquipment() {
        assertEquals("BW×12", CoachPromptBuilder.formatSet(app.overfit.domain.SetValues(0.0, 12, null), TrackingType.WEIGHT_REPS))
        assertEquals("70kg×10", CoachPromptBuilder.formatSet(app.overfit.domain.SetValues(70.0, 10, null), TrackingType.WEIGHT_REPS))
        assertEquals("machine", WeightRules.tag(Equipment.MACHINE))
        assertEquals(null, WeightRules.tag(null))
    }

    @Test
    fun imageTokensFollowTheDownscaledSize() {
        assertEquals(640, ChatTokens.image(800, 600))
        // 1125 × 1500 would be 2250 tokens; Anthropic scales it down to about 1600.
        assertEquals(ChatTokens.MAX_IMAGE_TOKENS, ChatTokens.image())
    }
}
