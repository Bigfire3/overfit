package app.overfit.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExerciseSearchTest {
    private fun ex(
        name: String,
        source: ExerciseSource = ExerciseSource.WORKOUT_GUIDE,
        muscle: Muscle = Muscle.CHEST,
        equipment: Equipment? = null,
    ) = Exercise(
        id = "x:$name",
        name = name,
        source = source,
        imageRef = null,
        primaryMuscles = listOf(muscle),
        secondaryMuscles = emptyList(),
        equipment = equipment,
        trackingType = TrackingType.WEIGHT_REPS,
        defaultRestSec = null,
    )

    private val bench = ex("Bench Press", equipment = Equipment.BARBELL)
    private val pullUp = ex("Pull-up", muscle = Muscle.LATS, equipment = Equipment.PULLUP_BAR)
    private val pushUp = ex("Push-up", equipment = Equipment.BODYWEIGHT)
    private val dbBench = ex("Dumbbell Bench Press", ExerciseSource.FREE_DB, equipment = Equipment.DUMBBELL)
    private val lateral = ex("Lateral Raise", muscle = Muscle.SHOULDERS, equipment = Equipment.DUMBBELL)
    private val handstand = ex("Handstand Hold", ExerciseSource.CUSTOM, Muscle.CORE)

    private val index = ExerciseSearch.index(listOf(dbBench, handstand, pushUp, lateral, pullUp, bench))

    private fun search(
        query: String = "",
        muscle: Muscle? = null,
        equipment: Equipment? = null,
        onlyCustom: Boolean = false,
    ) = ExerciseSearch.search(index, ExerciseSearch.Filter(query, muscle, equipment, onlyCustom))

    @Test
    fun exactMatchFirst() {
        val r = search("bench press")
        assertEquals(listOf(bench, dbBench), r)
    }

    @Test
    fun prefixMatch() {
        val r = search("benc")
        assertEquals(bench, r.first())
        assertTrue(dbBench in r)
        assertFalse(pullUp in r)
    }

    @Test
    fun typoMatch() {
        assertEquals(bench, search("bensh press").first())
    }

    @Test
    fun compoundWritingFindsHyphenatedName() {
        assertEquals(pushUp, search("pushup").first())
    }

    @Test
    fun germanSynonyms() {
        assertEquals(pullUp, search("Klimmzug").first())
        assertEquals(bench, search("Bankdrücken").first())
    }

    @Test
    fun muscleLabelIsSearchable() {
        val r = search("Brust")
        assertEquals(setOf(bench, pushUp, dbBench), r.toSet())
    }

    @Test
    fun englishMuscleAndEquipmentLabelsAreSearchable() {
        assertEquals(setOf(bench, pushUp, dbBench), search("chest").toSet())
        assertEquals(listOf(bench), search("chest barbell"))
        assertEquals(setOf(dbBench, lateral), search("dumbbell").toSet())
    }

    @Test
    fun subtitleFollowsLanguage() {
        assertEquals("Brust · Langhantel", bench.subtitle(Language.DE))
        assertEquals("Chest · Barbell", bench.subtitle(Language.EN))
    }

    @Test
    fun allWordsMustMatch() {
        assertEquals(listOf(dbBench), search("bench dumbbell"))
        assertEquals(listOf(bench), search("brust langhantel"))
        assertTrue(search("bench raise").isEmpty())
    }

    @Test
    fun filters() {
        assertEquals(setOf(bench, pushUp, dbBench), search(muscle = Muscle.CHEST).toSet())
        assertEquals(setOf(dbBench, lateral), search(equipment = Equipment.DUMBBELL).toSet())
        assertEquals(listOf(handstand), search(onlyCustom = true))
        assertEquals(listOf(dbBench), search("bench", equipment = Equipment.DUMBBELL))
    }

    @Test
    fun emptyQueryOrdersCustomThenWorkoutGuideThenFreeDb() {
        assertEquals(
            listOf(handstand, bench, lateral, pullUp, pushUp, dbBench),
            search(),
        )
    }

    @Test
    fun initials() {
        assertEquals("HA", Exercise.initialsOf("Handstand"))
        assertEquals("HB", Exercise.initialsOf("Hollow Body Hold"))
        assertEquals("PU", Exercise.initialsOf("Pull-up"))
        assertEquals("?", Exercise.initialsOf("  "))
    }
}
