package app.overfit.coach

import app.overfit.domain.ExerciseOrigin
import app.overfit.domain.Language
import app.overfit.domain.SetValues
import app.overfit.domain.TrackingType
import app.overfit.domain.WorkoutExercise
import app.overfit.domain.WorkoutSet
import app.overfit.workout.entry
import app.overfit.workout.exercise
import app.overfit.workout.planned
import app.overfit.workout.templateOf
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class CoachPromptBuilderTest {
    private val tz = TimeZone.UTC
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilliseconds()

    private val bench = exercise("wg:bench", name = "Bench Press")
    private val rows = exercise("wg:row", TrackingType.REPS, name = "Row")
    private val dips = exercise("wg:dips", name = "Dips")

    private fun set(id: String, target: SetValues, actual: SetValues, at: Long?) =
        WorkoutSet(id, target, actual, completed = at != null, completedAt = at)

    private fun session(id: String, start: String, vararg ex: WorkoutExercise, note: String? = null, feeling: Int? = null) =
        SessionRecord(id, "Push", 7, ms(start), ms(start) + 60 * 60_000, note, feeling, null, ex.toList())

    private val t0 = ms("2026-10-15T10:00:00Z")
    private val current = session(
        "s1", "2026-10-15T10:00:00Z",
        WorkoutExercise(
            "we1", bench, 120, "felt heavy", ExerciseOrigin.PLANNED, null,
            listOf(
                set("a", planned(80.0, 8), planned(80.0, 8), t0 + 60_000),
                set("b", planned(80.0, 8), planned(80.0, 7), t0 + 60_000 + 120_000),
                set("c", planned(80.0, 8), planned(), null),
            ),
        ),
        WorkoutExercise(
            "we2", rows, null, "", ExerciseOrigin.REPLACED, "wg:old-row",
            listOf(set("d", planned(reps = 12), planned(reps = 12), t0 + 400_000)),
        ),
        note = "Slept badly", feeling = 3,
    )

    private val template = templateOf(entry(bench, 120, planned(80.0, 8)), entry(rows, 60, planned(reps = 12)), name = "Push")
    private val earlier = session(
        "s0", "2026-10-08T10:00:00Z",
        WorkoutExercise("x", bench, null, "", ExerciseOrigin.PLANNED, null, listOf(set("e", planned(77.5, 8), planned(77.5, 8), t0 - 1))),
    )
    private val lastOverall = session(
        "s9", "2026-10-10T09:00:00Z",
        WorkoutExercise("y", dips, null, "", ExerciseOrigin.PLANNED, null, listOf(set("f", planned(), planned(10.0, 12), t0 - 2))),
    )

    private fun ctx(profile: String = "Name: Max\nDate: 2026-11-03", memory: String? = "Likes squats", lang: Language = Language.EN) = CoachContext(
        kind = ReviewKind.SESSION, language = lang, instructions = "You are a strict coach.", profileBlock = profile, memory = memory,
        exerciseList = listOf("wg:bench" to "Bench Press", "wg:row" to "Row"),
        current = current, template = template, sameTemplate = listOf(earlier), lastOverall = lastOverall, timeZone = tz,
    )

    @Test
    fun staticSystemHasRulesAndExerciseListButNoProfileOrDate() {
        val p = CoachPromptBuilder.build(ctx())
        assertTrue(p.staticSystem.startsWith("You are a strict coach."))
        assertTrue(p.staticSystem.contains(CoachPromptBuilder.OUTPUT_RULES))
        assertTrue(p.staticSystem.contains("wg:bench | Bench Press"))
        assertTrue(p.staticSystem.contains("wg:row | Row"))
        assertFalse(p.staticSystem.contains("Name: Max"))
        assertFalse(p.staticSystem.contains("2026-11-03"))
    }

    @Test
    fun saunaIsExplainedInStaticSystemAndMarkedPerWorkout() {
        val sauna = current.copy(sauna = true)
        val p = CoachPromptBuilder.build(ctx().copy(current = sauna, sameTemplate = listOf(earlier.copy(sauna = true))))
        // The explanation is always there (static prefix stays byte-identical for caching).
        assertTrue(p.staticSystem.contains(CoachPromptBuilder.SAUNA_NOTE))
        assertEquals(CoachPromptBuilder.build(ctx()).staticSystem, p.staticSystem)
        val header = p.user.lines().first { it.startsWith("\"Push\" 2026-10-15") }
        assertTrue(header.endsWith("feeling 3/5, sauna: yes"), header)
        assertTrue(p.user.lines().first { it.startsWith("\"Push\" 2026-10-08") }.endsWith("sauna: yes"))
        // Without sauna nothing is added.
        assertFalse(CoachPromptBuilder.build(ctx()).user.contains("sauna"))
    }

    @Test
    fun staticSystemIsStableAcrossProfileChanges() {
        val a = CoachPromptBuilder.build(ctx(profile = "Name: Max\nDate: 2026-11-03"))
        val b = CoachPromptBuilder.build(ctx(profile = "Name: Eva\nDate: 2026-12-24"))
        assertEquals(a.staticSystem, b.staticSystem)
        assertTrue(a.dynamicSystem != b.dynamicSystem)
    }

    @Test
    fun dynamicSystemHasProfileAndLanguage() {
        val de = CoachPromptBuilder.build(ctx(lang = Language.DE)).dynamicSystem
        assertTrue(de.contains("Name: Max"))
        assertTrue(de.contains("German"))
        assertTrue(CoachPromptBuilder.build(ctx(lang = Language.EN)).dynamicSystem.contains("English"))
    }

    @Test
    fun userMessageSessionContents() {
        val u = CoachPromptBuilder.build(ctx()).user
        assertTrue(u.contains("Likes squats"))
        assertTrue(u.contains("## Today's workout"))
        assertTrue(u.contains("plan 80kg×8 → done 80kg×8"))
        assertTrue(u.contains("plan 80kg×8 → done 80kg×7 (+2:00)"))
        assertTrue(u.contains("S3: plan 80kg×8 → not done"))
        assertTrue(u.contains("(replaced wg:old-row)"))
        assertTrue(u.contains("note: felt heavy"))
        assertTrue(u.contains("Note: Slept badly"))
        assertTrue(u.contains("feeling 3/5"))
        assertTrue(u.contains("60 min"))
        assertTrue(u.contains("## Current template \"Push\""))
        assertTrue(u.contains("## Earlier sessions of this template"))
        assertTrue(u.contains("2026-10-08"))
        assertTrue(u.contains("## Latest other workout"))
        assertTrue(u.contains("Dips [wg:dips]"))
        assertTrue(u.contains("No photo: photo_feedback must be null."))
    }

    @Test
    fun firstCompletedSetHasNoGap() {
        val u = CoachPromptBuilder.userMessage(ctx())
        assertTrue(u.contains("S1: plan 80kg×8 → done 80kg×8\n"))
    }

    @Test
    fun emptyMemory() {
        assertTrue(CoachPromptBuilder.userMessage(ctx(memory = null)).contains("(empty"))
        assertTrue(CoachPromptBuilder.userMessage(ctx(memory = "  ")).contains("(empty"))
    }

    @Test
    fun progressKind() {
        val c = ctx().copy(kind = ReviewKind.PROGRESS, current = null, template = null, recent = listOf(current, earlier))
        val u = CoachPromptBuilder.userMessage(c)
        assertTrue(u.contains("next_session must be null"))
        assertTrue(u.contains("## Workouts of the last weeks"))
    }

    @Test
    fun mainExerciseProgressListsRepeatedExercisesOnly() {
        val newer = session(
            "s2", "2026-10-22T10:00:00Z",
            WorkoutExercise("z", bench, null, "", ExerciseOrigin.PLANNED, null, listOf(set("g", planned(), planned(85.0, 5), t0 + 7 * 86_400_000L))),
        )
        val lines = CoachPromptBuilder.mainExerciseProgress(listOf(newer, earlier, lastOverall), tz)
        assertEquals(1, lines.size) // dips only once -> omitted
        val l = lines.single()
        assertTrue(l.startsWith("Bench Press [wg:bench]: "))
        // oldest first: 77.5*(1+8/30)=98.17, 85*(1+5/30)=99.17
        assertTrue(l.indexOf("2026-10-08 77.5kg×8 (e1RM 98)") in 0 until l.indexOf("2026-10-22 85kg×5 (e1RM 99)"))
    }

    @Test
    fun formatSetAllTypes() {
        val f = CoachPromptBuilder::formatSet
        assertEquals("80kg×8", f(planned(80.0, 8), TrackingType.WEIGHT_REPS))
        assertEquals("BW×8", f(planned(null, 8), TrackingType.WEIGHT_REPS))
        assertEquals("-20kg×8", f(planned(-20.0, 8), TrackingType.WEIGHT_REPS))
        assertEquals("12 reps", f(planned(reps = 12), TrackingType.REPS))
        assertEquals("45s", f(planned(duration = 45), TrackingType.TIME))
        assertEquals("20kg×45s", f(planned(20.0, null, 45), TrackingType.WEIGHT_TIME))
        assertEquals("? reps", f(planned(), TrackingType.REPS))
    }

    @Test
    fun comparisonsAppearAsPerExerciseSection() {
        val stats = ExerciseStats("2026-10-15", SetValues(80.0, 8, null), 2, 1200.0, 101.3, 8, null, 101.3)
        val c = ExerciseComparison("wg:bench", "Bench Press", TrackingType.WEIGHT_REPS, stats, stats.copy(date = "2026-10-08"), Verdict.SAME)
        val u = CoachPromptBuilder.build(ctx().copy(comparisons = listOf(c))).user
        assertTrue(u.contains("## Per exercise"), u)
        assertTrue(u.contains("Bench Press [wg:bench]: today best 80kg×8"), u)
        assertTrue(u.contains("last 2026-10-08"), u)
        assertTrue(u.contains("by the numbers: same"), u)
        assertFalse(CoachPromptBuilder.build(ctx()).user.contains("Per exercise"))
    }

    @Test
    fun outputRulesMentionExerciseReviews() {
        assertTrue(CoachPromptBuilder.OUTPUT_RULES.contains("exercise_reviews"))
        assertTrue(CoachPromptBuilder.build(ctx()).staticSystem.contains("exercise_reviews"))
    }
}
