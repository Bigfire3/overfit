package app.overfit.i18n

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class StringsTest {
    @Test
    fun formatsDatesPerLanguage() {
        assertEquals("2. Nov. 2026", German.date(LocalDate(2026, 11, 2)))
        assertEquals("Nov 2, 2026", English.date(LocalDate(2026, 11, 2)))
        assertEquals("1. März 2027", German.date(LocalDate(2027, 3, 1)))
    }
}
