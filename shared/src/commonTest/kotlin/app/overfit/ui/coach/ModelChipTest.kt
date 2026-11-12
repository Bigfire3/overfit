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
    fun sendButtonShowsThePrice() {
        val s = app.overfit.i18n.coachChatStringsFor(Language.DE)
        val tokens = app.overfit.coach.TokenEstimate(6_700, 0)
        val credit = app.overfit.coach.ChatPricing.credit(config, "standard")
        // 6 700 in × 1,84 € + 500 out × 9,20 € per million tokens, +28 % → 2,17 ct.
        assertEquals("≈ 2,2 ct", app.overfit.ui.coach.chat.sendPriceLabel(credit, tokens, s, Language.DE))
        assertEquals("≈ 2.2 ¢", app.overfit.ui.coach.chat.sendPriceLabel(credit, tokens, app.overfit.i18n.coachChatStringsFor(Language.EN), Language.EN))
        // A photo (1600 tokens) makes it dearer.
        val photo = app.overfit.ui.coach.chat.sendPriceLabel(credit, tokens.copy(imageTokens = 1_600), s, Language.DE)
        assertEquals("≈ 2,5 ct", photo)
        // Unavailable tier / unknown config: said so (never a bare arrow).
        assertEquals("Preis unbekannt", app.overfit.ui.coach.chat.sendPriceLabel(app.overfit.coach.ChatPricing.credit(config, "pro"), tokens, s, Language.DE))
        assertEquals("Preis unbekannt", app.overfit.ui.coach.chat.sendPriceLabel(app.overfit.coach.ChatPricing.credit(null, "standard"), tokens, s, Language.DE))
        // Credit before the estimate is built: no number yet.
        assertNull(app.overfit.ui.coach.chat.sendPriceLabel(credit, null, s, Language.DE))
        // Own key: "eigener Schlüssel", with the list price (no markup) when the stored config knows the model.
        val known = app.overfit.coach.ChatPricing.ownKey(config, "claude-sonnet-5-5")
        assertEquals("Schlüssel ≈ 1,7 ct", app.overfit.ui.coach.chat.sendPriceLabel(known, tokens, s, Language.DE))
        val unknown = app.overfit.coach.ChatPricing.ownKey(null, "gpt-5")
        assertEquals("eigener Schlüssel", app.overfit.ui.coach.chat.sendPriceLabel(unknown, tokens, s, Language.DE))
        assertEquals("Demo · kostenlos", app.overfit.ui.coach.chat.sendPriceLabel(app.overfit.coach.ChatPricing.Demo, tokens, s, Language.DE))
    }
}
