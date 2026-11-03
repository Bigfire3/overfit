package app.overfit.domain

import app.overfit.data.catalog.CatalogSeeder
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Suchqualität gegen den echten, gebündelten Katalog: typische Suchen liefern die Grundübung zuerst. */
@RunWith(RobolectricTestRunner::class)
class CatalogSearchTest {
    companion object {
        private var index: List<ExerciseSearch.Indexed>? = null
    }

    private fun index(): List<ExerciseSearch.Indexed> = index ?: run {
        val catalog = CatalogSeeder.parse(File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes())
        ExerciseSearch.index(
            catalog.exercises.map {
                Exercise(
                    id = it.id, name = it.name, source = it.source, imageRef = it.image,
                    primaryMuscles = it.primary.mapNotNull(Muscle::fromKey),
                    secondaryMuscles = it.secondary.mapNotNull(Muscle::fromKey),
                    equipment = Equipment.fromKey(it.equipment), trackingType = it.tracking, defaultRestSec = null,
                )
            },
        ).also { index = it }
    }

    private fun first(query: String) = ExerciseSearch.search(index(), ExerciseSearch.Filter(query)).first().name

    @Test
    fun commonQueriesFindBaseExerciseFirst() {
        assertEquals("Pull-up", first("Klimmzug"))
        assertEquals("Pull-up", first("pullup"))
        assertEquals("Bench Press", first("Bankdrücken"))
        assertEquals("Bench Press", first("bench press"))
        assertEquals("Squat", first("Kniebeuge"))
        assertEquals("Deadlift", first("Kreuzheben"))
        assertEquals("Push-up", first("Liegestütz"))
        assertEquals("Plank", first("Unterarmstütz"))
        assertEquals("Lateral Raise", first("seitheben"))
    }

    @Test
    fun englishMuscleLabelFindsExercises() {
        val r = ExerciseSearch.search(index(), ExerciseSearch.Filter("chest"))
        assertEquals(true, r.isNotEmpty())
        assertEquals(true, r.take(20).all { Muscle.CHEST in it.primaryMuscles + it.secondaryMuscles || "chest" in it.name.lowercase() })
    }
}
