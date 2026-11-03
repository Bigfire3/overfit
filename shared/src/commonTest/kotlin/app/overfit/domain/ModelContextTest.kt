package app.overfit.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelContextTest {
    private val today = LocalDate(2026, 11, 2)

    @Test
    fun rendersFullBlock() {
        val block = buildProfileBlock(
            profile = Profile(name = "Fabi", birthYear = 1995, unit = WeightUnit.KG),
            weights = listOf(WeightEntry("2026-10-01", 84.0), WeightEntry("2026-11-02", 82.0)),
            language = Language.DE,
            today = today,
        )
        assertEquals(
            "User: Fabi · Age: 31 · Weight: 82 kg (as of 2026-11-02)\n" +
                "Reply language: German · Units: metric · Today: 2026-11-02",
            block,
        )
    }

    @Test
    fun usesDisplayUnitAndSkipsMissingFields() {
        val block = buildProfileBlock(
            profile = Profile(name = "Sam", birthYear = null, unit = WeightUnit.LB),
            weights = listOf(WeightEntry("2026-11-02", 81.6)),
            language = Language.EN,
            today = today,
        )
        assertEquals(
            "User: Sam · Weight: 179.9 lb (as of 2026-11-02)\n" +
                "Reply language: English · Units: imperial · Today: 2026-11-02",
            block,
        )
    }
}
