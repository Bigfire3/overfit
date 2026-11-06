package app.overfit.workout

import app.overfit.domain.ExerciseOrigin
import app.overfit.domain.SetValues
import app.overfit.domain.Template
import app.overfit.domain.TrackingType
import app.overfit.domain.Workout
import app.overfit.platform.HapticPattern
import app.overfit.platform.RecordingHaptics
import app.overfit.platform.RecordingRestAlerts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutControllerTest {
    private class Env(val scope: TestScope, val testScope: CoroutineScope) {
        val store = InMemoryWorkoutStore()
        val alerts = RecordingRestAlerts()
        val haptics = RecordingHaptics()
        var prefs = WorkoutPrefs()
        val now get() = scope.testScheduler.currentTime
        val c = WorkoutController(store, alerts, haptics, { prefs }, testScope, { now })
    }

    private fun TestScope.env() = Env(this, backgroundScope)

    private val bench = exercise("bench", name = "Bench")
    private val squat = exercise("squat", name = "Squat")

    /** Bench with a 60 s template rest and two sets, squat with one set. */
    private val template = templateOf(
        entry(bench, 60, planned(50.0, 10), planned(50.0, 8)),
        entry(squat, null, planned(80.0, 5)),
    )

    private suspend fun Env.started(t: Template = template): Workout {
        c.awaitLoaded()
        assertTrue(c.startFromTemplate(t))
        return c.state.value.workout!!
    }

    private val Env.w get() = c.state.value.workout!!
    private fun Env.setId(entry: Int, set: Int) = w.exercises[entry].sets[set].id

    // --- start ---------------------------------------------------------------------------------

    @Test
    fun startFromTemplate_createsWorkoutRequestsPermissionAndPersists() = runTest {
        val e = env()
        advanceTimeBy(5_000)
        val w = e.started()
        assertEquals("Push", w.name)
        assertEquals(5_000L, w.startedAt)
        assertEquals(1, e.alerts.permissionRequests)
        e.c.flush()
        assertEquals(w, e.store.active)
    }

    @Test
    fun startFromTemplate_secondStartWhileRunningIsRejected() = runTest {
        val e = env()
        val first = e.started()
        assertFalse(e.c.startFromTemplate(template))
        assertFalse(e.c.startEmpty("x"))
        assertEquals(first.id, e.w.id)
        assertEquals(1, e.alerts.permissionRequests)
    }

    @Test
    fun startEmpty_usesLocalizedDefaultName() = runTest {
        val e = env()
        e.c.awaitLoaded()
        assertTrue(e.c.startEmpty())
        assertTrue(e.w.name.startsWith("Workout vom "))
        assertTrue(e.w.exercises.isEmpty())
    }

    @Test
    fun startFromTemplate_prefillsGapsFromLastTime() = runTest {
        val e = env()
        e.store.last["squat"] = listOf(planned(100.0, 3))
        val t = templateOf(entry(squat, null, planned()))
        val w = e.started(t)
        assertEquals(planned(100.0, 3), w.exercises.single().sets.single().target)
        assertEquals(mapOf("squat" to listOf(planned(100.0, 3))), e.c.state.value.lastTime)
    }

    // --- workout notification ------------------------------------------------------------------

    @Test
    fun notice_duringTheWholeWorkout_setAndRestTransitionsAnnounced() = runTest {
        val e = env()
        e.started()
        // Start: the first set, silently.
        val (first, firstAnnounce) = e.alerts.notices.last()
        assertEquals("Bench", first.title)
        assertEquals("Satz 1/2 · 50 kg × 10", first.body)
        assertEquals("Satz fertig", first.doneLabel)
        assertFalse(firstAnnounce)
        // Set → rest: announced, and it knows what comes after.
        e.c.toggleSet(e.setId(0, 0))
        val rest = assertNotNull(e.alerts.pending)
        assertTrue(rest.announce)
        assertEquals("Satz 2/2 · 50 kg × 8", rest.after?.body)
        assertEquals("Pause vorbei", rest.after?.note)
        // +15 s: no transition.
        e.c.adjustRest(15)
        assertFalse(e.alerts.pending!!.announce)
        // Rest over in the app: back to the set (the alarm announces it, the app updates silently).
        advanceTimeBy(80_000)
        val (next, nextAnnounce) = e.alerts.notices.last()
        assertEquals("Satz 2/2 · 50 kg × 8", next.body)
        assertFalse(nextAnnounce)
        // Finished: everything goes.
        e.c.finish()
        assertEquals(1, e.alerts.ends)
    }

    @Test
    fun notice_onlyChangesRepost_skipAndAllDone() = runTest {
        val e = env()
        e.started()
        val count = e.alerts.notices.size
        e.c.edit { it.withName("Push B") } // the notice does not show the name while sets are open
        assertEquals(count, e.alerts.notices.size)
        e.c.setValues(e.setId(0, 0), SetValues(weightKg = 52.5, reps = 10))
        assertEquals("Satz 1/2 · 52,5 kg × 10", e.alerts.notices.last().first.body)
        e.c.toggleSet(e.setId(0, 0))
        e.c.skipRest()
        assertEquals("Satz 2/2 · 50 kg × 8", e.alerts.notices.last().first.body)
        e.c.toggleSet(e.setId(0, 1))
        e.c.skipRest()
        e.c.toggleSet(e.setId(1, 0)) // last set: no rest
        val done = e.alerts.notices.last().first
        assertEquals("Push B", done.title)
        assertEquals("Alle Sätze erledigt", done.body)
        assertNull(done.doneLabel)
    }

    @Test
    fun repostNotice_showsTheCurrentStateAgain() = runTest {
        val e = env()
        e.c.awaitLoaded()
        e.c.repostNotice() // no workout: nothing
        assertTrue(e.alerts.notices.isEmpty())
        e.started()
        val count = e.alerts.notices.size
        e.c.repostNotice()
        assertEquals(count + 1, e.alerts.notices.size)
        e.c.toggleSet(e.setId(0, 0))
        val scheduled = e.alerts.scheduled.size
        e.c.repostNotice()
        assertEquals(scheduled + 1, e.alerts.scheduled.size)
        assertFalse(e.alerts.pending!!.announce)
    }

    // --- toggle / rest -------------------------------------------------------------------------

    @Test
    fun toggleSet_ticksAndStartsTemplateRest() = runTest {
        val e = env()
        advanceTimeBy(1_000)
        e.started()
        val id = e.setId(0, 0)
        e.c.toggleSet(id)
        assertEquals(listOf(HapticPattern.Tick), e.haptics.played)
        val set = e.w.exercises[0].sets[0]
        assertTrue(set.completed)
        assertEquals(1_000L, set.completedAt)
        val rest = assertNotNull(e.w.rest)
        assertEquals(1_000L, rest.startedAt)
        assertEquals(61_000L, rest.endsAt)
        assertEquals(id, rest.afterSetId)
    }

    @Test
    fun toggleSet_restFallbackChain() = runTest {
        val e = env()
        val withDefault = exercise("row", rest = 120, name = "Row")
        val plain = exercise("curl", name = "Curl")
        e.prefs = WorkoutPrefs(defaultRestSec = 75)
        e.started(
            templateOf(
                entry(bench, 45, planned(1.0, 1), planned(1.0, 1)),
                entry(withDefault, null, planned(1.0, 1), planned(1.0, 1)),
                entry(plain, null, planned(1.0, 1), planned(1.0, 1)),
            ),
        )
        e.c.toggleSet(e.setId(0, 0))
        assertEquals(45_000L, e.w.rest!!.totalMs)
        e.c.toggleSet(e.setId(1, 0))
        assertEquals(120_000L, e.w.rest!!.totalMs)
        e.c.toggleSet(e.setId(2, 0))
        assertEquals(75_000L, e.w.rest!!.totalMs)
    }

    @Test
    fun toggleSet_schedulesAlertWithEndAndNextExercise() = runTest {
        val e = env()
        advanceTimeBy(2_000)
        e.started()
        // Finish the bench sets; after the second one the squat is next.
        e.c.toggleSet(e.setId(0, 0))
        val first = e.alerts.pending!!
        assertTrue(first.body.contains("Bench"), first.body)
        e.c.toggleSet(e.setId(0, 1))
        val alert = e.alerts.pending!!
        assertEquals(e.w.rest!!.endsAt, alert.endsAt)
        assertEquals(e.now + 60_000, alert.endsAt)
        assertEquals(e.now, alert.startedAt)
        assertTrue(alert.body.contains("Squat"), alert.body)
        assertTrue(alert.body.startsWith("Nächste: "), alert.body)
        assertTrue(alert.vibrate)
    }

    @Test
    fun alert_usesLanguageAndVibrationPrefs() = runTest {
        val e = env()
        e.prefs = WorkoutPrefs(vibration = false, language = app.overfit.domain.Language.EN)
        e.started()
        e.c.toggleSet(e.setId(0, 0))
        val alert = e.alerts.pending!!
        assertTrue(alert.body.startsWith("Next: "), alert.body)
        assertFalse(alert.vibrate)
    }

    @Test
    fun rest_endsWhenTimeIsUpWithHaptic() = runTest {
        val e = env()
        e.started()
        e.c.toggleSet(e.setId(0, 0))
        runCurrent()
        advanceTimeBy(59_999)
        runCurrent()
        assertNotNull(e.w.rest)
        assertEquals(listOf(HapticPattern.Tick), e.haptics.played)
        advanceTimeBy(1)
        runCurrent()
        assertNull(e.w.rest)
        assertEquals(listOf(HapticPattern.Tick, HapticPattern.RestEnd), e.haptics.played)
        // The system alert stays; it fires by itself.
        assertNotNull(e.alerts.pending)
        // The end is persisted too.
        e.c.flush()
        assertNull(e.store.active!!.rest)
    }

    @Test
    fun ticks_appPlaysThemThreeSecondsBeforeTheEndWhenThePlatformAsks() = runTest {
        val e = env()
        e.alerts.appPlays = true
        e.started()
        e.c.toggleSet(e.setId(0, 0))
        val alert = e.alerts.pending!!
        assertTrue(alert.ticks)
        assertEquals("Noch 3 Sekunden", alert.ticksBody)
        runCurrent()
        advanceTimeBy(56_999)
        runCurrent()
        assertEquals(0, e.alerts.ticksPlayed)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, e.alerts.ticksPlayed)
        advanceTimeBy(3_000)
        runCurrent()
        assertNull(e.w.rest)
        assertEquals(1, e.alerts.ticksPlayed)
    }

    @Test
    fun ticks_notWhenOffOrLeftToThePlatform() = runTest {
        val e = env()
        e.prefs = WorkoutPrefs(ticks = false)
        e.alerts.appPlays = true
        e.started()
        e.c.toggleSet(e.setId(0, 0))
        assertFalse(e.alerts.pending!!.ticks)
        advanceTimeBy(61_000)
        runCurrent()
        assertEquals(0, e.alerts.ticksPlayed)

        // On (default), but the platform plays them itself (Android alarm).
        e.prefs = WorkoutPrefs()
        e.alerts.appPlays = false
        e.c.toggleSet(e.setId(0, 1))
        assertTrue(e.alerts.pending!!.ticks)
        advanceTimeBy(61_000)
        runCurrent()
        assertEquals(0, e.alerts.ticksPlayed)
    }

    @Test
    fun ticks_skippedOrExtendedRestTicksOnlyAtTheNewEnd() = runTest {
        val e = env()
        e.alerts.appPlays = true
        e.started()
        e.c.toggleSet(e.setId(0, 0))
        runCurrent()
        advanceTimeBy(50_000)
        e.c.adjustRest(15) // ends at 75 s now
        runCurrent()
        advanceTimeBy(10_000) // 60 s: the old tick time has passed
        runCurrent()
        assertEquals(0, e.alerts.ticksPlayed)
        advanceTimeBy(12_000) // 72 s
        runCurrent()
        assertEquals(1, e.alerts.ticksPlayed)

        e.c.toggleSet(e.setId(0, 1))
        runCurrent()
        advanceTimeBy(10_000)
        e.c.skipRest()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, e.alerts.ticksPlayed)
        assertNull(e.alerts.pending)
    }

    @Test
    fun adjustRest_plusExtendsAndReschedules() = runTest {
        val e = env()
        e.started()
        e.c.toggleSet(e.setId(0, 0))
        val before = e.w.rest!!
        val scheduledBefore = e.alerts.scheduled.size
        e.c.adjustRest(15)
        assertEquals(before.endsAt + 15_000, e.w.rest!!.endsAt)
        assertEquals(before.startedAt, e.w.rest!!.startedAt)
        assertEquals(scheduledBefore + 1, e.alerts.scheduled.size)
        assertEquals(before.endsAt + 15_000, e.alerts.pending!!.endsAt)
        // The in-app end moves with it.
        advanceTimeBy(60_000)
        runCurrent()
        assertNotNull(e.w.rest)
        advanceTimeBy(15_000)
        runCurrent()
        assertNull(e.w.rest)
    }

    @Test
    fun adjustRest_minusBeyondRemainingEndsRestAndCancelsAlert() = runTest {
        val e = env()
        e.started()
        e.c.toggleSet(e.setId(0, 0))
        advanceTimeBy(50_000)
        runCurrent()
        // 10 s left, take off 15 s.
        e.c.adjustRest(-15)
        assertNull(e.w.rest)
        assertNull(e.alerts.pending)
        assertTrue(e.alerts.cancels > 0)
        advanceTimeBy(120_000)
        runCurrent()
        assertFalse(HapticPattern.RestEnd in e.haptics.played)
    }

    @Test
    fun adjustRest_minusWithinRemainingShortensRest() = runTest {
        val e = env()
        e.started()
        e.c.toggleSet(e.setId(0, 0))
        val before = e.w.rest!!
        e.c.adjustRest(-15)
        assertEquals(before.endsAt - 15_000, e.w.rest!!.endsAt)
    }

    @Test
    fun adjustRest_withoutRestDoesNothing() = runTest {
        val e = env()
        e.started()
        e.c.adjustRest(15)
        assertNull(e.w.rest)
        assertTrue(e.alerts.scheduled.isEmpty())
    }

    @Test
    fun skipRest_clearsRestAlertAndSuppressesRestEndHaptic() = runTest {
        val e = env()
        e.started()
        e.c.toggleSet(e.setId(0, 0))
        e.c.skipRest()
        assertNull(e.w.rest)
        assertNull(e.alerts.pending)
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(listOf(HapticPattern.Tick), e.haptics.played)
    }

    @Test
    fun reopeningTheSetThatStartedTheRestStopsIt() = runTest {
        val e = env()
        e.started()
        val id = e.setId(0, 0)
        e.c.toggleSet(id)
        assertNotNull(e.w.rest)
        e.c.toggleSet(id)
        assertNull(e.w.rest)
        assertFalse(e.w.exercises[0].sets[0].completed)
        assertNull(e.w.exercises[0].sets[0].completedAt)
        assertNull(e.alerts.pending)
        advanceTimeBy(120_000)
        runCurrent()
        assertFalse(HapticPattern.RestEnd in e.haptics.played)
    }

    @Test
    fun reopeningAnotherSetKeepsTheRest() = runTest {
        val e = env()
        e.started()
        val first = e.setId(0, 0)
        e.c.toggleSet(first)
        e.c.toggleSet(e.setId(0, 1))
        val rest = e.w.rest!!
        e.c.toggleSet(first)
        assertEquals(rest, e.w.rest)
    }

    @Test
    fun lastOpenSet_startsNoRest() = runTest {
        val e = env()
        e.started(templateOf(entry(bench, 60, planned(50.0, 10))))
        e.c.toggleSet(e.setId(0, 0))
        assertNull(e.w.rest)
        assertNull(e.alerts.pending)
        assertEquals(listOf(HapticPattern.Tick), e.haptics.played)
    }

    @Test
    fun lastOpenSet_afterOthersCompletedStartsNoRest() = runTest {
        val e = env()
        e.started()
        e.c.toggleSet(e.setId(0, 0))
        e.c.toggleSet(e.setId(1, 0))
        // Only bench set 2 is open now; completing it is the very last one.
        assertNotNull(e.w.rest)
        e.c.toggleSet(e.setId(0, 1))
        assertNull(e.w.rest)
    }

    @Test
    fun zeroRest_startsNoRest() = runTest {
        val e = env()
        e.prefs = WorkoutPrefs(defaultRestSec = 0)
        e.started(templateOf(entry(squat, null, planned(1.0, 1), planned(1.0, 1))))
        e.c.toggleSet(e.setId(0, 0))
        assertNull(e.w.rest)
        assertNull(e.alerts.pending)
        assertTrue(e.alerts.scheduled.isEmpty())
    }

    @Test
    fun templateRestZeroOverridesDefault() = runTest {
        val e = env()
        e.started(templateOf(entry(bench, 0, planned(1.0, 1), planned(1.0, 1))))
        e.c.toggleSet(e.setId(0, 0))
        assertNull(e.w.rest)
    }

    @Test
    fun vibrationOff_noHaptics() = runTest {
        val e = env()
        e.prefs = WorkoutPrefs(vibration = false)
        e.started()
        e.c.toggleSet(e.setId(0, 0))
        advanceTimeBy(61_000)
        runCurrent()
        assertNull(e.w.rest)
        assertTrue(e.haptics.played.isEmpty())
    }

    // --- logNextSet ----------------------------------------------------------------------------

    @Test
    fun logNextSet_checksOffInOrderUntilAllDone() = runTest {
        val e = env()
        e.prefs = WorkoutPrefs(defaultRestSec = 0)
        e.started(templateOf(entry(bench, null, planned(1.0, 1), planned(1.0, 1)), entry(squat, null, planned(1.0, 1))))
        val order = listOf(e.setId(0, 0), e.setId(0, 1), e.setId(1, 0))
        order.forEachIndexed { i, id ->
            assertTrue(e.c.logNextSet())
            assertEquals(i + 1, e.w.completedSets)
            assertTrue(e.w.exercises.flatMap { it.sets }.first { it.id == id }.completed)
        }
        assertFalse(e.c.logNextSet())
        assertEquals(3, e.w.completedSets)
    }

    @Test
    fun logNextSet_continuesAfterTheLastDoneSetNotFromTheStart() = runTest {
        val e = env()
        e.prefs = WorkoutPrefs(defaultRestSec = 0)
        e.started(templateOf(entry(bench, null, planned(1.0, 1), planned(1.0, 1), planned(1.0, 1))))
        // Skip ahead: set 2 is done, set 1 still open.
        e.c.toggleSet(e.setId(0, 1))
        advanceTimeBy(1_000)
        assertTrue(e.c.logNextSet())
        assertTrue(e.w.exercises[0].sets[2].completed)
        assertFalse(e.w.exercises[0].sets[0].completed)
    }

    @Test
    fun logNextSet_withoutWorkoutIsFalse() = runTest {
        val e = env()
        e.c.awaitLoaded()
        assertFalse(e.c.logNextSet())
    }

    // --- process death -------------------------------------------------------------------------

    private class Second(val scope: TestScope, val store: InMemoryWorkoutStore, job: Job) {
        val alerts = RecordingRestAlerts()
        val haptics = RecordingHaptics()
        val c = WorkoutController(
            store, alerts, haptics, { WorkoutPrefs() },
            CoroutineScope(scope.backgroundScope.coroutineContext + job), { scope.testScheduler.currentTime },
        )
        val w get() = c.state.value.workout
    }

    /** Controller 1 runs in its own job so that "killing the process" is cancelling it. */
    private fun TestScope.firstEnv(job: Job) = Env(this, CoroutineScope(backgroundScope.coroutineContext + job))

    @Test
    fun processDeath_restoresWorkoutAndRunningRest() = runTest {
        val job1 = Job()
        val e1 = firstEnv(job1)
        e1.started()
        e1.c.toggleSet(e1.setId(0, 0))
        e1.c.flush()
        val saved = e1.w
        job1.cancel() // process dies

        advanceTimeBy(20_000)
        val e2 = Second(this, e1.store, Job())
        e2.c.awaitLoaded()
        assertEquals(saved, e2.w)
        assertEquals(saved.rest, e2.w!!.rest)
        // The alert is planned again (the system may have lost it).
        assertEquals(saved.rest!!.endsAt, e2.alerts.pending!!.endsAt)
        // The restored rest still ends on time, with a haptic.
        advanceTimeBy(39_999)
        runCurrent()
        assertNotNull(e2.w!!.rest)
        advanceTimeBy(1)
        runCurrent()
        assertNull(e2.w!!.rest)
        assertEquals(listOf(HapticPattern.RestEnd), e2.haptics.played)
    }

    @Test
    fun processDeath_restRunOutInTheMeantimeIsDropped() = runTest {
        val job1 = Job()
        val e1 = firstEnv(job1)
        e1.started()
        e1.c.toggleSet(e1.setId(0, 0))
        e1.c.flush()
        job1.cancel()

        advanceTimeBy(61_000)
        val e2 = Second(this, e1.store, Job())
        e2.c.awaitLoaded()
        assertNull(e2.w!!.rest)
        assertEquals(1, e2.w!!.completedSets)
        assertNull(e2.alerts.pending)
        assertTrue(e2.alerts.scheduled.isEmpty())
        e2.c.flush()
        assertNull(e1.store.active!!.rest)
    }

    @Test
    fun noStoredWorkout_loadsWithoutWorkout() = runTest {
        val e = env()
        assertFalse(e.c.state.value.loaded)
        e.c.awaitLoaded()
        assertTrue(e.c.state.value.loaded)
        assertNull(e.c.state.value.workout)
    }

    // --- finish / discard ----------------------------------------------------------------------

    @Test
    fun finish_storesWorkoutWithoutRestAndClearsState() = runTest {
        val e = env()
        e.started()
        e.c.toggleSet(e.setId(0, 0))
        assertNotNull(e.w.rest)
        advanceTimeBy(5_000)
        val before = e.w
        val cancelsBefore = e.alerts.cancels
        e.c.finish()
        assertNull(e.c.state.value.workout)
        assertTrue(e.c.state.value.loaded)
        assertNull(e.store.active)
        val (stored, endedAt) = e.store.finished.single()
        assertNull(stored.rest)
        assertEquals(before.copy(rest = null), stored)
        assertEquals(5_000L, endedAt)
        assertTrue(e.alerts.cancels > cancelsBefore)
        assertNull(e.alerts.pending)
        // No stray rest end afterwards.
        advanceTimeBy(120_000)
        runCurrent()
        assertFalse(HapticPattern.RestEnd in e.haptics.played)
        assertNull(e.store.active)
    }

    @Test
    fun finish_completesEvenIfTheCallerIsCancelled() = runTest {
        // The workout screen calls finish() from its own scope and closes as soon as the state is empty.
        val e = env()
        e.started()
        val caller = launch { e.c.finish() }
        runCurrent()
        caller.cancel()
        runCurrent()
        assertNull(e.c.state.value.workout)
        assertEquals(1, e.store.finished.size)
    }

    @Test
    fun discard_completesEvenIfTheCallerIsCancelled() = runTest {
        val e = env()
        e.started()
        e.c.flush()
        assertNotNull(e.store.active)
        val caller = launch { e.c.discard() }
        runCurrent()
        caller.cancel()
        runCurrent()
        assertNull(e.store.active)
    }

    @Test
    fun finish_allowsStartingTheNextWorkout() = runTest {
        val e = env()
        e.started()
        e.c.finish()
        assertTrue(e.c.startEmpty("again"))
    }

    @Test
    fun finish_withoutWorkoutDoesNothing() = runTest {
        val e = env()
        e.c.awaitLoaded()
        e.c.finish()
        assertTrue(e.store.finished.isEmpty())
    }

    @Test
    fun discard_removesWorkoutFromStore() = runTest {
        val e = env()
        e.started()
        e.c.toggleSet(e.setId(0, 0))
        e.c.flush()
        assertNotNull(e.store.active)
        e.c.discard()
        assertNull(e.store.active)
        assertNull(e.c.state.value.workout)
        assertTrue(e.store.finished.isEmpty())
        assertNull(e.alerts.pending)
    }

    // --- changes -------------------------------------------------------------------------------

    @Test
    fun addExercises_prefillsFromLastTime() = runTest {
        val e = env()
        e.c.awaitLoaded()
        e.c.startEmpty("Free")
        val row = exercise("row", name = "Row")
        val curl = exercise("curl", name = "Curl")
        e.store.last["row"] = listOf(planned(60.0, 8), planned(60.0, 6))
        e.c.addExercises(listOf(row, curl))
        val entries = e.w.exercises
        assertEquals(listOf("row", "curl"), entries.map { it.exercise.id })
        assertTrue(entries.all { it.origin == ExerciseOrigin.ADDED })
        assertEquals(listOf(planned(60.0, 8), planned(60.0, 6)), entries[0].sets.map { it.actual })
        // Nothing from last time: three empty sets.
        assertEquals(3, entries[1].sets.size)
        assertTrue(entries[1].sets.all { it.target == SetValues() })
        assertEquals(listOf(planned(60.0, 8), planned(60.0, 6)), e.c.state.value.lastTime["row"])
        assertEquals(emptyList(), e.c.state.value.lastTime["curl"])
    }

    @Test
    fun addExercises_withoutWorkoutDoesNothing() = runTest {
        val e = env()
        e.c.awaitLoaded()
        e.c.addExercises(listOf(exercise("row")))
        assertNull(e.c.state.value.workout)
    }

    @Test
    fun addPicked_completesEvenIfTheCallerIsCancelled() = runTest {
        // The workout screen hands the picker's result over and may leave the composition right away.
        val e = env()
        e.started()
        val loaded = kotlinx.coroutines.CompletableDeferred<List<app.overfit.domain.Exercise>>()
        val caller = launch { e.c.addPicked(null) { loaded.await() }.join() }
        runCurrent()
        caller.cancel()
        loaded.complete(listOf(exercise("row", name = "Row")))
        runCurrent()
        assertEquals(listOf("bench", "squat", "row"), e.w.exercises.map { it.exercise.id })
    }

    @Test
    fun addPicked_withEntryReplacesIt() = runTest {
        val e = env()
        e.started()
        val entryId = e.w.exercises[0].id
        e.c.addPicked(entryId) { listOf(exercise("dips", TrackingType.REPS), exercise("row")) }
        runCurrent()
        assertEquals(listOf("dips", "squat"), e.w.exercises.map { it.exercise.id })
    }

    @Test
    fun replaceExercise_marksReplacedAndPersists() = runTest {
        val e = env()
        e.started()
        val entryId = e.w.exercises[0].id
        e.c.replaceExercise(entryId, exercise("dips", TrackingType.REPS))
        assertEquals(ExerciseOrigin.REPLACED, e.w.exercises[0].origin)
        assertEquals("bench", e.w.exercises[0].replacedExerciseId)
        e.c.flush()
        assertEquals(e.w, e.store.active)
    }

    @Test
    fun edit_isPersistedAfterFlush() = runTest {
        val e = env()
        e.started()
        e.c.edit { it.withName("Renamed") }
        e.c.flush()
        assertEquals("Renamed", e.store.active!!.name)
    }

    @Test
    fun edit_runningRestKeepsGoingWhenItsSetIsRemoved() = runTest {
        val e = env()
        e.started()
        val id = e.setId(0, 0)
        e.c.toggleSet(id)
        val rest = e.w.rest
        e.c.edit { it.removeSet(id) }
        assertEquals(rest, e.w.rest)
    }

    @Test
    fun edit_updatesAlertWhenNextSetChanges() = runTest {
        val e = env()
        e.started()
        e.c.toggleSet(e.setId(0, 0))
        val before = e.alerts.scheduled.size
        // Remove the set that would be next; the alert text must follow.
        val next = e.setId(0, 1)
        e.c.edit { it.removeSet(next) }
        assertEquals(before + 1, e.alerts.scheduled.size)
        assertTrue(e.alerts.pending!!.body.contains("Squat"), e.alerts.pending!!.body)
    }

    @Test
    fun writes_areCoalescedButLatestStateWins() = runTest {
        val e = env()
        e.started()
        repeat(10) { i -> e.c.edit { it.withName("n$i") } }
        e.c.flush()
        assertEquals("n9", e.store.active!!.name)
    }

    // --- saved workout follows the workout ------------------------------------------------------

    @Test
    fun finishAndDiscard_passWorkoutToPlans_finishStoresReturnedTemplateId() = runTest {
        val adopted = mutableListOf<Workout>()
        val store = InMemoryWorkoutStore()
        val c = WorkoutController(store, RecordingRestAlerts(), RecordingHaptics(), { WorkoutPrefs() }, backgroundScope, { testScheduler.currentTime },
            plans = { w -> adopted += w; w.copy(templateId = 42) })
        c.awaitLoaded()
        assertTrue(c.startEmpty())
        c.addExercises(listOf(bench))
        c.finish()
        assertEquals(1, adopted.size)
        assertEquals(42L, store.finished.single().first.templateId)
        assertEquals(listOf("bench"), store.finished.single().first.exercises.map { it.exercise.id })

        assertTrue(c.startEmpty())
        c.discard()
        assertEquals(2, adopted.size)
        assertTrue(c.startEmpty())
        c.discard(keepStructure = false)
        assertEquals(2, adopted.size)
    }

    @Test
    fun failingPlans_stillFinish() = runTest {
        val store = InMemoryWorkoutStore()
        val c = WorkoutController(store, RecordingRestAlerts(), RecordingHaptics(), { WorkoutPrefs() }, backgroundScope, { testScheduler.currentTime },
            plans = { error("db down") })
        c.awaitLoaded()
        assertTrue(c.startEmpty())
        c.finish()
        assertEquals(1, store.finished.size)
        assertNull(store.finished.single().first.templateId)
    }

    @Test
    fun startEmpty_defaultNameHasTheDate() = runTest {
        val e = env()
        e.c.awaitLoaded()
        assertTrue(e.c.startEmpty())
        assertTrue(e.w.name.startsWith("Workout vom "), e.w.name)
    }
}
