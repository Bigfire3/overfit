package app.overfit.ui.coach

import app.overfit.ai.ProviderId
import app.overfit.coach.CoachSettings
import app.overfit.data.AppState
import app.overfit.data.PersistedState
import app.overfit.data.Settings
import app.overfit.domain.Language
import app.overfit.domain.Profile
import app.overfit.fuel.FuelConfig
import app.overfit.fuel.FuelTierInfo
import app.overfit.fuel.TierPrice
import app.overfit.i18n.EnglishCoachSettings
import app.overfit.i18n.GermanCoachSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelChipTest {
    private fun state(provider: ProviderId, key: Boolean) =
        AppState(PersistedState(profile = Profile("Fabi"), settings = Settings(Language.DE), providerId = provider), hasApiKey = key)

    private val config = FuelConfig(
        markupBp = 2_800,
        tiers = mapOf(
            "standard" to FuelTierInfo(true, "claude-sonnet-5-5", true, TierPrice(1_840_000, 9_200_000), "Claude Sonnet 5.5", "Anthropic"),
            "pro" to FuelTierInfo(false, "claude-opus-5-5", true, TierPrice(3_680_000, 18_400_000), "Claude Opus 5.5", "Anthropic"),
        ),
    )

    @Test
    fun creditShowsTheModelOfTheTier() {
        val fuel = state(ProviderId.FUEL, key = false)
        assertEquals("Claude Sonnet 5.5", modelChipLabel(fuel, CoachSettings(), fuelAvailable = true, GermanCoachSettings))
        val pro = CoachSettings(models = mapOf("FUEL" to "pro"))
        assertEquals("Claude Opus 5.5", modelChipLabel(fuel, pro, fuelAvailable = true, GermanCoachSettings))
        assertEquals("Kein KI-Zugang", modelChipLabel(fuel, pro, fuelAvailable = false, GermanCoachSettings))
    }

    @Test
    fun ownKeyShowsTheModelNameOrId() {
        val anthropic = state(ProviderId.ANTHROPIC, key = true)
        assertEquals("Claude Sonnet 5.5", modelChipLabel(anthropic, CoachSettings(models = mapOf("ANTHROPIC" to "claude-sonnet-5-5")), true, GermanCoachSettings))
        val openAi = state(ProviderId.OPENAI, key = true)
        assertEquals("gpt-5.5-mini", modelChipLabel(openAi, CoachSettings(models = mapOf("OPENAI" to "gpt-5.5-mini")), false, GermanCoachSettings))
    }

    @Test
    fun noAccess() {
        assertEquals("No AI access", modelChipLabel(state(ProviderId.OPENROUTER, key = false), CoachSettings(), true, EnglishCoachSettings))
    }

    @Test
    fun costOnlyWithCredit() {
        val fuel = state(ProviderId.FUEL, key = false)
        // 6 700 in × 1,84 € + 500 out × 9,20 € per million tokens, +28 % → 2,17 ct.
        assertEquals("≈ 2,2 ct", chatCostEstimate(fuel, CoachSettings(), config, Language.DE))
        assertEquals("≈ 2.2 ¢", chatCostEstimate(fuel, CoachSettings(), config, Language.EN))
        // Unavailable tier, unknown config, own key, no access: no estimate.
        assertNull(chatCostEstimate(fuel, CoachSettings(models = mapOf("FUEL" to "pro")), config, Language.DE))
        assertNull(chatCostEstimate(fuel, CoachSettings(), null, Language.DE))
        assertNull(chatCostEstimate(state(ProviderId.ANTHROPIC, key = true), CoachSettings(), config, Language.DE))
        assertNull(chatCostEstimate(state(ProviderId.OPENROUTER, key = false), CoachSettings(), config, Language.DE))
    }
}
