package app.overfit.fuel

import app.overfit.ai.FuelProvider
import app.overfit.coach.modelOfCurrentTier
import app.overfit.data.InMemoryKeyValueStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The server decides provider and model per tier: the app learns names from `/v1/config` and keeps the last config. */
class ModelNamesTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val configJson = """
        {"mode":"test","preset":"openai","tiers":{
          "standard":{"available":true,"provider":"openai","model":"gpt-5","displayName":"GPT-5","vendor":"OpenAI",
            "fallback":{"model":"gemini-3.6-flash","displayName":"Gemini 3.6 Flash","vendor":"Google"}},
          "lite":{"available":true,"provider":"workers-ai","model":"@cf/meta/llama-4-scout-17b-16e-instruct",
            "displayName":"Llama 4 Scout","vendor":"Cloudflare","fallback":null},
          "pro":{"available":false,"model":"o-pro","fallback":"standard"}},
         "modelNames":{"old-model-1":{"displayName":"Old Model","vendor":"Acme"}}}
    """.trimIndent()

    private class FakeFuel(var config: FuelConfig?) : FuelService {
        var calls = 0
        override val isDemo = false
        override suspend fun config(): FuelConfig { calls++; return config ?: throw FuelException("network") }
        override suspend fun account() = FuelAccount()
        override suspend fun ledger(before: Long?, limit: Int) = LedgerPage()
        override suspend fun createRecoveryCode() = error("unused")
        override suspend fun restore(code: String) = error("unused")
        override suspend fun messages(bodyJson: String) = FuelMessageResult(JsonObject(emptyMap()), 0, 0)
    }

    @AfterTest
    fun reset() = ModelNames.clear()

    @Test
    fun parsesNewFieldsAndBothFallbackShapes() {
        val cfg = json.decodeFromString(FuelConfig.serializer(), configJson)
        assertEquals("openai", cfg.preset)
        assertEquals("openai", cfg.tiers["standard"]!!.provider)
        assertEquals("gemini-3.6-flash", cfg.tiers["standard"]!!.fallbackModel?.model)
        assertNull(cfg.tiers["lite"]!!.fallbackModel)
        assertNull(cfg.tiers["pro"]!!.fallbackModel) // old backend: tier name as string
        assertEquals("Old Model", cfg.modelNames["old-model-1"]?.displayName)
    }

    @Test
    fun modelLabelPrefersServerNamesThenTableThenId() {
        assertEquals("gpt-5", modelLabel("gpt-5"))
        assertEquals("Claude Sonnet 5.5 (Anthropic)", modelLabel("claude-sonnet-5-5"))
        ModelNames.register(json.decodeFromString(FuelConfig.serializer(), configJson))
        assertEquals("GPT-5 (OpenAI)", modelLabel("gpt-5"))
        assertEquals("Llama 4 Scout", modelShortName("@cf/meta/llama-4-scout-17b-16e-instruct"))
        assertEquals("Old Model (Acme)", modelLabel("old-model-1"))
        assertEquals("gpt-5", modelOfCurrentTier("standard"))
        ModelNames.register(FuelMessageResult(JsonObject(emptyMap()), 0, 0, servedModel = "mistral-x", servedModelName = "Mistral X", servedVendor = "Mistral"))
        assertEquals("Mistral X (Mistral)", modelLabel("mistral-x"))
    }

    @Test
    fun configFallsBackToLastStoredConfigWhenServerIsUnreachable() = runTest {
        val kv = InMemoryKeyValueStore()
        val loaded = json.decodeFromString(FuelConfig.serializer(), configJson)
        val online = FakeFuel(loaded)
        assertEquals(loaded, FuelProvider(kv) { online }.config())
        assertNotNull(kv.get(FuelProvider.CONFIG_KEY))

        // New process, server down: the stored config answers and its names are known before any request.
        ModelNames.clear()
        val offline = FakeFuel(null)
        val provider = FuelProvider(kv) { offline }
        assertEquals("GPT-5 (OpenAI)", modelLabel("gpt-5"))
        val cfg = provider.config()
        assertEquals(1, offline.calls)
        assertEquals("gpt-5", cfg?.tiers?.get("standard")?.model)
        assertEquals("openai", cfg?.preset)

        // "Delete all data" drops it.
        provider.resetAll()
        assertNull(kv.get(FuelProvider.CONFIG_KEY))
        assertNull(provider.config())
    }
}
