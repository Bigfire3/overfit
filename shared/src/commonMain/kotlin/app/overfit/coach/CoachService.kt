package app.overfit.coach

import app.overfit.ai.ProviderId
import app.overfit.data.ExerciseRepository
import app.overfit.data.TemplateRepository
import app.overfit.data.db.AiStatus
import app.overfit.data.db.CoachDao
import app.overfit.data.db.CoachMemoryEntity
import app.overfit.data.db.CoachReviewEntity
import app.overfit.domain.Exercise
import app.overfit.domain.Language
import app.overfit.domain.Template
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** How the coach reaches an AI right now. */
sealed interface CoachAccess {
    data object None : CoachAccess
    data class Ready(val providerId: ProviderId) : CoachAccess
}

/** Loads a body photo (file name in the photo directory) downscaled for the coach (≈1500 px, JPEG). Null if missing. */
fun interface CoachPhotoLoader {
    suspend fun load(path: String): ByteArray?
}

/** Profile block and app language for the next request (from the profile store, always current). */
data class CoachProfile(val block: String, val language: Language)

/**
 * The coach: builds requests, sends them through the current provider, parses and stores the answer, keeps the
 * queue. Every request is a row in `coach_review`; it stays QUEUED while there is no network (or a timeout / rate
 * limit / server error happened) and is sent again on [processQueue] (app start, app back in the foreground, new
 * request, "Try again"). Non-recoverable problems (no access, invalid key, no Fuel left, invalid answer twice) end in
 * FAILED and wait for "Try again".
 *
 * All work runs in [scope] (the app scope), so leaving a screen never cancels a request.
 */
class CoachService(
    private val dao: CoachDao,
    private val templates: TemplateRepository,
    private val exercises: ExerciseRepository,
    val settings: CoachSettingsStore,
    private val access: () -> CoachAccess,
    /** Provider chosen in the profile (also when it is not usable right now). */
    private val currentProvider: () -> ProviderId = { ProviderId.ANTHROPIC },
    private val transport: (ProviderId) -> CoachTransport,
    private val profile: () -> CoachProfile,
    private val defaultInstructions: (Language) -> String,
    private val scope: CoroutineScope,
    private val photos: CoachPhotoLoader = CoachPhotoLoader { null },
    private val clock: () -> Long,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    private val mutex = Mutex()

    val reviews: Flow<List<ReviewItem>> = dao.observeAll().map { list -> list.map { it.toItem() } }
    fun review(id: String): Flow<ReviewItem?> = dao.observe(id).map { it?.toItem() }
    fun reviewForSession(sessionId: String): Flow<ReviewItem?> = dao.observeLatestForSession(sessionId).map { it?.toItem() }
    val memory: Flow<CoachMemoryEntity?> = dao.observeMemory()
    /** Last known memory text, for synchronous readers (chat sync). */
    var memoryText: String? = null
    val memoryVersions: Flow<List<CoachMemoryEntity>> = dao.observeMemoryVersions()
    /** Newest finished workout (for "Review last workout"). */
    val latestFinished = dao.observeLatestFinished()

    fun access(): CoachAccess = access.invoke()

    /** Provider the next request goes to (for the settings screen). */
    fun providerId(): ProviderId = (access() as? CoachAccess.Ready)?.providerId ?: currentProvider()

    /** Runs [block] in the app scope (UI actions that must not be cancelled by leaving the screen). */
    fun launch(block: suspend () -> Unit): Job = scope.launch { block() }

    /** Non-suspending entry for the UI: queue a session review and send it in the app scope. */
    fun startSessionReview(sessionId: String): Job = scope.launch { requestSessionReview(sessionId) }

    /** Non-suspending entry for the UI; the id is known right away so the screen can observe it. */
    fun startProgressReview(): String {
        val id = newId()
        scope.launch {
            dao.upsert(newRow(id, null, ReviewKind.PROGRESS))
            sendPending()
        }
        return id
    }

    /** Name of the workout and its template (demo review). */
    suspend fun sessionAndTemplate(sessionId: String): Pair<String, Template?> {
        val s = dao.session(sessionId)?.session ?: return "" to null
        return s.name to s.templateId?.let { templates.get(it) }
    }

    // --- Requests ------------------------------------------------------------------------------

    /**
     * Queues a review of [sessionId] and starts sending. If the workout already has a review that is done or on its
     * way, that one is returned instead (no double costs).
     */
    suspend fun requestSessionReview(sessionId: String): String {
        dao.latestForSession(sessionId)?.let { existing ->
            if (existing.status != ReviewStatus.FAILED) return existing.id
            retry(existing.id)
            return existing.id
        }
        val id = newId()
        dao.upsert(newRow(id, sessionId, ReviewKind.SESSION))
        dao.setAiStatus(sessionId, AiStatus.QUEUED)
        processQueue()
        return id
    }

    /** "Assess progress": coach assessment of the last weeks. */
    suspend fun requestProgressReview(): String {
        val id = newId()
        dao.upsert(newRow(id, null, ReviewKind.PROGRESS))
        processQueue()
        return id
    }

    fun retry(id: String) {
        scope.launch {
            val row = dao.get(id) ?: return@launch
            dao.upsert(row.copy(status = ReviewStatus.QUEUED, errorKind = null, errorMessage = null, updatedAt = clock()))
            row.sessionId?.let { dao.setAiStatus(it, AiStatus.QUEUED) }
            sendPending()
        }
    }

    suspend fun delete(id: String) = dao.delete(id)

    /** Sends everything that is queued; returns immediately, the work runs in the app scope. */
    fun processQueue(): Job = scope.launch {
        // A failure here (e.g. database closed) must not crash the app; the queue is tried again next time.
        try { sendPending() } catch (e: CancellationException) { throw e } catch (_: Exception) { }
    }

    /** Same as [processQueue] but suspends until done (tests). */
    suspend fun sendPending() = mutex.withLock {
        for (row in dao.pending()) {
            val stop = sendOne(row)
            if (stop) break
        }
    }

    /** Sends one queued request. Returns true if the rest of the queue should wait (no network etc.). */
    private suspend fun sendOne(row: CoachReviewEntity): Boolean {
        val access = access()
        if (access !is CoachAccess.Ready) {
            fail(row, CoachErrorKind.NO_ACCESS, null)
            return false
        }
        val provider = access.providerId
        val model = settings.state.value.model(provider)
        val sending = row.copy(status = ReviewStatus.SENDING, providerId = provider.name, model = model, attempts = row.attempts + 1, updatedAt = clock())
        dao.upsert(sending)
        row.sessionId?.let { dao.setAiStatus(it, AiStatus.SENT) }
        return try {
            val prompt = buildPrompt(row)
            val (reply, parsed) = exchange(transport(provider), prompt, model)
            complete(sending, reply, parsed)
            false
        } catch (e: CancellationException) {
            // Scope ends: the row stays SENDING and counts as pending, so it is sent again on the next start.
            throw e
        } catch (e: CoachException) {
            if (e.kind.retryable) {
                row.sessionId?.let { dao.setAiStatus(it, AiStatus.QUEUED) }
                dao.upsert(sending.copy(status = ReviewStatus.QUEUED, errorKind = e.kind.name, errorMessage = e.message, updatedAt = clock()))
                true
            } else {
                fail(sending, e.kind, e.message)
                false
            }
        } catch (e: InvalidAnswer) {
            fail(sending, CoachErrorKind.INVALID_ANSWER, e.reply?.text?.take(500), raw = e.reply?.raw)
            false
        } catch (e: Exception) {
            fail(sending, CoachErrorKind.SERVER, e.message)
            false
        }
    }

    /**
     * One request plus at most one repeat: an answer that does not parse (or was cut off / declined) is asked again
     * once; a 400 with structured output is repeated without it (some models via OpenRouter do not support it).
     */
    private suspend fun exchange(transport: CoachTransport, prompt: CoachPrompt, model: String): Pair<CoachReply, CoachReview> {
        var structured = true
        var lastReply: CoachReply? = null
        repeat(2) { attempt ->
            val reply = try {
                transport.send(prompt, model, structured)
            } catch (e: CoachException) {
                if (e.kind == CoachErrorKind.BAD_REQUEST && structured && attempt == 0) {
                    structured = false
                    return@repeat
                }
                throw e
            }
            lastReply = reply
            val parsed = if (reply.refused) null else (CoachParser.parse(reply.text) as? ParseResult.Ok)?.review
            if (parsed != null) return reply to parsed
        }
        throw InvalidAnswer(lastReply)
    }

    private class InvalidAnswer(val reply: CoachReply?) : Exception("invalid_answer")

    private suspend fun complete(row: CoachReviewEntity, reply: CoachReply, review: CoachReview) {
        val now = clock()
        val json = CoachParser.extractJsonObject(reply.text)
        // Session status first: whoever sees the review DONE also sees the session DONE.
        row.sessionId?.let { dao.setAiStatus(it, AiStatus.DONE) }
        dao.upsert(
            row.copy(
                status = ReviewStatus.DONE,
                updatedAt = now,
                errorKind = null,
                errorMessage = null,
                rawResponse = reply.raw,
                reviewJson = json,
                planStatus = if (review.nextSession != null) PlanStatus.OPEN else PlanStatus.NONE,
                costMicro = reply.costMicro,
                balanceMicro = reply.balanceMicro,
            ),
        )
        val memory = review.memory?.trim()
        if (!memory.isNullOrEmpty() && memory != dao.currentMemory()?.text) {
            dao.insertMemory(CoachMemoryEntity(text = memory, createdAt = now, source = SOURCE_COACH, reviewId = row.id))
        }
    }

    private suspend fun fail(row: CoachReviewEntity, kind: CoachErrorKind, message: String?, raw: String? = row.rawResponse) {
        row.sessionId?.let { dao.setAiStatus(it, AiStatus.FAILED) }
        dao.upsert(row.copy(status = ReviewStatus.FAILED, errorKind = kind.name, errorMessage = message, rawResponse = raw, updatedAt = clock()))
    }

    // --- Request building ----------------------------------------------------------------------

    suspend fun buildContext(kind: ReviewKind, sessionId: String?): CoachContext {
        val p = profile()
        val s = settings.state.value
        val now = clock()
        val current = sessionId?.let { dao.finishedOrAny(it) }
        val template = current?.templateId?.let { templates.get(it) }
        val same = current?.templateId?.let { dao.finishedOfTemplate(it, current.id, SAME_TEMPLATE).map { r -> r.toRecord() } }.orEmpty()
        val lastOverall = current?.let { c -> dao.latestFinishedExcept(c.id)?.toRecord()?.takeIf { o -> same.none { it.id == o.id } } }
        val recent = dao.finishedSince(now - RECENT_WEEKS * WEEK_MS, RECENT_LIMIT).map { it.toRecord() }
        val used = dao.usedExerciseIds().toSet()
        val list = exercises.getActive()
            .filter { it.id.startsWith("wg:") || it.isCustom || it.id in used }
            .map { it.id to it.name }
        return CoachContext(
            kind = kind,
            language = p.language,
            instructions = s.instructions ?: defaultInstructions(p.language),
            profileBlock = p.block,
            memory = dao.currentMemory()?.text,
            exerciseList = list,
            current = current,
            template = template,
            sameTemplate = same,
            lastOverall = lastOverall,
            recent = if (kind == ReviewKind.PROGRESS) recent else recent.filter { it.id != current?.id },
            timeZone = timeZone(),
        )
    }

    private suspend fun CoachDao.finishedOrAny(id: String): SessionRecord? = session(id)?.toRecord()

    private suspend fun buildPrompt(row: CoachReviewEntity): CoachPrompt {
        var ctx = buildContext(row.kind, row.sessionId)
        val images = mutableListOf<CoachImage>()
        if (settings.state.value.sendPhotos) {
            val latest = when (row.kind) {
                ReviewKind.SESSION -> ctx.current?.let { c -> dao.firstPhotoOf(c.id) }?.let { it.startedAt to it.fileName }
                ReviewKind.PROGRESS -> dao.latestPhotoBefore(clock())?.let { it.startedAt to it.fileName }
            }
            if (latest != null) photos.load(latest.second)?.let { images += CoachImage(dateOf(latest.first), it) }
            if (images.isNotEmpty()) {
                val compare = dao.latestPhotoBefore(latest!!.first - COMPARE_MS)
                val bytes = compare?.fileName?.let { photos.load(it) }
                if (compare != null && bytes != null) {
                    images += CoachImage(dateOf(compare.startedAt), bytes)
                    ctx = ctx.copy(comparisonPhotoDate = dateOf(compare.startedAt))
                }
                ctx = ctx.copy(photoToday = true)
            }
        }
        return CoachPromptBuilder.build(ctx, images)
    }

    private fun dateOf(ms: Long): String =
        Instant.fromEpochMilliseconds(ms).toLocalDateTime(timeZone()).date.toString()

    // --- Plan ----------------------------------------------------------------------------------

    /** Diff of the review's plan against the current template (null without plan or template). */
    suspend fun planDiff(item: ReviewItem): Pair<Template, PlanDiff>? {
        val plan = item.review?.nextSession ?: return null
        val templateId = item.sessionId?.let { dao.finishedOrAny(it) }?.templateId ?: return null
        val template = templates.get(templateId) ?: return null
        val lookup = lookup(plan)
        return template to PlanDiffs.diff(template, plan) { lookup[it] }
    }

    /** Takes over the changes with the given keys; the template is updated. */
    suspend fun acceptPlan(reviewId: String, keys: Set<String>) {
        val row = dao.get(reviewId) ?: return
        val (template, diff) = planDiff(row.toItem()) ?: return
        val valid = keys.filter { k -> diff.changes.any { it.key == k } }.toSet()
        if (valid.isNotEmpty()) templates.save(PlanDiffs.apply(template, diff, valid))
        val status = when {
            valid.isEmpty() -> PlanStatus.REJECTED
            valid.size == diff.changes.size -> PlanStatus.ACCEPTED
            else -> PlanStatus.PARTIAL
        }
        dao.upsert(row.copy(planStatus = status, acceptedChanges = valid.joinToString(","), updatedAt = clock()))
    }

    suspend fun rejectPlan(reviewId: String) {
        val row = dao.get(reviewId) ?: return
        dao.upsert(row.copy(planStatus = PlanStatus.REJECTED, updatedAt = clock()))
    }

    /** Free workout: the plan becomes a new template. Returns its id. */
    suspend fun saveAsTemplate(reviewId: String, name: (String) -> String): Long? {
        val row = dao.get(reviewId) ?: return null
        val sessionName = row.sessionId?.let { dao.session(it)?.session?.name } ?: "Overfit"
        val plan = row.toItem().review?.nextSession ?: return null
        val lookup = lookup(plan)
        val template = PlanDiffs.toTemplate(name(sessionName), plan) { lookup[it] }
        if (template.exercises.isEmpty()) return null
        val id = templates.save(template)
        dao.upsert(row.copy(planStatus = PlanStatus.ACCEPTED, updatedAt = clock()))
        return id
    }

    private suspend fun lookup(plan: PlanSuggestion): Map<String, Exercise> =
        exercises.getAll(plan.exercises.map { it.exerciseId }.distinct()).associateBy { it.id }

    // --- Memory --------------------------------------------------------------------------------

    suspend fun saveMemory(text: String) {
        val trimmed = text.trim()
        if (trimmed == dao.currentMemory()?.text) return
        dao.insertMemory(CoachMemoryEntity(text = trimmed, createdAt = clock(), source = SOURCE_USER, reviewId = null))
    }

    /** Deletes the memory with all versions. */
    suspend fun clearMemory() = dao.deleteMemory()

    /** Backup: all reviews and memory versions. */
    suspend fun exportAll(): Pair<List<CoachReviewEntity>, List<CoachMemoryEntity>> =
        dao.allReviews() to dao.allMemory()

    /** Backup import: replaces reviews and memory. */
    suspend fun importAll(reviews: List<CoachReviewEntity>, memory: List<CoachMemoryEntity>) {
        dao.deleteAll()
        dao.deleteMemory()
        reviews.forEach { dao.upsert(it) }
        memory.forEach { dao.insertMemory(it) }
    }

    /** "Delete all data". */
    suspend fun resetAll() {
        dao.deleteAll()
        dao.deleteMemory()
        settings.reset()
    }

    private fun newRow(id: String, sessionId: String?, kind: ReviewKind): CoachReviewEntity {
        val now = clock()
        return CoachReviewEntity(
            id = id, sessionId = sessionId, kind = kind, status = ReviewStatus.QUEUED, createdAt = now, updatedAt = now,
            providerId = null, model = null, attempts = 0, errorKind = null, errorMessage = null, rawResponse = null,
            reviewJson = null, planStatus = PlanStatus.NONE, acceptedChanges = null, costMicro = null, balanceMicro = null,
            withPhotos = settings.state.value.sendPhotos,
        )
    }

    companion object {
        const val SOURCE_COACH = "COACH"
        const val SOURCE_USER = "USER"
        private const val SAME_TEMPLATE = 4
        private const val RECENT_WEEKS = 8
        private const val RECENT_LIMIT = 40
        private const val WEEK_MS = 7L * 24 * 3600 * 1000
        private const val COMPARE_MS = 28L * 24 * 3600 * 1000

        @OptIn(ExperimentalUuidApi::class)
        private fun newId() = Uuid.random().toString()
    }
}
