package app.overfit.coach

import app.overfit.ai.ProviderId
import app.overfit.data.InMemoryKeyValueStore
import app.overfit.data.db.AiRequestDao
import app.overfit.data.db.AiRequestEntity
import app.overfit.fuel.FuelAccount
import app.overfit.fuel.FuelConfig
import app.overfit.fuel.FuelMessageResult
import app.overfit.fuel.FuelService
import app.overfit.fuel.LedgerPage
import app.overfit.fuel.ServerRequest
import app.overfit.fuel.ServerRequestPage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Request log ("Abrechnung"): what is written per request, usage parsing, server sync, migration of the model choice. */
class RequestLogTest {
    private val prompt = CoachPrompt("STATIC", "DYNAMIC", "USER")

    private class ListSink : AiRequestSink {
        val entries = mutableListOf<AiRequestEntity>()
        override suspend fun record(entry: AiRequestEntity) { entries += entry }
    }

    private fun transport(id: ProviderId, answer: suspend () -> CoachReply) =
        FakeCoachTransport(id) { _, _ -> answer() }

    @Test
    fun ownKeyRequestIsLoggedWithTokensButNoCost() = runTest {
        val sink = ListSink()
        var now = 1_000L
        val t = LoggingTransport(transport(ProviderId.ANTHROPIC) { now += 2_500; CoachReply("{}", "{}", usage = TokenUsage(800, 120, 4000, 0)) }, sink) { now }
        t.send(prompt.copy(review = true), "claude-sonnet-5-5")
        val e = sink.entries.single()
        assertEquals("ANTHROPIC", e.providerId)
        assertEquals("review", e.purpose)
        assertEquals("claude-sonnet-5-5", e.requestedModel)
        assertEquals("claude-sonnet-5-5", e.servedModel)
        assertEquals(LoggingTransport.STATUS_OK, e.status)
        assertEquals(1_000L, e.startedAt)
        assertEquals(2_500L, e.durationMs)
        assertEquals(800L, e.inputTokens)
        assertEquals(120L, e.outputTokens)
        assertEquals(4000L, e.cacheReadTokens)
        assertNull(e.costMicro) // own key: no credit cost
        assertNull(e.serverId)
    }

    @Test
    fun creditRequestShowsModelOfTierCostFallbackAndServerId() = runTest {
        val sink = ListSink()
        val t = LoggingTransport(
            transport(ProviderId.FUEL) {
                CoachReply(
                    "{}", "{}", costMicro = 3_100, servedModel = "claude-haiku-5-5", fallbackFrom = "claude-sonnet-5-5",
                    usage = TokenUsage(500, 90), serverRequestId = 42, fallbackReason = "HTTP 529: Overloaded",
                )
            },
            sink,
        ) { 0 }
        t.send(prompt.copy(purpose = AiPurpose.AUTOFILL), "standard")
        val e = sink.entries.single()
        assertEquals("FUEL", e.providerId)
        assertEquals("autofill", e.purpose)
        assertEquals("claude-sonnet-5-5", e.requestedModel)
        assertEquals("claude-haiku-5-5", e.servedModel)
        assertEquals("HTTP 529: Overloaded", e.fallbackReason)
        assertEquals(3_100L, e.costMicro)
        assertEquals(42L, e.serverId)
    }

    @Test
    fun failedRequestsAreLoggedAndTheErrorStillReachesTheCaller() = runTest {
        val sink = ListSink()
        val t = LoggingTransport(
            transport(ProviderId.FUEL) { throw CoachException(CoachErrorKind.NO_FUEL, "x", serverRequestId = 7, serverCode = "insufficient_funds") },
            sink,
        ) { 0 }
        assertFailsWith<CoachException> { t.send(prompt, "lite") }
        val e = sink.entries.single()
        assertEquals(LoggingTransport.STATUS_FAILED, e.status)
        assertEquals("insufficient_funds", e.errorKind)
        assertEquals("claude-haiku-5-5", e.requestedModel)
        assertNull(e.servedModel)
        assertEquals(7L, e.serverId)
        assertEquals("chat", e.purpose)

        val own = LoggingTransport(transport(ProviderId.OPENAI) { throw CoachException(CoachErrorKind.AUTH) }, sink) { 0 }
        assertFailsWith<CoachException> { own.send(prompt, "gpt-5") }
        assertEquals(CoachErrorKind.AUTH.name, sink.entries.last().errorKind)
    }

    @Test
    fun aBrokenLogNeverBreaksTheRequest() = runTest {
        val t = LoggingTransport(transport(ProviderId.FUEL) { CoachReply("ok", "ok") }, { error("disk full") }) { 0 }
        assertEquals("ok", t.send(prompt, "standard").text)
    }

    @Test
    fun usageIsReadFromAnthropicAndOpenAiAnswers() {
        val a = anthropicReply(
            Json.parseToJsonElement(
                """{"content":[{"type":"text","text":"x"}],"stop_reason":"end_turn","usage":{"input_tokens":10,"output_tokens":5,"cache_read_input_tokens":300,"cache_creation_input_tokens":20}}""",
            ).jsonObject,
            "",
        )
        assertEquals(TokenUsage(10, 5, 300, 20), a.usage)
        assertNull(anthropicReply(Json.parseToJsonElement("""{"content":[]}""").jsonObject, "").usage)
        val o = ChatCompletionsCoach.chatReply(
            Json.parseToJsonElement(
                """{"choices":[{"message":{"content":"x"},"finish_reason":"stop"}],"usage":{"prompt_tokens":1000,"completion_tokens":50,"prompt_tokens_details":{"cached_tokens":600}}}""",
            ).jsonObject,
            "",
        )
        assertEquals(TokenUsage(400, 50, 600, 0), o.usage)
    }

    @Test
    fun purposeGoesToTheCreditBackend() {
        fun sent(p: CoachPrompt) = fuelBody(p, "lite", true)["overfit"]!!.jsonObject["purpose"]!!.jsonPrimitive.content
        assertEquals("review", sent(prompt.copy(review = true)))
        assertEquals("chat", sent(prompt))
        assertEquals("autofill", sent(prompt.copy(purpose = AiPurpose.AUTOFILL)))
        assertEquals(AiPurpose.OTHER, AiPurpose.of("whatever"))
        assertEquals(AiPurpose.PLAN, AiPurpose.of("plan"))
    }

    // ---- Server sync ----

    private class FakeDao : AiRequestDao {
        val rows = MutableStateFlow<Map<String, AiRequestEntity>>(emptyMap())
        override fun observeRecent(limit: Int): Flow<List<AiRequestEntity>> = error("unused")
        override suspend fun get(id: String) = rows.value[id]
        override suspend fun byServerId(serverId: Long) = rows.value.values.firstOrNull { it.serverId == serverId }
        override suspend fun upsert(entry: AiRequestEntity) { rows.value = rows.value + (entry.id to entry) }
        override suspend fun deleteOlderThan(beforeMs: Long) = Unit
        override suspend fun clear() { rows.value = emptyMap() }
    }

    private class PagedService(private val pages: List<ServerRequestPage>) : FuelService {
        val befores = mutableListOf<Long?>()
        override val isDemo = false
        override suspend fun requests(before: Long?, limit: Int): ServerRequestPage {
            befores += before
            return pages[befores.size - 1]
        }
        override suspend fun config(): FuelConfig = error("unused")
        override suspend fun account(): FuelAccount = error("unused")
        override suspend fun ledger(before: Long?, limit: Int): LedgerPage = error("unused")
        override suspend fun createRecoveryCode(): String = error("unused")
        override suspend fun restore(code: String): FuelAccount = error("unused")
        override suspend fun messages(bodyJson: String): FuelMessageResult = error("unused")
    }

    @Test
    fun syncMergesServerEntriesWithLocalOnesBySeverIdAndPages() = runTest {
        val dao = FakeDao()
        // Local entry of a credit request that the server also knows (id 5).
        dao.upsert(AiRequestEntity("local", 1_000, providerId = "FUEL", purpose = "review", requestedModel = "claude-sonnet-5-5", status = "OK", serverId = 5))
        val service = PagedService(
            listOf(
                ServerRequestPage(
                    listOf(
                        ServerRequest(6, ts = 2, purpose = "chat", tier = "lite", status = "failed", errorCode = "insufficient_funds", requestedModel = "claude-haiku-5-5"),
                        ServerRequest(5, ts = 1, purpose = "review", tier = "standard", status = "ok", requestedModel = "claude-sonnet-5-5", model = "claude-haiku-5-5", fallbackFromStatus = 529, inputTokens = 900, outputTokens = 100, chargedMicro = 2_000),
                    ),
                    next = 5,
                ),
                ServerRequestPage(listOf(ServerRequest(4, ts = 0, tier = "standard", status = "ok", requestedModel = "")), next = null),
            ),
        )
        assertEquals(3, syncServerRequests(service, dao))
        assertEquals(listOf(null, 5L), service.befores)
        val rows = dao.rows.value
        assertEquals(3, rows.size) // the local one was updated, not duplicated
        val merged = rows.getValue("local")
        assertEquals("claude-haiku-5-5", merged.servedModel)
        assertEquals("HTTP 529", merged.fallbackReason)
        assertEquals(2_000L, merged.costMicro)
        assertEquals(900L, merged.inputTokens)
        val failed = rows.getValue("srv-6")
        assertEquals("FAILED", failed.status)
        assertEquals("insufficient_funds", failed.errorKind)
        assertEquals(2_000L, failed.startedAt)
        val old = rows.getValue("srv-4")
        assertEquals("other", old.purpose) // old app version sent no purpose
    }

    // ---- Settings migration ----

    @Test
    fun storedProTierFromTheSonnetTimeBecomesStandardOnce() {
        val kv = InMemoryKeyValueStore()
        kv.put(CoachSettingsStore.KEY, """{"models":{"FUEL":"pro","ANTHROPIC":"claude-opus-5-5"}}""")
        val store = CoachSettingsStore(kv)
        assertEquals("standard", store.state.value.model(ProviderId.FUEL))
        assertEquals("claude-opus-5-5", store.state.value.model(ProviderId.ANTHROPIC)) // own keys untouched
        // A later choice of Opus stays.
        store.setModel(ProviderId.FUEL, "pro")
        assertEquals("pro", CoachSettingsStore(kv).state.value.model(ProviderId.FUEL))
        store.setModel(ProviderId.FUEL, "lite")
        assertEquals("lite", CoachSettingsStore(kv).state.value.model(ProviderId.FUEL))
        // A backup from before the switch is migrated on restore as well.
        store.replace(CoachSettings(models = mapOf("FUEL" to "pro")))
        assertEquals("standard", store.state.value.model(ProviderId.FUEL))
        // Fresh install: nothing to migrate.
        assertTrue(CoachSettingsStore(InMemoryKeyValueStore()).state.value.claudeTiers)
    }
}
