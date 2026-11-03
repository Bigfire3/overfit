package app.overfit.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileTest {
    @Test
    fun parseDecimalAcceptsCommaAndDot() {
        assertEquals(82.4, parseDecimal("82,4"))
        assertEquals(82.4, parseDecimal("82.4"))
        assertEquals(82.0, parseDecimal(" 82 "))
    }

    @Test
    fun parseDecimalRejectsGarbage() {
        assertNull(parseDecimal(""))
        assertNull(parseDecimal("abc"))
        assertNull(parseDecimal("-3"))
        assertNull(parseDecimal("1,2,3"))
    }

    @Test
    fun parseAgeChecksRange() {
        assertEquals(31, parseAge("31"))
        assertNull(parseAge("5"))
        assertNull(parseAge("200"))
        assertNull(parseAge("3.5"))
    }

    @Test
    fun parseWeightKeepsKgAndConvertsLb() {
        assertEquals(82.4, parseWeightToKg("82,4", WeightUnit.KG))
        assertEquals(81.6, parseWeightToKg("180", WeightUnit.LB))
        assertNull(parseWeightToKg("10", WeightUnit.KG))
        assertNull(parseWeightToKg("900", WeightUnit.KG))
    }

    @Test
    fun ageStaysCurrentOverTime() {
        val today = LocalDate(2026, 11, 2)
        assertEquals(1995, birthYearFromAge(31, today))
        assertEquals(31, ageFromBirthYear(1995, today))
        assertEquals(32, ageFromBirthYear(1995, LocalDate(2027, 11, 2)))
    }

    @Test
    fun sameDayWeightReplacesEntry() {
        val entries = upsertWeight(
            listOf(WeightEntry("2026-11-01", 83.0), WeightEntry("2026-11-02", 82.8)),
            WeightEntry("2026-11-02", 82.4),
        )
        assertEquals(listOf(WeightEntry("2026-11-01", 83.0), WeightEntry("2026-11-02", 82.4)), entries)
        assertEquals(WeightEntry("2026-11-02", 82.4), latestWeight(entries))
    }

    @Test
    fun formatsPerLanguageAndUnit() {
        assertEquals("82,4 kg", formatWeight(82.4, WeightUnit.KG, Language.DE))
        assertEquals("82.4 kg", formatWeight(82.4, WeightUnit.KG, Language.EN))
        assertEquals("82 kg", formatWeight(82.0, WeightUnit.KG, Language.EN))
        assertEquals("179.9 lb", formatWeight(81.6, WeightUnit.LB, Language.EN))
    }
}
