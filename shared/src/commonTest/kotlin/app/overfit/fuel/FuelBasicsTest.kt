package app.overfit.fuel

import app.overfit.domain.Language
import kotlin.test.Test
import kotlin.test.assertEquals

class FuelBasicsTest {
    @Test
    fun sha256Vectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.hash(ByteArray(0)).toHex())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.hash("abc".encodeToByteArray()).toHex())
        // two blocks (56 bytes forces an extra padding block)
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.hash("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()).toHex(),
        )
    }

    @Test
    fun canonicalStringMatchesContractExample() {
        val body = """{"displayName":"Fabian"}""".encodeToByteArray()
        assertEquals("84adfca0d14b5eeabe8c85f59d4ad3aa7975a28d924e5a43d24e268a50bd7b7c", Sha256.hash(body).toHex())
        val text = RequestSigner.canonicalString("put", "/v1/account/profile", "1762128000", "q3xK9mZc2YpWb7LdEoT5Ag", body)
        assertEquals(
            "OVERFIT-V1\nPUT\n/v1/account/profile\n1762128000\nq3xK9mZc2YpWb7LdEoT5Ag\n84adfca0d14b5eeabe8c85f59d4ad3aa7975a28d924e5a43d24e268a50bd7b7c",
            text,
        )
    }

    @Test
    fun emptyBodyHashAndQueryInPath() {
        val text = RequestSigner.canonicalString("GET", "/v1/account/ledger?limit=20", "1", "n", ByteArray(0))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", text.lines().last())
        assertEquals("/v1/account/ledger?limit=20", text.lines()[2])
    }

    @Test
    fun deviceIdAndPublicKeyFormats() {
        val point = ByteArray(65) { if (it == 0) 4 else it.toByte() }
        val spki = ByteArray(26) { 1 } + point
        assertEquals(point.toList(), rawPointOf(spki).toList())
        assertEquals(point.toList(), rawPointOf(point).toList())
        assertEquals(43, deviceIdOf(point).length)
        assertEquals(point.toList(), point.toBase64Url().fromBase64Url().toList())
    }

    @Test
    fun euroFormatting() {
        assertEquals("4,20 €", formatEuro(4_200_000, Language.DE))
        assertEquals("€4.20", formatEuro(4_200_000, Language.EN))
        assertEquals("0,00 €", formatEuro(0, Language.DE))
        assertEquals("0,01 €", formatEuro(5_000, Language.DE)) // half up
        assertEquals("0,00 €", formatEuro(4_999, Language.DE))
        assertEquals("−1,50 €", formatEuro(-1_500_000, Language.DE))
        assertEquals("7,87 €", formatEuro(7_870_370, Language.DE))
        assertEquals("0,0070 €", formatCost(6_956, Language.DE))
        assertEquals("0,10 €", formatCost(100_000, Language.DE))
    }

    @Test
    fun ledgerKindsAndOldAccountFields() {
        assertEquals(LedgerKind.Paid, LedgerEntry(1, 0, "purchase", 5_000_000).kind)
        assertEquals(LedgerKind.Paid, LedgerEntry(1, 0, "code_redeem", 5_000_000).kind)
        assertEquals(LedgerKind.Credited, LedgerEntry(1, 0, "test_credit", 1_000_000).kind)
        assertEquals(LedgerKind.Credited, LedgerEntry(1, 0, "invite_bonus", 0).kind)
        assertEquals(LedgerKind.Used, LedgerEntry(1, 0, "usage", -2_000).kind)
        assertEquals(LedgerKind.Refunded, LedgerEntry(1, 0, "refund", -5_000_000).kind)
        assertEquals(LedgerKind.Deducted, LedgerEntry(1, 0, "invite_expired", -1_000_000, category = "expired").kind)
        assertEquals(LedgerKind.Used, LedgerEntry(1, 0, "invite_expired", -1_000_000).kind)
        assertEquals(LedgerKind.Used, LedgerEntry(1, 0, "x", 0, category = "used").kind) // server wins
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        // An older backend still sends the removed invite/gift fields: they are ignored, only the credit counts.
        val a = json.decodeFromString<FuelAccount>(
            """{"balanceMicro":4200000,"purchasedMicro":3200000,"giftedMicro":1000000,"expiring":[{"amountMicro":1000000,"expiresAt":9}],"giftReviews":{"remaining":5,"expiring":[{"count":5,"expiresAt":9}]},"invite":{"invitedThisMonth":2}}""",
        )
        assertEquals(4_200_000, a.balanceMicro)
        assertEquals("4,20 € Guthaben", app.overfit.ui.fuel.creditSummary(a, app.overfit.i18n.GermanFuel))
        assertEquals("€4.20 credit", app.overfit.ui.fuel.creditSummary(a, app.overfit.i18n.EnglishFuel))
        assertEquals(0, a.reservedMicro) // older servers do not send it
    }

    @Test
    fun reservationIsShownNextToTheBalance() {
        val a = FuelAccount(balanceMicro = 4_200_000, reservedMicro = 610_000)
        assertEquals("4,20 € Guthaben (0,61 € reserviert)", app.overfit.ui.fuel.creditSummary(a, app.overfit.i18n.GermanFuel))
        assertEquals("€4.20 credit (€0.61 reserved)", app.overfit.ui.fuel.creditSummary(a, app.overfit.i18n.EnglishFuel))
        assertEquals("(0,61 € reserviert)", app.overfit.ui.fuel.reservedNote(610_000, app.overfit.i18n.GermanFuel))
        assertEquals(null, app.overfit.ui.fuel.reservedNote(0, app.overfit.i18n.GermanFuel))
    }

    @Test
    fun requestLogTellsTimeoutFromNoConnection() {
        val r = app.overfit.i18n.requestLogStringsFor(app.overfit.domain.Language.EN)
        assertEquals("No answer in time", app.overfit.ui.fuel.errorText("timeout", r))
        assertEquals("No answer in time", app.overfit.ui.fuel.errorText("TIMEOUT", r))
        assertEquals("No connection", app.overfit.ui.fuel.errorText("network", r))
    }

    @Test
    fun backendUrlNormalizing() {
        assertEquals("https://x.workers.dev", normalizeBackendUrl(" https://x.workers.dev/ "))
        assertEquals("", normalizeBackendUrl("x.workers.dev"))
        assertEquals("", normalizeBackendUrl(""))
    }
}
