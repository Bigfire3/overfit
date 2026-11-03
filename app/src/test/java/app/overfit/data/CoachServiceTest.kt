package app.overfit.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.overfit.ai.ProviderId
import app.overfit.coach.CoachAccess
import app.overfit.coach.CoachErrorKind
import app.overfit.coach.CoachException
import app.overfit.coach.CoachProfile
import app.overfit.coach.CoachReply
import app.overfit.coach.CoachService
import app.overfit.coach.FakeCoachTransport
import app.overfit.coach.PlanStatus
import app.overfit.coach.ReviewKind
import app.overfit.coach.ReviewStatus
import app.overfit.data.db.AiStatus
import app.overfit.data.db.OverfitDatabase
import app.overfit.domain.Language
import app.overfit.domain.PlannedSet
import app.overfit.domain.Template
import app.overfit.domain.TemplateExercise
import app.overfit.domain.WeightUnit
import app.overfit.domain.Workout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.toLocalDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.atomic.AtomicLong

@RunWith(RobolectricTestRunner::class)
class CoachServiceTest {
    private lateinit var db: OverfitDatabase
    private lateinit var data: AppData
    private lateinit var scope: CoroutineScope
    private lateinit var coach: CoachService
    private val kv = InMemoryKeyValueStore()
    private val store = AppStore(kv, InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG))
    private val time = AtomicLong(10_000_000L)
    private val dao get() = db.coachDao()

    private var failure: Exception? = null
    private val structuredFlags = mutableListOf<Boolean>()
    private var answers: MutableList<String> = mutableListOf()
    private lateinit var fake: FakeCoachTransport
    private var templateId = 0L
    private var sessionId = "w1"

    private val validJson = """{"rating":8,"summary":"Starkes Training","positives":["Sauber"],"improvements":[],"tips":["Mehr schlafen"],"photo_feedback":null,"next_session":{"exercises":[{"exercise_id":"wg:bench-press","is_new":false,"rest_sec":90,"sets":[{"weight_kg":62.5,"reps":8,"duration_sec":null},{"weight_kg":62.5,"reps":8,"duration_sec":null}],"reason":"Alle Sätze geschafft"}],"note":null},"memory":"Ziel: Kraft."}"""

    private val twoChangeJson = """{"rating":7,"summary":"Ok","positives":[],"improvements":[],"tips":[],"photo_feedback":null,"next_session":{"exercises":[{"exercise_id":"wg:bench-press","is_new":false,"rest_sec":90,"sets":[{"weight_kg":62.5,"reps":8,"duration_sec":null},{"weight_kg":62.5,"reps":8,"duration_sec":null}],"reason":null},{"exercise_id":"wg:arnold-press","is_new":false,"rest_sec":60,"sets":[{"weight_kg":25.0,"reps":8,"duration_sec":null},{"weight_kg":25.0,"reps":8,"duration_sec":null}],"reason":null}],"note":null},"memory":null}"""

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext())
            .allowMainThreadQueries()
            .build()
        data = AppData(db, kv, readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() })
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        store.connectAi(ProviderId.ANTHROPIC, "sk-test")
        answers = mutableListOf(validJson)
        fake = FakeCoachTransport { _, structured ->
            structuredFlags += structured
            failure?.let { throw it }
            val text = if (answers.size > 1) answers.removeAt(0) else answers.first()
            CoachReply(text = text, raw = "RAW:$text")
        }
        coach = CoachService(
            dao = dao,
            templates = data.templates,
            exercises = data.exercises,
            settings = data.coachSettings,
            access = { if (store.state.value.hasApiKey) CoachAccess.Ready(ProviderId.ANTHROPIC) else CoachAccess.None },
            transport = { fake },
            profile = { CoachProfile("Name: Fabi", Language.DE) },
            defaultInstructions = { "Du bist Coach." },
            scope = scope,
            clock = { time.incrementAndGet() },
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    /** Seeds the catalogue, template "Push" (bench 2×60 kg × 8, rest 60 s) and a finished workout from it. */
    private suspend fun seedFinishedWorkout(withArnold: Boolean = false) {
        data.seedCatalog()
        val bench = requireNotNull(data.exercises.get("wg:bench-press"))
        val exercises = mutableListOf(TemplateExercise(bench, 60, null, List(2) { PlannedSet(60.0, 8) }))
        if (withArnold) {
            val arnold = requireNotNull(data.exercises.get("wg:arnold-press"))
            exercises += TemplateExercise(arnold, 60, null, List(2) { PlannedSet(20.0, 8) })
        }
        templateId = data.templates.save(Template(0, "Push", null, exercises))
        val template = requireNotNull(data.templates.get(templateId))
        var w = Workout.fromTemplate(template, time.get(), id = sessionId)
        w.exercises.forEach { e -> e.sets.forEach { w = w.completeSet(it.id, time.get() + 1) } }
        data.workouts.finish(w, time.get() + 60_000)
    }

    private suspend fun aiStatus() = dao.session(sessionId)!!.session.aiStatus

    private suspend fun awaitStatus(id: String, status: ReviewStatus) = withTimeout(10_000) {
        while (dao.get(id)?.status != status) delay(20)
    }

    private suspend fun benchSets() = requireNotNull(data.templates.get(templateId)).exercises.first { it.exercise.id == "wg:bench-press" }

    @Test
    fun happyPath() = runBlocking {
        seedFinishedWorkout()
        val id = coach.requestSessionReview(sessionId)
        coach.sendPending()

        val row = requireNotNull(dao.get(id))
        assertEquals(ReviewStatus.DONE, row.status)
        assertEquals(ReviewKind.SESSION, row.kind)
        assertEquals(PlanStatus.OPEN, row.planStatus)
        assertEquals(AiStatus.DONE, aiStatus())
        assertNotNull(row.rawResponse)
        assertTrue(row.rawResponse!!.startsWith("RAW:"))
        assertEquals(8, coach.review(id).first()!!.review!!.rating)

        val memory = requireNotNull(dao.currentMemory())
        assertEquals("Ziel: Kraft.", memory.text)
        assertEquals(CoachService.SOURCE_COACH, memory.source)
        assertEquals(id, memory.reviewId)

        assertEquals(1, fake.prompts.size)
        assertTrue(fake.prompts.single().user.contains("Bench"))
        assertTrue(fake.prompts.single().staticSystem.contains("wg:bench-press"))
    }

    @Test
    fun requestingTwiceReturnsSameReviewAndCallsTransportOnce() = runBlocking {
        seedFinishedWorkout()
        val first = coach.requestSessionReview(sessionId)
        coach.sendPending()
        val second = coach.requestSessionReview(sessionId)
        coach.sendPending()
        assertEquals(first, second)
        assertEquals(1, fake.prompts.size)
    }

    @Test
    fun networkErrorQueuesAndCatchUpSendsLater() = runBlocking {
        seedFinishedWorkout()
        failure = CoachException(CoachErrorKind.NETWORK)
        val id = coach.requestSessionReview(sessionId)
        coach.sendPending()

        val queued = requireNotNull(dao.get(id))
        assertEquals(ReviewStatus.QUEUED, queued.status)
        assertEquals("NETWORK", queued.errorKind)
        assertEquals(AiStatus.QUEUED, aiStatus())

        failure = null
        coach.sendPending()
        val done = requireNotNull(dao.get(id))
        assertEquals(ReviewStatus.DONE, done.status)
        assertNull(done.errorKind)
        assertEquals(AiStatus.DONE, aiStatus())
    }

    @Test
    fun authErrorFailsAndRetryCanSucceed() = runBlocking {
        seedFinishedWorkout()
        failure = CoachException(CoachErrorKind.AUTH)
        val id = coach.requestSessionReview(sessionId)
        coach.sendPending()
        val failed = requireNotNull(dao.get(id))
        assertEquals(ReviewStatus.FAILED, failed.status)
        assertEquals("AUTH", failed.errorKind)
        assertEquals(AiStatus.FAILED, aiStatus())

        failure = null
        coach.retry(id)
        awaitStatus(id, ReviewStatus.DONE)
        assertEquals(AiStatus.DONE, aiStatus())
    }

    @Test
    fun invalidAnswerTwiceFailsWithRawResponse() = runBlocking {
        seedFinishedWorkout()
        answers = mutableListOf("kein json")
        val id = coach.requestSessionReview(sessionId)
        coach.sendPending()
        val row = requireNotNull(dao.get(id))
        assertEquals(ReviewStatus.FAILED, row.status)
        assertEquals("INVALID_ANSWER", row.errorKind)
        assertEquals("RAW:kein json", row.rawResponse)
        assertEquals(2, fake.prompts.size)
    }

    @Test
    fun invalidThenValidAnswerSucceeds() = runBlocking {
        seedFinishedWorkout()
        answers = mutableListOf("kein json", validJson)
        val id = coach.requestSessionReview(sessionId)
        coach.sendPending()
        assertEquals(ReviewStatus.DONE, dao.get(id)!!.status)
        assertEquals(2, fake.prompts.size)
    }

    @Test
    fun badRequestIsRepeatedWithoutStructuredOutput() = runBlocking {
        seedFinishedWorkout()
        var calls = 0
        fake = FakeCoachTransport { _, structured ->
            structuredFlags += structured
            if (calls++ == 0) throw CoachException(CoachErrorKind.BAD_REQUEST)
            CoachReply(validJson, validJson)
        }
        val id = coach.requestSessionReview(sessionId)
        coach.sendPending()
        assertEquals(ReviewStatus.DONE, dao.get(id)!!.status)
        assertEquals(listOf(true, false), structuredFlags)
    }

    @Test
    fun noAccessFailsWithNoAccess() = runBlocking {
        seedFinishedWorkout()
        store.disconnectAi()
        val id = coach.requestSessionReview(sessionId)
        coach.sendPending()
        val row = requireNotNull(dao.get(id))
        assertEquals(ReviewStatus.FAILED, row.status)
        assertEquals("NO_ACCESS", row.errorKind)
        assertEquals(AiStatus.FAILED, aiStatus())
        assertTrue(fake.prompts.isEmpty())
    }

    @Test
    fun acceptPlanWithAllKeysUpdatesTemplate() = runBlocking {
        seedFinishedWorkout()
        val id = coach.requestSessionReview(sessionId)
        coach.sendPending()
        val (_, diff) = requireNotNull(coach.planDiff(coach.review(id).first()!!))
        coach.acceptPlan(id, diff.changes.map { it.key }.toSet())

        val bench = benchSets()
        assertEquals(90, bench.restSec)
        assertEquals(listOf(62.5, 62.5), bench.sets.map { it.weightKg })
        assertEquals(PlanStatus.ACCEPTED, dao.get(id)!!.planStatus)
    }

    @Test
    fun rejectPlanLeavesTemplateUnchanged() = runBlocking {
        seedFinishedWorkout()
        val id = coach.requestSessionReview(sessionId)
        coach.sendPending()
        coach.rejectPlan(id)

        val bench = benchSets()
        assertEquals(60, bench.restSec)
        assertEquals(listOf(60.0, 60.0), bench.sets.map { it.weightKg })
        assertEquals(PlanStatus.REJECTED, dao.get(id)!!.planStatus)
    }

    @Test
    fun acceptingSubsetOfChangesIsPartial() = runBlocking {
        seedFinishedWorkout(withArnold = true)
        answers = mutableListOf(twoChangeJson)
        val id = coach.requestSessionReview(sessionId)
        coach.sendPending()
        val (_, diff) = requireNotNull(coach.planDiff(coach.review(id).first()!!))
        assertEquals(2, diff.changes.size)
        val benchKey = diff.changes.first { it.exercise.id == "wg:bench-press" }.key
        coach.acceptPlan(id, setOf(benchKey))

        assertEquals(PlanStatus.PARTIAL, dao.get(id)!!.planStatus)
        assertEquals(62.5, benchSets().sets.first().weightKg)
        val arnold = requireNotNull(data.templates.get(templateId)).exercises.first { it.exercise.id == "wg:arnold-press" }
        assertEquals(20.0, arnold.sets.first().weightKg)
    }

    @Test
    fun progressReviewIsDone() = runBlocking {
        seedFinishedWorkout()
        val id = coach.requestProgressReview()
        coach.sendPending()
        val row = requireNotNull(dao.get(id))
        assertEquals(ReviewStatus.DONE, row.status)
        assertEquals(ReviewKind.PROGRESS, row.kind)
        assertNull(row.sessionId)
        assertTrue(fake.prompts.single().user.contains("Assess the progress"))
    }

    @Test
    fun startProgressReviewReturnsIdAndCompletes() = runBlocking {
        seedFinishedWorkout()
        val id = coach.startProgressReview()
        awaitStatus(id, ReviewStatus.DONE)
        assertEquals(ReviewKind.PROGRESS, dao.get(id)!!.kind)
    }

    @Test
    fun memoryVersionsFlow() = runBlocking {
        assertNull(dao.currentMemory())
        coach.saveMemory("  Erste Version  ")
        assertEquals("Erste Version", dao.currentMemory()!!.text)
        assertEquals(CoachService.SOURCE_USER, dao.currentMemory()!!.source)
        coach.saveMemory("Erste Version") // unchanged: no new version
        assertEquals(1, coach.memoryVersions.first().size)
        coach.saveMemory("Zweite Version")
        val versions = coach.memoryVersions.first()
        assertEquals(listOf("Zweite Version", "Erste Version"), versions.map { it.text })
        assertEquals("Zweite Version", coach.memory.first()!!.text)
        coach.clearMemory()
        assertNull(coach.memory.first())
        assertTrue(coach.memoryVersions.first().isEmpty())
    }

    @Test
    fun backupContributorRoundTrip() = runBlocking {
        seedFinishedWorkout()
        coach.requestSessionReview(sessionId)
        coach.sendPending()
        coach.saveMemory("Von Hand")
        coach.settings.setSendPhotos(true)
        val backup = app.overfit.ui.coach.CoachBackup(coach)
        val exported = requireNotNull(backup.export())

        data.resetAll()
        assertTrue(coach.reviews.first().isEmpty())
        assertFalse(coach.settings.state.value.sendPhotos)

        backup.import(exported)
        val reviews = coach.reviews.first()
        assertEquals(1, reviews.size)
        assertEquals(8, reviews.single().review?.rating)
        assertEquals("Von Hand", dao.currentMemory()!!.text)
        assertEquals(2, coach.memoryVersions.first().size)
        assertTrue(coach.settings.state.value.sendPhotos)

        backup.import(null) // backup without coach data → coach data empty
        assertTrue(coach.reviews.first().isEmpty())
        assertNull(dao.currentMemory())
    }

    @Test
    fun resetAllClearsCoachData() = runBlocking {
        seedFinishedWorkout()
        coach.requestSessionReview(sessionId)
        coach.sendPending()
        assertFalse(coach.reviews.first().isEmpty())
        assertNotNull(dao.currentMemory())

        data.resetAll()
        assertTrue(coach.reviews.first().isEmpty())
        assertNull(dao.currentMemory())
    }

    // --- Per exercise ------------------------------------------------------------------------

    private val day = 86_400_000L
    private val base = 1_760_000_000_000L

    private fun dateOf(ms: Long) =
        kotlin.time.Instant.fromEpochMilliseconds(ms).toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date.toString()

    /** One bench workout (2 sets of [weight] × 8, all done) started on day [dayIndex]; [finished] = false leaves it running. */
    private suspend fun benchWorkout(id: String, dayIndex: Int, weight: Double, finished: Boolean = true) {
        val bench = requireNotNull(data.exercises.get("wg:bench-press"))
        val tid = data.templates.save(Template(0, "Push", null, listOf(TemplateExercise(bench, 60, null, List(2) { PlannedSet(weight, 8) }))))
        var w = Workout.fromTemplate(requireNotNull(data.templates.get(tid)), base + dayIndex * day, id = id)
        w.exercises.forEach { e -> e.sets.forEach { w = w.completeSet(it.id, base + dayIndex * day + 1000) } }
        if (finished) data.workouts.finish(w, base + dayIndex * day + 60_000) else data.workouts.save(w)
        templateId = tid
    }

    @Test
    fun comparisonsUseTheLastEarlierFinishedWorkoutOnly() = runBlocking {
        data.seedCatalog()
        benchWorkout("old", 0, 60.0)
        benchWorkout("running", 3, 90.0, finished = false) // not finished: never "last time"
        benchWorkout("cur", 7, 65.0)
        benchWorkout("newer", 14, 100.0) // later than the current one: never "last time"

        val c = coach.comparisons("cur").single()
        assertEquals("wg:bench-press", c.exerciseId)
        assertEquals(app.overfit.coach.Verdict.BETTER, c.verdict)
        val last = requireNotNull(c.last)
        assertEquals(dateOf(base), last.date)
        assertEquals(60.0, last.bestSet!!.weightKg!!, 0.0)
        assertEquals(dateOf(base + 7 * day), c.today.date)
        assertEquals(2, c.today.setsDone)

        // The oldest workout has nothing before it (newer and running ones are ignored).
        val first = coach.comparisons("old").single()
        assertNull(first.last)
        assertNull(first.verdict)
        // The newer one compares against "cur" (the latest before it), not against "old".
        assertEquals(dateOf(base + 7 * day), coach.comparisons("newer").single().last!!.date)
        // Unknown session
        assertTrue(coach.comparisons("nope").isEmpty())
    }

    @Test
    fun comparisonsMergeCoachFeedbackById() = runBlocking {
        data.seedCatalog()
        benchWorkout("old", 0, 60.0)
        benchWorkout("cur", 7, 65.0)
        val fb = listOf(app.overfit.coach.ExerciseFeedback("wg:bench-press", app.overfit.coach.Verdict.SAME, "ok", null))
        val c = coach.comparisons("cur", fb).single()
        assertEquals(app.overfit.coach.Verdict.SAME, c.verdict)
        assertEquals(app.overfit.coach.Verdict.BETTER, c.localVerdict)
    }

    @Test
    fun sessionPromptHasPerExerciseSectionAndStoredReviewKeepsExerciseReviews() = runBlocking {
        data.seedCatalog()
        sessionId = "cur"
        benchWorkout("old", 0, 60.0)
        benchWorkout("cur", 7, 65.0)
        answers = mutableListOf(
            """{"rating":8,"summary":"Gut","exercise_reviews":[{"exercise_id":"wg:bench-press","verdict":"better","comment":"Mehr Gewicht","next":{"weight_kg":67.5,"reps":8,"duration_sec":null}}],"positives":[],"improvements":[],"tips":[],"photo_feedback":null,"next_session":null,"memory":null}""",
        )
        val id = coach.requestSessionReview(sessionId)
        coach.sendPending()

        val user = fake.prompts.single().user
        assertTrue(user, user.contains("## Per exercise"))
        assertTrue(user, user.contains("[wg:bench-press]: today best 65kg×8"))
        assertTrue(user, user.contains("last ${dateOf(base)}: best 60kg×8"))
        assertTrue(user, user.contains("by the numbers: better"))

        val review = requireNotNull(coach.review(id).first()!!.review)
        val er = review.exerciseReviews.single()
        assertEquals("wg:bench-press", er.exerciseId)
        assertEquals(app.overfit.coach.Verdict.BETTER, er.verdict)
        assertEquals("Mehr Gewicht", er.comment)
        assertEquals(app.overfit.coach.SuggestedSet(67.5, 8, null), er.next)
    }
}
