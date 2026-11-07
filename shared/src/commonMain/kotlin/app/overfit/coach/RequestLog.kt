package app.overfit.coach

import app.overfit.ai.ProviderId
import app.overfit.data.db.AiRequestDao
import app.overfit.data.db.AiRequestEntity
import app.overfit.fuel.FuelService
import app.overfit.fuel.ServerRequest
import app.overfit.fuel.modelOfTier
import kotlinx.coroutines.CancellationException
import kotlin.random.Random

/** Where finished requests go (the database; a list in tests). */
fun interface AiRequestSink {
    suspend fun record(entry: AiRequestEntity)
}

fun AiRequestDao.asSink(): AiRequestSink = AiRequestSink { e ->
    // A credit request whose server entry was synced earlier keeps one row (unique serverId).
    val existing = e.serverId?.let { byServerId(it) }
    upsert(if (existing != null) e.copy(id = existing.id) else e)
}

/**
 * Wraps a transport and writes every request into the request log ("Abrechnung"): time, purpose, model, tokens, cost,
 * status, fallback. Purely local, so with an own key nothing reaches the Overfit backend (OwnKeyPrivacyTest). A failing
 * log write never breaks the request.
 */
class LoggingTransport(
    private val inner: CoachTransport,
    private val sink: AiRequestSink,
    private val clock: () -> Long,
) : CoachTransport {
    override val providerId: ProviderId get() = inner.providerId

    override suspend fun send(prompt: CoachPrompt, model: String, structured: Boolean): CoachReply {
        val started = clock()
        val requested = requestedModel(model)
        val reply = try {
            inner.send(prompt, model, structured)
        } catch (e: CancellationException) {
            throw e
        } catch (e: CoachException) {
            write(
                AiRequestEntity(
                    id = newLogId(), startedAt = started, durationMs = clock() - started, providerId = providerId.name,
                    purpose = prompt.effectivePurpose.wire, requestedModel = requested, status = STATUS_FAILED,
                    errorKind = e.serverCode ?: e.kind.name, serverId = e.serverRequestId,
                ),
            )
            throw e
        } catch (e: Exception) {
            write(
                AiRequestEntity(
                    id = newLogId(), startedAt = started, durationMs = clock() - started, providerId = providerId.name,
                    purpose = prompt.effectivePurpose.wire, requestedModel = requested, status = STATUS_FAILED,
                    errorKind = CoachErrorKind.NETWORK.name,
                ),
            )
            throw e
        }
        val served = reply.servedModel ?: requested
        write(
            AiRequestEntity(
                id = newLogId(), startedAt = started, durationMs = clock() - started, providerId = providerId.name,
                purpose = prompt.effectivePurpose.wire,
                requestedModel = reply.fallbackFrom ?: served,
                servedModel = served,
                status = STATUS_OK,
                fallbackReason = reply.fallbackReason,
                inputTokens = reply.usage?.input, outputTokens = reply.usage?.output,
                cacheReadTokens = reply.usage?.cacheRead, cacheWriteTokens = reply.usage?.cacheWrite,
                costMicro = if (providerId == ProviderId.FUEL) reply.costMicro ?: 0 else null,
                serverId = reply.serverRequestId,
            ),
        )
        return reply
    }

    /** Credit sends a tier; the log shows the tier's model. */
    private fun requestedModel(model: String): String =
        if (providerId == ProviderId.FUEL) modelOfCurrentTier(model) else model

    private suspend fun write(entry: AiRequestEntity) {
        try { sink.record(entry) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
    }

    companion object {
        const val STATUS_OK = "OK"
        const val STATUS_FAILED = "FAILED"
    }
}

/** Model of a credit tier today (Claude-only since 7.11.2026); unknown values stay as they are. */
fun modelOfCurrentTier(tier: String): String = when (tier) {
    app.overfit.ai.TIER_LITE -> "claude-haiku-5-5"
    app.overfit.ai.TIER_STANDARD -> "claude-sonnet-5-5"
    app.overfit.ai.TIER_PRO -> "claude-opus-5-5"
    else -> tier
}

private fun newLogId(): String = Random.nextBytes(12).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

/** Server log entry → local row (credit; also requests of other devices of the account). */
fun ServerRequest.toEntity(existingId: String?): AiRequestEntity = AiRequestEntity(
    id = existingId ?: "srv-$id",
    startedAt = ts * 1000,
    durationMs = durationMs,
    providerId = ProviderId.FUEL.name,
    purpose = purpose ?: AiPurpose.OTHER.wire,
    requestedModel = requestedModel.ifEmpty { modelOfTier(tier) ?: tier },
    servedModel = model,
    status = if (status == "ok") LoggingTransport.STATUS_OK else LoggingTransport.STATUS_FAILED,
    errorKind = errorCode,
    fallbackReason = fallbackReason ?: fallbackFromStatus?.let { "HTTP $it" },
    inputTokens = inputTokens, outputTokens = outputTokens, cacheReadTokens = cacheReadTokens, cacheWriteTokens = cacheWriteTokens,
    costMicro = chargedMicro,
    serverId = id,
)

/**
 * Pulls the server's request log (newest first, up to [maxPages] pages) into the local table. Entries already known
 * (same serverId) are updated, so a credit request appears once, with the server's numbers.
 */
suspend fun syncServerRequests(service: FuelService, dao: AiRequestDao, maxPages: Int = 4): Int {
    var before: Long? = null
    var n = 0
    repeat(maxPages) {
        val page = service.requests(before, 100)
        page.entries.forEach { r ->
            dao.upsert(r.toEntity(dao.byServerId(r.id)?.id))
            n++
        }
        before = page.next ?: return n
    }
    return n
}
