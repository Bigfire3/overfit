package app.overfit.platform

import app.overfit.data.DeviceDefaults
import app.overfit.domain.Language
import app.overfit.domain.WeightUnit
import io.ktor.client.HttpClient

/** Regions that use pounds for body weight. */
internal val LB_REGIONS = setOf("US", "LR", "MM")

internal fun defaultsFor(languageCode: String?, regionCode: String?) = DeviceDefaults(
    language = if (languageCode?.lowercase()?.startsWith("de") == true) Language.DE else Language.EN,
    unit = if (regionCode?.uppercase() in LB_REGIONS) WeightUnit.LB else WeightUnit.KG,
)

expect fun deviceDefaults(): DeviceDefaults

expect fun createHttpClient(): HttpClient

/** Android: closing the keyboard with Back leaves the field focused (blinking cursor), so focus is cleared then. iOS has no such Back. */
expect val clearFocusWhenKeyboardCloses: Boolean
