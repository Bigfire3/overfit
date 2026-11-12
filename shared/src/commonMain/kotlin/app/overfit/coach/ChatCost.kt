package app.overfit.coach

import app.overfit.fuel.DEFAULT_USAGE_PROFILES
import app.overfit.fuel.FuelConfig
import app.overfit.fuel.FuelTask
import app.overfit.fuel.TierPrice
import kotlin.math.ceil

/**
 * Input tokens one chat question will probably use: [textTokens] from the real prompt (system prompt with instructions,
 * rules and exercise list, profile, attachments, history, question) and [imageTokens] for attached photos. The answer
 * comes from [ChatPricing.outputTokens].
 */
data class TokenEstimate(val textTokens: Long, val imageTokens: Long) {
    val inputTokens: Long get() = textTokens + imageTokens

    /** The same estimate with [text] (the question being typed) added. */
    fun plusText(text: String): TokenEstimate = copy(textTokens = textTokens + ChatTokens.text(text))
}

/**
 * Rough token counting without a tokenizer (good enough for a "≈" price; see DECISIONS „Preis-Schätzung“).
 * Photos: the app sends them with the long edge at [PHOTO_MAX_EDGE] px; Anthropic counts (width × height) / 750 tokens
 * and scales images down above ~[MAX_IMAGE_TOKENS] tokens.
 */
object ChatTokens {
    /** Mixed German/English text, JSON and the exercise list: about 3.5 characters per token. */
    const val CHARS_PER_TOKEN = 3.5
    /** Per message and for the request frame (roles, schema instructions). */
    const val MESSAGE_OVERHEAD = 8L
    /** Long edge of a photo as the app sends it ([app.overfit.platform.prepareJpeg] with maxEdge 1500). */
    const val PHOTO_MAX_EDGE = 1500
    /** Phone photos are 3:4: 1125 × 1500 px after downscaling. */
    const val PHOTO_SHORT_EDGE = 1125
    /** Anthropic scales larger images down to about this many tokens (≈ 1.15 megapixels). */
    const val MAX_IMAGE_TOKENS = 1_600L

    fun text(text: String): Long = if (text.isEmpty()) 0 else ceil(text.length / CHARS_PER_TOKEN).toLong()

    /** Tokens of one image of [width] × [height] px: (w × h) / 750, at most [MAX_IMAGE_TOKENS]. */
    fun image(width: Int = PHOTO_SHORT_EDGE, height: Int = PHOTO_MAX_EDGE): Long =
        minOf((width.toLong() * height + 749) / 750, MAX_IMAGE_TOKENS)

    /** Input of [prompt] as it is sent; its images are counted through [photos] (the estimate does not load them). */
    fun estimate(prompt: CoachPrompt, photos: Int): TokenEstimate {
        val schema = prompt.schema?.toString().orEmpty()
        val text = text(prompt.staticSystem) + text(prompt.dynamicSystem) + text(prompt.user) + text(schema) +
            prompt.history.sumOf { text(it.text) + MESSAGE_OVERHEAD } + 3 * MESSAGE_OVERHEAD
        return TokenEstimate(text, photos * image())
    }
}

/** What the next question costs: with credit (price of the chosen tier + markup), own key (list price if known), demo. */
sealed interface ChatPricing {
    /** Expected answer tokens (typical chat answer + thinking tokens of the model). */
    val outputTokens: Long

    /** Credit: list price of the chosen tier (the cheaper fallback model never costs more) plus [markupBp]. */
    data class Credit(val price: TierPrice, val markupBp: Int, override val outputTokens: Long) : ChatPricing

    /** Own key: the provider bills the user directly; [price] is the list price when Overfit knows the model, else null. */
    data class OwnKey(val price: TierPrice?, override val outputTokens: Long) : ChatPricing

    /** No AI access: the demo answers for free. */
    data object Demo : ChatPricing { override val outputTokens = 0L }

    /** Credit, but the price table is not known yet (backend not reached). */
    data object Unknown : ChatPricing { override val outputTokens = DEFAULT_USAGE_PROFILES.getValue(FuelTask.CHAT.key).outputTokens }

    companion object {
        /** Typical answer tokens of a chat question from the backend's usage profile, plus [thinking] tokens. */
        fun outputTokens(config: FuelConfig?, thinking: Int): Long {
            val profile = config?.usageProfiles?.get(FuelTask.CHAT.key) ?: DEFAULT_USAGE_PROFILES.getValue(FuelTask.CHAT.key)
            return profile.outputTokens + thinking
        }

        /** Credit pricing of [tier] from [config]; Unknown when the tier is not in the table. */
        fun credit(config: FuelConfig?, tier: String): ChatPricing {
            val info = config?.tiers?.get(tier)?.takeIf { it.available } ?: return Unknown
            return Credit(info.price, config.markupBp, outputTokens(config, info.thinkingTokens))
        }

        /**
         * Own key: the price of [model] if the last stored server config lists it (no request is made for this, see
         * OwnKeyPrivacyTest), otherwise unknown.
         */
        fun ownKey(storedConfig: FuelConfig?, model: String): ChatPricing {
            val info = storedConfig?.tiers?.values?.firstOrNull { it.model == model || model.endsWith("/" + it.model) }
            return OwnKey(info?.price, outputTokens(storedConfig, info?.thinkingTokens ?: 0))
        }
    }
}

object ChatCost {
    /**
     * Price in µ€ of [tokens] at [price] (µ€ per million tokens) plus [markupBp]; integer maths, rounded up like the
     * backend. Input counted at the full input price (the cached part of the system prompt is cheaper on follow-up
     * questions, so the estimate errs on the high side).
     */
    fun micro(tokens: TokenEstimate, outputTokens: Long, price: TierPrice, markupBp: Int = 0): Long {
        val sum = tokens.inputTokens * price.input + outputTokens * price.out
        val bp = 10_000L
        val num = sum * (bp + markupBp)
        val den = 1_000_000L * bp
        return (num + den - 1) / den
    }

    /** µ€ for [tokens] under [pricing]; null = no price (demo, unknown). */
    fun micro(tokens: TokenEstimate, pricing: ChatPricing): Long? = when (pricing) {
        is ChatPricing.Credit -> micro(tokens, pricing.outputTokens, pricing.price, pricing.markupBp)
        is ChatPricing.OwnKey -> pricing.price?.let { micro(tokens, pricing.outputTokens, it) }
        else -> null
    }
}
