package app.overfit.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NumbersTest {
    @Test
    fun parseWeight_acceptsCommaAndDot() {
        assertEquals(92.5, Numbers.parseWeight("92,5"))
        assertEquals(92.5, Numbers.parseWeight("92.5"))
    }

    @Test
    fun parseWeight_negativeAndTrimmed() {
        assertEquals(-10.0, Numbers.parseWeight("-10"))
        assertEquals(7.0, Numbers.parseWeight(" 7 "))
    }

    @Test
    fun parseWeight_roundsToHundredth() {
        assertEquals(92.46, Numbers.parseWeight("92,456"))
        assertEquals(92.45, Numbers.parseWeight("92.454"))
    }

    @Test
    fun parseWeight_invalidIsNull() {
        listOf("", "   ", "-", ".", "abc", "1,2,3", "1e999", "NaN", "10001").forEach {
            assertNull(Numbers.parseWeight(it), "'$it'")
        }
    }

    @Test
    fun formatWeight_germanFormat() {
        assertEquals("92,5", Numbers.formatWeight(92.5))
        assertEquals("90", Numbers.formatWeight(90.0))
        assertEquals("-10,25", Numbers.formatWeight(-10.25))
        assertEquals("-0,5", Numbers.formatWeight(-0.5))
        assertEquals("0,05", Numbers.formatWeight(0.05))
        assertEquals("0", Numbers.formatWeight(0.0))
    }

    @Test
    fun parseWeight_poundsAreConvertedToKg() {
        val kg = Numbers.parseWeight("225", WeightUnit.LB)
        assertNotNull(kg)
        assertEquals(102.0583, kg, 1e-9)
        assertNull(Numbers.parseWeight("5000", WeightUnit.LB))
    }

    @Test
    fun formatWeight_poundsRoundTrip() {
        val kg = Numbers.parseWeight("225", WeightUnit.LB)!!
        assertEquals("225", Numbers.formatWeight(kg, WeightUnit.LB))
        assertEquals("225", Numbers.formatWeight(kg, WeightUnit.LB, Language.EN))
        assertEquals("2,2", Numbers.formatWeight(1.0, WeightUnit.LB))
    }

    @Test
    fun formatWeight_englishUsesPoint() {
        assertEquals("92.5", Numbers.formatWeight(92.5, language = Language.EN))
        assertEquals("-10.25", Numbers.formatWeight(-10.25, WeightUnit.KG, Language.EN))
        assertEquals("90", Numbers.formatWeight(90.0, WeightUnit.KG, Language.EN))
    }

    @Test
    fun parseCount() {
        assertEquals(12, Numbers.parseCount("12"))
        assertEquals(8, Numbers.parseCount(" 8 "))
        assertEquals(0, Numbers.parseCount("0"))
        assertNull(Numbers.parseCount("-1"))
        assertNull(Numbers.parseCount("abc"))
        assertNull(Numbers.parseCount(""))
        assertNull(Numbers.parseCount("1,5"))
        assertNull(Numbers.parseCount("10000"))
    }

    @Test
    fun parseDuration() {
        assertEquals(90, Numbers.parseDuration("90"))
        assertEquals(90, Numbers.parseDuration("1:30"))
        assertEquals(45, Numbers.parseDuration(":45"))
        assertEquals(90, Numbers.parseDuration(" 1:30 "))
        assertNull(Numbers.parseDuration("1:75"))
        assertNull(Numbers.parseDuration("1:2:3"))
        assertNull(Numbers.parseDuration(""))
        assertNull(Numbers.parseDuration("abc"))
        assertNull(Numbers.parseDuration("-5"))
        assertNull(Numbers.parseDuration("100000"))
    }

    @Test
    fun formatDuration() {
        assertEquals("1:30", Numbers.formatDuration(90))
        assertEquals("0:45", Numbers.formatDuration(45))
        assertEquals("0:00", Numbers.formatDuration(0))
        assertEquals("60:00", Numbers.formatDuration(3600))
    }
}
