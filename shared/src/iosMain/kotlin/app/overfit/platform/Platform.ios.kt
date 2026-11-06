package app.overfit.platform

import app.overfit.data.DeviceDefaults
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSLocale
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import platform.Foundation.preferredLanguages

actual fun deviceDefaults(): DeviceDefaults =
    defaultsFor(NSLocale.preferredLanguages.firstOrNull() as? String, NSLocale.currentLocale.countryCode)

actual fun createHttpClient(): HttpClient = HttpClient(Darwin)

actual val clearFocusWhenKeyboardCloses: Boolean = false
