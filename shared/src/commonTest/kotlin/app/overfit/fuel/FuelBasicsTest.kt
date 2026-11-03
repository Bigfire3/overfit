package app.overfit.fuel

import app.overfit.domain.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        assertEquals("1,00", formatRevealNumber(1_000_000, Language.DE))
        assertEquals("0.37", formatRevealNumber(370_000, Language.EN))
    }

    @Test
    fun revealCountsUpMonotonicallyAndLandsExactly() {
        val plan = RevealPlan(1_000_000)
        assertEquals(RevealPhase.Black, plan.phaseAt(0))
        assertEquals(0, plan.valueAt(0))
        assertEquals(0, plan.valueAt(plan.blackMs - 1))
        assertEquals(RevealPhase.Counting, plan.phaseAt(plan.blackMs))
        assertEquals(RevealPhase.Done, plan.phaseAt(plan.totalMs))
        assertEquals(1_000_000, plan.valueAt(plan.totalMs))
        assertEquals(1_000_000, plan.valueAt(plan.totalMs + 10_000))
        var last = 0L
        for (t in 0..plan.totalMs step 16) {
            val v = plan.valueAt(t)
            assertTrue(v >= last && v <= 1_000_000, "not monotonic at $t")
            last = v
        }
    }

    @Test
    fun spotTokenParsing() {
        assertEquals("abc123.def_456-x", parseSpotToken("https://bigfire3.github.io/overfit/spot#abc123.def_456-x"))
        assertEquals("abc123.def_456-x", parseSpotToken("  abc123.def_456-x "))
        assertEquals("abc123.def_456-x", parseSpotToken(spotLink("abc123.def_456-x")))
        assertEquals("abc123.def_456-x", parseSpotToken("overfit://spot#abc123.def_456-x"))
        assertTrue(isSpotLink(" overfit://spot#abc123.def_456-x"))
        assertTrue(!isSpotLink("overfit://t#abc"))
        assertNull(parseSpotToken("https://example.com/foo"))
        assertNull(parseSpotToken("hello world"))
        assertNull(parseSpotToken(""))
    }

    @Test
    fun backendUrlNormalizing() {
        assertEquals("https://x.workers.dev", normalizeBackendUrl(" https://x.workers.dev/ "))
        assertEquals("", normalizeBackendUrl("x.workers.dev"))
        assertEquals("", normalizeBackendUrl(""))
    }
}
