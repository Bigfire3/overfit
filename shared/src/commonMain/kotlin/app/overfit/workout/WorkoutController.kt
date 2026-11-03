package app.overfit.workout

import app.overfit.domain.Exercise
import app.overfit.domain.Language
import app.overfit.domain.RestTimer
import app.overfit.domain.SetValues
import app.overfit.domain.Template
import app.overfit.domain.WeightUnit
import app.overfit.domain.Workout
import app.overfit.i18n.stringsFor
import app.overfit.platform.HapticPattern
import app.overfit.platform.Haptics
import app.overfit.platform.RestAlert
import app.overfit.platform.RestAlerts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Settings the workout needs, read fresh on every use. */
data class WorkoutPrefs(
    val defaultRestSec: Int = DEFAULT_REST_SEC,
    val vibration: Boolean = true,
    val unit: WeightUnit = WeightUnit.KG,
    val language: Language = Language.DE,
) {
    companion object {
        const val DEFAULT_REST_SEC = 90
    }
}

/**
 * Owns the running workout: applies changes, persists after every change, runs the rest timer
 * and drives haptics and the system alerts.
 *
 * Threading: call from the main thread; [scope] should run on the main dispatcher (tests pass a
 * test scope with virtual time, [clock] then reads the virtual time).
 *
 * Persistence: every change is written completely and asynchronously. Writes are coalesced (only
 * the latest state is written when they pile up) and strictly ordered with [finish]/[discard], so
 * after a process kill the last state written is at most one change behind.
 */
class WorkoutController(
    private val store: WorkoutStore,
    private val alerts: RestAlerts,
    private val haptics: Haptics,
    private val prefs: () -> WorkoutPrefs,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    /**
     * Called when a workout ends (finished or discarded, inside the write lock): passes the
     * structure on to its saved workout or creates one, returns the workout with the template ID
     * to store. Default: nothing is saved (tests).
     */
    private val plans: suspend (Workout) -> Workout = { it },
) {
    data class State(
        /** False until the stored workout (if any) has been loaded. */
        val loaded: Boolean = false,
        val workout: Workout? = null,
        /** "Last time" per exercise ID: completed sets of the last finished workout with it. */
        val lastTime: Map<String, List<SetValues>> = emptyMap(),
        /** The workout that was just finished (opens the finish flow); null after a discard. */
        val finishedId: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val loadedSignal = CompletableDeferred<Unit>()
    private var restJob: Job? = null

    // Coalesced writer: [pendingSave] holds the newest unsaved state, the channel wakes the writer.
    private val writeLock = Mutex()
    private var pendingSave: Workout? = null
    private val saveSignal = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (signal in saveSignal) writeLock.withLock { writePending() }
        }
        scope.launch {
            val stored = runCatching { store.loadActive() }.getOrNull()
            if (stored != null) restore(stored)
            _state.value = _state.value.copy(loaded = true)
            loadedSignal.complete(Unit)
        }
    }

    private val workout: Workout? get() = _state.value.workout

    suspend fun awaitLoaded() = loadedSignal.await()

    /** Waits until everything changed so far is written (tests, shutdown). */
    suspend fun flush() = writeLock.withLock { writePending() }

    private suspend fun writePending() {
        val w = pendingSave ?: return
        pendingSave = null
        runCatching { store.save(w) }
    }

    private fun restore(stored: Workout) {
        val now = clock()
        val rest = stored.rest?.takeUnless { it.isOver(now) }
        val w = stored.copy(rest = rest)
        _state.value = _state.value.copy(workout = w)
        if (rest != stored.rest) persist(w)
        if (rest != null) {
            // Idempotent: replaces the alert if the system still has it, recreates it otherwise.
            alerts.schedule(alertFor(w, rest))
            watchRest(rest)
        }
        scope.launch { loadLastTime(w.exercises.map { it.exercise.id }) }
    }

    // --- Start / end ---------------------------------------------------------------------------

    /**
     * Starts an empty workout; it becomes a saved workout when it ends. Default name: "Workout
     * vom 3. November". Does nothing while another workout is running.
     */
    fun startEmpty(name: String? = null): Boolean {
        if (workout != null) return false
        val now = clock()
        val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(TimeZone.currentSystemDefault()).date
        begin(Workout.empty(name ?: stringsFor(prefs().language).newWorkoutName(today), now))
        return true
    }

    /** Starts a workout from [template] (target values, gaps filled from last time). */
    suspend fun startFromTemplate(template: Template): Boolean {
        if (workout != null) return false
        val ids = template.exercises.map { it.exercise.id }.distinct()
        val last = loadLastTime(ids)
        if (workout != null) return false
        begin(Workout.fromTemplate(template, clock(), last))
        return true
    }

    private fun begin(w: Workout) {
        _state.value = _state.value.copy(finishedId = null)
        alerts.requestPermission()
        set(w)
    }

    /** Saves the workout as finished. Unchecked sets stay as they are (not done). */
    suspend fun finish() {
        val w = workout ?: return
        val endedAt = clock()
        // Runs in the controller's scope: the caller's screen closes as soon as the workout is
        // gone from the state, which must not cancel the write.
        val job = scope.launch {
            writeLock.withLock {
                pendingSave = null
                store.finish(adopt(w).copy(rest = null), endedAt)
            }
        }
        finishJob = job
        clear(finishedId = w.id)
        job.join()
    }

    private var finishJob: Job? = null

    /** [plans] that never breaks finishing or discarding: on an error the workout stays as it is. */
    private suspend fun adopt(w: Workout): Workout = try {
        plans(w)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        w
    }

    /** Waits until the last finished workout is stored (the finish flow reads it right after). */
    suspend fun awaitFinished() { finishJob?.join() }

    /** The finish flow was opened; a later return to the workout screen should not open it again. */
    fun consumeFinished() { _state.value = _state.value.copy(finishedId = null) }

    /**
     * Throws the running workout away: it does not count in the history. Its structure is still
     * passed on to the saved workout (nothing built during the workout gets lost), except with
     * [keepStructure] = false (reset of all data).
     */
    suspend fun discard(keepStructure: Boolean = true) {
        val w = workout ?: return
        clear()
        scope.launch {
            writeLock.withLock {
                pendingSave = null
                if (keepStructure) adopt(w)
                store.delete(w.id)
            }
        }.join()
    }

    private fun clear(finishedId: String? = null) {
        restJob?.cancel()
        restJob = null
        alerts.cancel()
        // "Last time" changes with this workout; reload it for the next one.
        _state.value = State(loaded = true, finishedId = finishedId)
    }

    // --- Changes -------------------------------------------------------------------------------

    /**
     * Any change to the workout (values, sets, exercises, order, notes, name). If the set that
     * started the rest disappears, the rest keeps running.
     */
    fun edit(transform: (Workout) -> Workout) {
        val w = workout ?: return
        val updated = transform(w)
        if (updated != w) set(updated)
        // The "next" line of a running rest may have changed.
        updated.rest?.let { if (it == w.rest && nextLine(updated, it) != nextLine(w, it)) alerts.schedule(alertFor(updated, it)) }
    }

    fun setValues(setId: String, values: SetValues) = edit { it.setValues(setId, values) }

    /** Checks off a set (tick, rest starts) or opens it again (a rest it started stops). */
    fun toggleSet(setId: String) {
        val w = workout ?: return
        val (ei, si) = w.locate(setId) ?: return
        val set = w.exercises[ei].sets[si]
        if (set.completed) {
            val reopened = w.uncompleteSet(setId)
            if (w.rest?.afterSetId == setId) setRest(reopened, null) else set(reopened)
        } else {
            complete(w, setId)
        }
    }

    /** Checks off the next open set (button "Log next set"). False if all sets are done. */
    fun logNextSet(): Boolean {
        val w = workout ?: return false
        val next = w.nextOpenSet(w.rest?.afterSetId ?: lastCompletedSetId(w)) ?: return false
        complete(w, next.set.id)
        return true
    }

    private fun lastCompletedSetId(w: Workout): String? =
        w.exercises.flatMap { it.sets }.filter { it.completed }.maxByOrNull { it.completedAt ?: 0 }?.id

    private fun complete(w: Workout, setId: String) {
        val now = clock()
        val done = w.completeSet(setId, now)
        if (prefs().vibration) haptics.play(HapticPattern.Tick)
        val (ei, _) = done.locate(setId) ?: return
        val restSec = done.exercises[ei].restSec(prefs().defaultRestSec)
        // Nothing left to rest for after the very last set.
        val rest = if (restSec > 0 && done.nextOpenSet(setId) != null) RestTimer.start(now, restSec, setId) else null
        setRest(done, rest)
    }

    /** Adds exercises at the end; sets are prefilled from last time. */
    suspend fun addExercises(exercises: List<Exercise>) {
        if (exercises.isEmpty() || workout == null) return
        val last = loadLastTime(exercises.map { it.id })
        edit { w -> exercises.fold(w) { acc, e -> acc.addExercise(Workout.added(e, last[e.id].orEmpty())) } }
    }

    suspend fun replaceExercise(entryId: String, replacement: Exercise) {
        if (workout == null) return
        loadLastTime(listOf(replacement.id))
        edit { it.replaceExercise(entryId, replacement) }
    }

    // --- Rest timer ----------------------------------------------------------------------------

    /** ±15 s and the like. Taking off more than is left ends the rest. */
    fun adjustRest(deltaSec: Int) {
        val w = workout ?: return
        val rest = w.rest ?: return
        val adjusted = rest.adjust(deltaSec * 1000L)
        setRest(w, adjusted.takeUnless { it.isOver(clock()) })
    }

    fun skipRest() {
        val w = workout ?: return
        if (w.rest != null) setRest(w, null)
    }

    private fun setRest(w: Workout, rest: RestTimer?) {
        val updated = w.copy(rest = rest)
        set(updated)
        restJob?.cancel()
        restJob = null
        if (rest == null) {
            alerts.cancel()
        } else {
            alerts.schedule(alertFor(updated, rest))
            watchRest(rest)
        }
    }

    /** Ends [rest] in the app when its time is up: vibration and the timer disappears. */
    private fun watchRest(rest: RestTimer) {
        restJob?.cancel()
        restJob = scope.launch {
            delay(rest.remainingMs(clock()))
            val w = workout ?: return@launch
            if (w.rest != rest) return@launch
            restJob = null
            if (prefs().vibration) haptics.play(HapticPattern.RestEnd)
            // The system alert stays: it fires by itself (or is suppressed while the app is visible).
            set(w.copy(rest = null))
        }
    }

    private fun nextLine(w: Workout, rest: RestTimer): String? {
        val p = prefs()
        return w.nextOpenSet(rest.afterSetId)?.let { WorkoutFormat.next(it, p.unit, p.language, stringsFor(p.language)) }
    }

    private fun alertFor(w: Workout, rest: RestTimer): RestAlert {
        val p = prefs()
        val t = stringsFor(p.language)
        return RestAlert(
            startedAt = rest.startedAt,
            endsAt = rest.endsAt,
            title = t.rest,
            overTitle = t.restOver,
            body = nextLine(w, rest)?.let(t.nextUp) ?: t.restOverBody,
            vibrate = p.vibration,
            plusLabel = t.restPlus,
            skipLabel = t.restSkip,
            channelRunning = t.channelRest,
            channelOver = t.channelRestOver,
        )
    }

    // --- Internals -----------------------------------------------------------------------------

    private fun set(w: Workout) {
        _state.value = _state.value.copy(workout = w)
        persist(w)
    }

    private fun persist(w: Workout) {
        pendingSave = w
        saveSignal.trySend(Unit)
    }

    /** Loads "last time" for exercises not loaded yet and returns the complete map. */
    private suspend fun loadLastTime(ids: List<String>): Map<String, List<SetValues>> {
        val missing = ids.filter { it !in _state.value.lastTime }
        if (missing.isNotEmpty()) {
            val loaded = missing.associateWith { id -> runCatching { store.lastSets(id) }.getOrDefault(emptyList()) }
            _state.value = _state.value.copy(lastTime = _state.value.lastTime + loaded)
        }
        return _state.value.lastTime
    }
}
