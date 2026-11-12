package app.overfit.coach

import app.overfit.data.WorkoutRecord
import app.overfit.domain.Equipment
import app.overfit.domain.Exercise
import app.overfit.domain.ExerciseOrigin
import app.overfit.domain.ExerciseSource
import app.overfit.domain.Language
import app.overfit.domain.PlannedSet
import app.overfit.domain.Template
import app.overfit.domain.TemplateExercise
import app.overfit.domain.TrackingType
import app.overfit.domain.WeightEntry
import app.overfit.domain.WeightUnit
import app.overfit.domain.Workout
import app.overfit.i18n.chatStringsFor
import app.overfit.share.ShareMapper
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** "Apply" of a coach proposal on the running workout (Fabian 8.11.: "die KI dann wirklich was ändern kann am Workout"). */
class RunningMergeTest {
    private fun ex(id: String, name: String, equipment: Equipment? = null) =
        Exercise(id, name, ExerciseSource.WORKOUT_GUIDE, null, emptyList(), emptyList(), equipment, TrackingType.WEIGHT_REPS, 90)

    private val bench = ex("wg:bench", "Bench Press", Equipment.BARBELL)
    private val curl = ex("wg:curl", "Biceps Curl Machine", Equipment.MACHINE)
    private val dips = ex("wg:dips", "Dips", Equipment.BODYWEIGHT)
    private val fly = ex("wg:fly", "Cable Fly", Equipment.CABLE)

    private fun running(): Workout {
        val tpl = Template(
            1, "Push", null,
            listOf(
                TemplateExercise(bench, 120, null, List(3) { PlannedSet(60.0, 8) }),
                TemplateExercise(curl, 90, null, List(2) { PlannedSet(70.0, 10) }),
                TemplateExercise(dips, 90, null, List(2) { PlannedSet(0.0, 12) }),
            ),
        )
        var w = Workout.fromTemplate(tpl, 1_000L, id = "run")
        // Bench: first set done; curl: nothing done; dips: one set done.
        w = w.completeSet(w.exercises[0].sets[0].id, 2_000L)
        w = w.completeSet(w.exercises[2].sets[0].id, 3_000L)
        return w
    }

    private fun proposal(vararg ex: TemplateExercise) = Template(0, "Push", null, ex.toList())

    @Test
    fun doneSetsStayOpenSetsTakeTheProposal() {
        val before = running()
        val p = proposal(
            TemplateExercise(bench, 120, null, List(4) { PlannedSet(62.5, 6) }),
            TemplateExercise(curl, 90, null, List(3) { PlannedSet(75.0, 8) }),
            TemplateExercise(dips, 90, null, List(2) { PlannedSet(0.0, 12) }),
        )
        val after = RunningMerge.apply(before, p).workout
        val benchAfter = after.exercises.first { it.exercise.id == "wg:bench" }
        assertEquals(4, benchAfter.sets.size)
        // The done set keeps its logged values and its id.
        assertEquals(before.exercises[0].sets[0], benchAfter.sets[0])
        assertTrue(benchAfter.sets.drop(1).all { !it.completed && it.target == PlannedSet(62.5, 6) && it.actual == it.target })
        // Open set ids are reused (ticks and rest timers keep pointing at real sets).
        assertEquals(before.exercises[0].sets[1].id, benchAfter.sets[1].id)
        val curlAfter = after.exercises.first { it.exercise.id == "wg:curl" }
        assertEquals(List(3) { PlannedSet(75.0, 8) }, curlAfter.sets.map { it.target })
        assertEquals(before.exercises[0].id, benchAfter.id)
    }

    @Test
    fun addsNewExercisesAndRemovesOnlyUntouchedOnes() {
        val before = running()
        // Proposal drops bench (one set done) and curl (nothing done), keeps dips, adds a cable fly.
        val p = proposal(
            TemplateExercise(dips, 90, null, List(3) { PlannedSet(0.0, 10) }),
            TemplateExercise(fly, 60, "Langsam", List(3) { PlannedSet(15.0, 12) }),
        )
        val after = RunningMerge.apply(before, p).workout
        assertEquals(listOf("wg:bench", "wg:dips", "wg:fly"), after.exercises.map { it.exercise.id })
        // Bench stays with its done set only; curl is gone.
        assertEquals(1, after.exercises[0].sets.size)
        assertTrue(after.exercises[0].sets.single().completed)
        val added = after.exercises[2]
        assertEquals(ExerciseOrigin.ADDED, added.origin)
        assertEquals("Langsam", added.note)
        assertEquals(3, added.sets.size)
        // Dips: one done + two open = the proposed three.
        assertEquals(3, after.exercises[1].sets.size)
        assertEquals(1, after.exercises[1].completedSets)
    }

    @Test
    fun unknownExercisesAreLeftOutAndReported() {
        val unknown = ex(ShareMapper.PENDING_PREFIX + "zirkus", "Zirkus-Übung")
        val p = proposal(TemplateExercise(bench, 120, null, List(3) { PlannedSet(60.0, 8) }), TemplateExercise(unknown, null, null, listOf(PlannedSet(reps = 5))))
        val r = RunningMerge.apply(running(), p)
        assertEquals(listOf("Zirkus-Übung"), r.unknown)
        assertTrue(r.workout.exercises.none { it.exercise.id.startsWith(ShareMapper.PENDING_PREFIX) })
    }

    @Test
    fun asTemplateUsesLoggedValuesForDoneSets() {
        var w = running()
        w = w.setValues(w.exercises[0].sets[0].id, PlannedSet(65.0, 8))
        val t = RunningMerge.asTemplate(w)
        assertEquals(PlannedSet(65.0, 8), t.exercises[0].sets[0])
        assertEquals(PlannedSet(60.0, 8), t.exercises[0].sets[1])
    }

    @Test
    fun runningAndDayAttachmentsAsText() {
        val w = running()
        val finished = run {
            var f = Workout.fromTemplate(Template(2, "Legs", null, listOf(TemplateExercise(curl, 90, null, List(1) { PlannedSet(40.0, 10) }))), 86_400_000L, id = "legs")
            f = f.completeSet(f.exercises[0].sets[0].id, 86_400_000L + 1_000)
            WorkoutRecord(f, endedAt = 86_400_000L + 3_600_000, feeling = null, updatedAt = 0)
        }
        val src = AttachmentSources(
            listOf(finished), emptyList(), WeightUnit.KG, Language.DE, 100_000_000L, TimeZone.UTC,
            weights = listOf(WeightEntry("1970-01-02", 82.4)), running = w,
        )
        val t = chatStringsFor(Language.DE)
        val text = ChatAttachmentText.build(listOf(ChatAttachment.Running("run", "Push · läuft")), src, t)
        assertTrue(text.startsWith("## Running workout (in progress right now, not finished): Push"), text)
        assertTrue(text.contains("- Bench Press (free weight) [wg:bench]: done 60×8; open (planned) 60×8, 60×8"), text)
        assertTrue(text.contains("- Biceps Curl Machine (machine) [wg:curl]: done none; open (planned) 70×10, 70×10"), text)
        assertTrue(text.contains("- Dips (bodyweight) [wg:dips]: done BW×12; open (planned) BW×12"), text)
        // Gone (finished elsewhere): says so instead of failing.
        val gone = ChatAttachmentText.build(listOf(ChatAttachment.Running("other", "Pull · läuft")), src, t)
        assertTrue(gone.contains("no longer there"), gone)

        val day = ChatAttachmentText.build(listOf(ChatAttachment.Day("1970-01-02", "Tag · 02.01.")), src, t)
        assertTrue(day.startsWith("## Attached day: 1970-01-02"), day)
        assertTrue(day.contains("Body weight: 82,4 kg"), day)
        assertTrue(day.contains("Legs"), day)
        assertTrue(day.contains("Biceps Curl Machine (machine): 40×10"), day)
        val empty = ChatAttachmentText.build(listOf(ChatAttachment.Day("1970-01-05")), src, t)
        assertTrue(empty.contains("No training on this day."), empty)
    }

    @Test
    fun newAttachmentsSurviveJson() {
        val list = listOf(ChatAttachment.Running("run", "Push · läuft"), ChatAttachment.Day("2026-11-08", "Tag · 08.11."))
        assertEquals(list, ChatAttachment.decode(ChatAttachment.encode(list)))
    }

    @Test
    fun chatRulesAskForTheCompleteChangedWorkout() {
        assertTrue(CoachChatSchema.RULES.contains("put the COMPLETE changed workout"))
    }
}
