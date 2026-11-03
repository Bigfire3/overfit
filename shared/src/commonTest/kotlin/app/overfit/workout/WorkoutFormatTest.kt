package app.overfit.workout

import app.overfit.domain.Language
import app.overfit.domain.TrackingType
import app.overfit.domain.WeightUnit
import app.overfit.domain.Workout
import app.overfit.i18n.English
import app.overfit.i18n.German
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkoutFormatTest {
    private fun de(v: app.overfit.domain.SetValues, type: TrackingType, unit: WeightUnit = WeightUnit.KG) =
        WorkoutFormat.values(v, type, unit, Language.DE, German)

    private fun en(v: app.overfit.domain.SetValues, type: TrackingType, unit: WeightUnit = WeightUnit.KG) =
        WorkoutFormat.values(v, type, unit, Language.EN, English)

    @Test
    fun values_weightReps() {
        assertEquals("30 kg × 6", de(planned(30.0, 6), TrackingType.WEIGHT_REPS))
        assertEquals("92,5 kg × 5", de(planned(92.5, 5), TrackingType.WEIGHT_REPS))
        assertEquals("92.5 kg × 5", en(planned(92.5, 5), TrackingType.WEIGHT_REPS))
    }

    @Test
    fun values_repsOnly() {
        assertEquals("12 Wdh.", de(planned(reps = 12), TrackingType.REPS))
        assertEquals("12 reps", en(planned(reps = 12), TrackingType.REPS))
        // Weight is ignored for a reps-only exercise.
        assertEquals("12 Wdh.", de(planned(40.0, 12), TrackingType.REPS))
    }

    @Test
    fun values_time() {
        assertEquals("0:45", de(planned(duration = 45), TrackingType.TIME))
        assertEquals("1:05", en(planned(duration = 65), TrackingType.TIME))
    }

    @Test
    fun values_weightTime() {
        assertEquals("20 kg · 0:45", de(planned(20.0, duration = 45), TrackingType.WEIGHT_TIME))
        assertEquals("20.5 kg · 0:45", en(planned(20.5, duration = 45), TrackingType.WEIGHT_TIME))
    }

    @Test
    fun values_missingPartsAreLeftOut() {
        assertEquals("30 kg", de(planned(weight = 30.0), TrackingType.WEIGHT_REPS))
        assertEquals("6 Wdh.", de(planned(reps = 6), TrackingType.WEIGHT_REPS))
        assertEquals("20 kg", de(planned(weight = 20.0), TrackingType.WEIGHT_TIME))
        assertEquals("", de(planned(), TrackingType.WEIGHT_REPS))
        assertEquals("", de(planned(), TrackingType.TIME))
    }

    @Test
    fun values_poundsUseLbSymbolAndConvert() {
        val text = de(planned(100.0, 5), TrackingType.WEIGHT_REPS, WeightUnit.LB)
        assertTrue(text.startsWith("220,"), text)
        assertTrue(text.endsWith(" lb × 5"), text)
        val en = en(planned(100.0, 5), TrackingType.WEIGHT_REPS, WeightUnit.LB)
        assertTrue(en.startsWith("220."), en)
    }

    @Test
    fun lastTime_unitOnlyOnFirstWeight() {
        val sets = listOf(planned(90.0, 4), planned(90.0, 3))
        assertEquals("90 kg × 4, 90 × 3", WorkoutFormat.lastTime(sets, TrackingType.WEIGHT_REPS, WeightUnit.KG, Language.DE, German))
        assertEquals("90 kg × 4, 90 × 3", WorkoutFormat.lastTime(sets, TrackingType.WEIGHT_REPS, WeightUnit.KG, Language.EN, English))
    }

    @Test
    fun lastTime_repsOnlyKeepsUnitText() {
        val sets = listOf(planned(reps = 12), planned(reps = 10))
        assertEquals("12 Wdh., 10 Wdh.", WorkoutFormat.lastTime(sets, TrackingType.REPS, WeightUnit.KG, Language.DE, German))
    }

    @Test
    fun lastTime_emptyEntriesAreSkipped() {
        assertEquals("", WorkoutFormat.lastTime(emptyList(), TrackingType.WEIGHT_REPS, WeightUnit.KG, Language.DE, German))
        assertEquals(
            "50 kg × 5",
            WorkoutFormat.lastTime(listOf(planned(50.0, 5), planned()), TrackingType.WEIGHT_REPS, WeightUnit.KG, Language.DE, German),
        )
    }

    @Test
    fun next_combinesExerciseSetNumberAndValues() {
        val w = Workout.fromTemplate(
            templateOf(entry(exercise("lat", name = "Lateral raise"), null, planned(30.0, 6), planned(30.0, 6))),
            now = 0,
        )
        val ref = w.nextOpenSet(w.exercises[0].sets[0].id)!!
        assertEquals("Lateral raise · Set 2 · 30 kg × 6", WorkoutFormat.next(ref, WeightUnit.KG, Language.EN, English))
        assertEquals("Lateral raise · Satz 2 · 30 kg × 6", WorkoutFormat.next(ref, WeightUnit.KG, Language.DE, German))
    }

    @Test
    fun next_withoutValuesLeavesThemOut() {
        val w = Workout.added(exercise("a", name = "Curl"), emptyList()).let { Workout.empty("x", 0).addExercise(it) }
        val ref = w.nextOpenSet()!!
        assertEquals("Curl · Set 1", WorkoutFormat.next(ref, WeightUnit.KG, Language.EN, English))
    }

    @Test
    fun elapsed_underAndOverOneHour() {
        assertEquals("0:00", WorkoutFormat.elapsed(0))
        assertEquals("0:59", WorkoutFormat.elapsed(59_999))
        assertEquals("12:05", WorkoutFormat.elapsed(12 * 60_000L + 5_000))
        assertEquals("59:59", WorkoutFormat.elapsed(3_599_000))
        assertEquals("1:00:00", WorkoutFormat.elapsed(3_600_000))
        assertEquals("1:02:05", WorkoutFormat.elapsed(3_600_000 + 2 * 60_000 + 5_000))
        assertEquals("0:00", WorkoutFormat.elapsed(-5_000))
    }

    @Test
    fun countdown_roundsUp() {
        assertEquals("0:00", WorkoutFormat.countdown(0))
        assertEquals("0:01", WorkoutFormat.countdown(1))
        assertEquals("0:01", WorkoutFormat.countdown(1000))
        assertEquals("0:02", WorkoutFormat.countdown(1001))
        assertEquals("1:30", WorkoutFormat.countdown(90_000))
        assertEquals("1:30", WorkoutFormat.countdown(89_001))
        assertEquals("1:29", WorkoutFormat.countdown(89_000))
    }
}
