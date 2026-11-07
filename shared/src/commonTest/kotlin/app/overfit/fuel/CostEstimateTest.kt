package app.overfit.fuel

import app.overfit.domain.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CostEstimateTest {
    // Prices of backend/src/providers.ts (µ€ per million tokens, 0.92 €/$).
    private val gemini = FuelTierInfo(true, "gemini-3.6-flash", true, TierPrice(690_000, 3_450_000), "Gemini 3.6 Flash", "Google", 1_000)
    private val claude = FuelTierInfo(true, "claude-sonnet-5-5", true, TierPrice(1_840_000, 9_200_000), "Claude Sonnet 5.5", "Anthropic")
    private val llama = FuelTierInfo(true, "@cf/meta/llama-3.1-8b-instruct", false, TierPrice(259_153, 760_488), "Llama 3.1 8B", "Cloudflare")

    @Test
    fun reviewCostIsListPriceTimesTypicalTokensPlusMarkup() {
        // Gemini: (8200 in × 690 000 + (1400 + 1000 thinking) out × 3 450 000) / 1e6 = 13 938 µ€; +8 % = 15 053.04 → 15 054
        assertEquals(15_054, CostEstimate.micro(gemini, 800, FuelTask.COACH))
        // Claude: (8200 × 1 840 000 + 1400 × 9 200 000) / 1e6 = 27 968; +8 % = 30 205.44 → 30 206
        assertEquals(30_206, CostEstimate.micro(claude, 800, FuelTask.COACH))
    }

    @Test
    fun currentMarkupOf28PercentIsIncluded() {
        // Session AB: MARKUP_BP 2800. Gemini: 13 938 µ€ × 1.28 = 17 840.64 → 17 841 (≈ 1.8 ct per review)
        assertEquals(17_841, CostEstimate.micro(gemini, 2_800, FuelTask.COACH))
    }

    @Test
    fun chatAndAutofillUseTheirOwnProfiles() {
        // chat Gemini: (6700 × 690 000 + 1500 × 3 450 000) / 1e6 = 9 798 → +8 % = 10 581.84 → 10 582
        assertEquals(10_582, CostEstimate.micro(gemini, 800, FuelTask.CHAT))
        // autofill Llama: (900 × 259 153 + 120 × 760 488) / 1e6 = 324.5 → +8 % = 350.5 → 351 (no thinking tokens)
        assertEquals(351, CostEstimate.micro(llama, 800, FuelTask.AUTOFILL))
    }

    @Test
    fun backendProfilesOverrideTheDefaults() {
        val custom = mapOf("coach" to UsageProfile(1_000_000, 0))
        assertEquals(748_926, CostEstimate.micro(gemini, 800, FuelTask.COACH, custom)) // (690 000 + 1000 thinking × 3.45) × 1.08
    }

    @Test
    fun affordableCountsWholeRequests() {
        assertEquals(278, CostEstimate.affordable(4_200_000, 15_054))
        assertEquals(0, CostEstimate.affordable(-5, 15_054))
        assertEquals(0, CostEstimate.affordable(1_000, 0))
    }

    @Test
    fun centsAreShownWithSensiblePrecision() {
        assertEquals("1,5 ct", formatCents(15_054, Language.DE))
        assertEquals("3,0 ct", formatCents(30_206, Language.DE))
        assertEquals("0,04 ct", formatCents(351, Language.DE))
        assertEquals("< 0,01 ct", formatCents(20, Language.DE))
        assertEquals("1.5¢", formatCents(15_054, Language.EN).replace(" ", ""))
        assertEquals("1,20 €", formatCents(1_200_000, Language.DE))
    }

    @Test
    fun modelNamesAreRealNamesNeverTierNames() {
        assertEquals("Gemini 3.6 Flash (Google)", gemini.label)
        assertEquals("Claude Sonnet 5.5 (Anthropic)", claude.label)
        assertEquals("Llama 3.1 8B (Cloudflare)", llama.label)
        // Old backend without names: falls back to the known name of the model ID
        assertEquals("Claude Sonnet 5.5 (Anthropic)", FuelTierInfo(true, "claude-sonnet-5-5").label)
        assertEquals("Gemini 3.6 Flash", modelShortName("gemini-3.6-flash"))
        assertEquals("unbekanntes-modell", modelLabel("unbekanntes-modell"))
        listOf("gemini-3.6-flash", "claude-sonnet-5-5", "@cf/meta/llama-3.1-8b-instruct").forEach {
            val l = modelLabel(it)
            assertTrue(listOf("Standard", "Pro", "Lite").none { w -> Regex("\\b$w\\b").containsMatchIn(l) }, l)
        }
        assertEquals("gemini-3.6-flash", modelOfTier("standard"))
        assertFalse(modelOfTier("quatsch") != null)
        // Bookings since the Claude switch (7.11.2026) name the Claude model of their tier, older ones the old table.
        assertEquals("claude-sonnet-5-5", modelOfTierAt("standard", CLAUDE_ONLY_SINCE_SEC))
        assertEquals("claude-opus-5-5", modelOfTierAt("pro", CLAUDE_ONLY_SINCE_SEC + 60))
        assertEquals("claude-haiku-5-5", modelOfTierAt("lite", CLAUDE_ONLY_SINCE_SEC))
        assertEquals("gemini-3.6-flash", modelOfTierAt("standard", CLAUDE_ONLY_SINCE_SEC - 1))
        assertNull(modelOfTierAt("quatsch", CLAUDE_ONLY_SINCE_SEC))
    }
}
