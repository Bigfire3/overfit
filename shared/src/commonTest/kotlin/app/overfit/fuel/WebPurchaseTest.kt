package app.overfit.fuel

import app.overfit.ui.fuel.formatCodeInput
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebPurchaseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun configFromServerAndFlagPerChannel() {
        val cfg = json.decodeFromString<FuelConfig>(
            """{"mode":"test","packages":[],"webPurchase":{"enabled":true,"testMode":true,"provider":"stripe","channels":["sideload"],
               "pageUrl":"https://bigfire3.github.io/overfit/guthaben/","packages":[{"id":"web.5","priceMicro":5000000,"creditMicro":5000000}]}}""",
        )
        assertTrue(cfg.webPurchase.testMode)
        assertEquals(listOf(WebPackage("web.5", 5_000_000, 5_000_000)), cfg.webPurchase.packages)
        assertTrue(cfg.showsWebPurchase(Distribution.Sideload))
        assertFalse(cfg.showsWebPurchase(Distribution.Store), "store builds stay off until the server allows `store`")
        val both = cfg.copy(webPurchase = cfg.webPurchase.copy(channels = listOf("sideload", "store")))
        assertTrue(both.showsWebPurchase(Distribution.Store))
        val off = cfg.copy(webPurchase = cfg.webPurchase.copy(enabled = false))
        assertFalse(off.showsWebPurchase(Distribution.Sideload))
    }

    @Test
    fun oldBackendWithoutWebPurchaseMeansOff() {
        val cfg = json.decodeFromString<FuelConfig>("""{"packages":[]}""")
        assertFalse(cfg.showsWebPurchase(Distribution.Sideload))
        assertFalse(cfg.showsWebPurchase(Distribution.Store))
    }

    @Test
    fun returnLink() {
        assertTrue(isCreditReturnLink("overfit://guthaben"))
        assertTrue(isCreditReturnLink(" overfit://guthaben?x=1 "))
        assertFalse(isCreditReturnLink("overfit://guthabenX"))
        assertFalse(isCreditReturnLink("overfit://t#abc"))
        assertFalse(isCreditReturnLink("https://bigfire3.github.io/overfit/guthaben/"))
    }

    @Test
    fun ledgerSourceIsParsed() {
        val page = json.decodeFromString<LedgerPage>(
            """{"entries":[{"id":3,"ts":1,"type":"purchase","amountMicro":5000000,"source":"web"},{"id":2,"ts":1,"type":"code_redeem","amountMicro":5000000,"source":"code"},{"id":1,"ts":1,"type":"usage","amountMicro":-1}],"next":null}""",
        )
        assertEquals(listOf("web", "code", null), page.entries.map { it.source })
    }

    @Test
    fun codeInputIsUppercasedAndCleaned() {
        assertEquals("ABCD-EFGH-JKMN", formatCodeInput("abcd-efgh-jkmn"))
        assertEquals("ABCDEFGH", formatCodeInput("ab cd.ef gh"))
        assertEquals(16, formatCodeInput("A".repeat(40)).length)
    }
}
