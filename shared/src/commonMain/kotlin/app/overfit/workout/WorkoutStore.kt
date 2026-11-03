package app.overfit.workout

import app.overfit.domain.SetValues
import app.overfit.domain.Workout

/** Persistence of workouts (Room in the app, in-memory in tests). */
interface WorkoutStore {
    /** The workout that is still running, if any. */
    suspend fun loadActive(): Workout?

    /** Writes the running workout completely (insert or update). */
    suspend fun save(workout: Workout)

    /** Saves [workout] one last time and marks it as finished at [endedAt]. */
    suspend fun finish(workout: Workout, endedAt: Long)

    /** Removes a workout with everything in it (discard). */
    suspend fun delete(id: String)

    /** Completed sets of the exercise from the last finished workout with it ("last time"). */
    suspend fun lastSets(exerciseId: String): List<SetValues>
}

/** Simple in-memory store (tests, previews). */
class InMemoryWorkoutStore : WorkoutStore {
    var active: Workout? = null
    val finished = mutableListOf<Pair<Workout, Long>>()
    var saves = 0
    val last = mutableMapOf<String, List<SetValues>>()

    override suspend fun loadActive() = active
    override suspend fun save(workout: Workout) { saves++; active = workout }
    override suspend fun finish(workout: Workout, endedAt: Long) { active = null; finished += workout to endedAt }
    override suspend fun delete(id: String) { if (active?.id == id) active = null }
    override suspend fun lastSets(exerciseId: String) = last[exerciseId].orEmpty()
}
