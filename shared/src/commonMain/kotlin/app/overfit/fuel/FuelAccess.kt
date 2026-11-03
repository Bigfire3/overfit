package app.overfit.fuel

import app.overfit.data.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where the Fuel/Spot backend lives. The URL is baked in at build time (CI variable `OVERFIT_BACKEND_URL`, empty = not
 * configured) and can be overridden by a hidden developer setting (tap the version in "About" seven times).
 */
class FuelAccess(
    private val kv: KeyValueStore,
    private val buildUrl: String,
    private val factory: (baseUrl: String) -> FuelService,
) {
    private val _override = MutableStateFlow(kv.get(OVERRIDE_KEY).orEmpty())

    /** The URL the app currently uses; empty = Fuel/Spot not available yet. */
    val url: StateFlow<String> get() = _urlFlow
    private val _urlFlow = MutableStateFlow(resolve())
    val overrideUrl: StateFlow<String> = _override.asStateFlow()

    private var cached: Pair<String, FuelService>? = null

    private fun resolve() = normalizeBackendUrl(_override.value).ifEmpty { normalizeBackendUrl(buildUrl) }

    /** The real service, or null when no backend URL is configured. */
    fun service(): FuelService? {
        val u = _urlFlow.value
        if (u.isEmpty()) return null
        cached?.takeIf { it.first == u }?.let { return it.second }
        return factory(u).also { cached = u to it }
    }

    fun setOverride(url: String) {
        val clean = normalizeBackendUrl(url)
        if (clean.isEmpty()) kv.remove(OVERRIDE_KEY) else kv.put(OVERRIDE_KEY, clean)
        _override.value = clean
        _urlFlow.value = resolve()
    }

    companion object {
        const val OVERRIDE_KEY = "overfit.dev.backendUrl"
    }
}

/** Trims, drops trailing slashes and anything that is not an http(s) URL (then: empty = not configured). */
fun normalizeBackendUrl(raw: String): String {
    val t = raw.trim().trimEnd('/')
    return if (t.startsWith("https://") || t.startsWith("http://")) t else ""
}
