package app.overfit.chat

import app.overfit.data.WorkoutRecord
import app.overfit.domain.Language
import app.overfit.domain.PlannedSet
import app.overfit.domain.Profile
import app.overfit.domain.Template
import app.overfit.domain.WeightEntry
import app.overfit.domain.WeightUnit
import app.overfit.domain.WorkoutExercise
import app.overfit.domain.WorkoutSet
import app.overfit.domain.ExerciseOrigin
import app.overfit.domain.aiProfileFacts
import kotlinx.datetime.toLocalDateTime
import app.overfit.fuel.Sha256
import app.overfit.fuel.toHex
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Maps the app's data to the sync documents of docs/backend-api.md. Weights in kg, times in ms. Pure functions. */
object ChatDocs {
    private fun today(now: Long, timeZone: String): kotlinx.datetime.LocalDate {
        val tz = runCatching { kotlinx.datetime.TimeZone.of(timeZone) }.getOrDefault(kotlinx.datetime.TimeZone.UTC)
        return kotlin.time.Instant.fromEpochMilliseconds(now).toLocalDateTime(tz).date
    }

    const val PROFILE_ID = "me"
    const val MEMORY_ID = "coach"

    fun profile(profile: Profile, weights: List<WeightEntry>, language: Language, timeZone: String, now: Long): SyncDoc =
        SyncDoc(DocKind.PROFILE, PROFILE_ID, now, buildJsonObject {
            put("name", profile.name)
            profile.birthYear?.let { put("birthYear", it) }
            put("unit", if (profile.unit == WeightUnit.KG) "kg" else "lb")
            put("language", if (language == Language.DE) "de" else "en")
            put("timeZone", timeZone)
            // Same line as in every other AI path; the backend adds it to every MCP tool answer.
            aiProfileFacts(profile, weights, today(now, timeZone), language)?.let { put("summary", it) }
            put("bodyweight", buildJsonArray {
                weights.sortedBy { it.date }.forEach { add(buildJsonObject { put("date", it.date); put("kg", it.kg) }) }
            })
        })

    fun memory(text: String, now: Long): SyncDoc =
        SyncDoc(DocKind.MEMORY, MEMORY_ID, now, buildJsonObject { put("text", text) })

    /** Templates have a local number as ID; it is used as document ID (and comes back as `replacesTemplateId`). */
    fun templateId(id: Long): String = id.toString()

    fun template(t: Template, now: Long): SyncDoc =
        SyncDoc(DocKind.TEMPLATE, templateId(t.id), now, buildJsonObject {
            put("name", t.name)
            t.note?.let { put("note", it) }
            put("exercises", buildJsonArray {
                t.exercises.forEach { te ->
                    add(buildJsonObject {
                        put("exerciseId", te.exercise.id)
                        put("name", te.exercise.name)
                        put("trackingType", te.exercise.trackingType.name)
                        te.restSec?.let { put("restSec", it) }
                        te.note?.let { put("note", it) }
                        put("sets", buildJsonArray { te.sets.forEach { add(plannedSet(it)) } })
                    })
                }
            })
        })

    fun workout(r: WorkoutRecord): SyncDoc {
        val w = r.workout
        return SyncDoc(DocKind.WORKOUT, w.id, r.updatedAt, buildJsonObject {
            put("name", w.name)
            w.templateId?.let { put("templateId", templateId(it)) }
            put("startedAt", w.startedAt)
            put("endedAt", r.endedAt?.let { JsonPrimitive(it) } ?: JsonNull)
            w.note?.takeIf { it.isNotBlank() }?.let { put("note", it) }
            r.feeling?.let { put("feeling", it) }
            if (r.sauna) put("sauna", true)
            put("exercises", buildJsonArray { w.exercises.forEach { add(workoutExercise(it)) } })
        })
    }

    private fun workoutExercise(e: WorkoutExercise): JsonObject = buildJsonObject {
        put("exerciseId", e.exercise.id)
        put("name", e.exercise.name)
        put("trackingType", e.exercise.trackingType.name)
        put("origin", when (e.origin) {
            ExerciseOrigin.PLANNED -> "planned"
            ExerciseOrigin.ADDED -> "added"
            ExerciseOrigin.REPLACED -> "replaced"
        })
        e.replacedExerciseId?.let { put("replacedExerciseId", it) }
        e.note.takeIf { it.isNotBlank() }?.let { put("note", it) }
        e.restSec?.let { put("restSec", it) }
        put("sets", buildJsonArray { e.sets.forEach { add(workoutSet(it)) } })
    }

    private fun workoutSet(s: WorkoutSet): JsonObject = buildJsonObject {
        s.actual.weightKg?.let { put("kg", it) }
        s.actual.reps?.let { put("reps", it) }
        s.actual.durationSec?.let { put("sec", it) }
        put("completed", s.completed)
        s.completedAt?.let { put("completedAt", it) }
        val planned = plannedSet(s.target)
        if (planned.isNotEmpty()) put("planned", planned)
    }

    private fun plannedSet(s: PlannedSet): JsonObject = buildJsonObject {
        s.weightKg?.let { put("kg", it) }
        s.reps?.let { put("reps", it) }
        s.durationSec?.let { put("sec", it) }
    }

    /** Stable fingerprint of a document's content (not of `updatedAt`), used to find what changed since the last upload. */
    fun fingerprint(doc: SyncDoc): String =
        Sha256.hash(doc.data.toString().encodeToByteArray()).toHex().take(24)

    /** Splits work into upload packages of at most [size] entries; the first one carries `reset` when asked. */
    fun <T> batches(items: List<T>, size: Int = ChatApi.MAX_BATCH): List<List<T>> =
        if (items.isEmpty()) emptyList() else items.chunked(size)
}
