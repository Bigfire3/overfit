package app.overfit.platform

import app.overfit.data.DeviceDefaults
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.Locale

actual fun deviceDefaults(): DeviceDefaults =
    Locale.getDefault().let { defaultsFor(it.language, it.country) }

actual fun createHttpClient(): HttpClient = HttpClient(OkHttp)
