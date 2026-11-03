package app.overfit.share

import app.overfit.data.ExerciseRepository
import app.overfit.data.TemplateRepository
import app.overfit.domain.Exercise
import app.overfit.shared.resources.Res
import kotlinx.coroutines.delay

/** Import of shared templates (preview, then save) and the bundled sample templates. */
class TemplateImporter(
    private val exercises: ExerciseRepository,
    private val templates: TemplateRepository,
    private val readSample: suspend (String) -> ByteArray = { Res.readBytes("files/samples/$it.json") },
) {
    suspend fun preview(training: SharedTraining): ImportPreview = preview(training, catalog())

    private fun preview(training: SharedTraining, catalog: List<Exercise>): ImportPreview = ShareMapper.resolve(training, catalog)

    /** All exercises; right after a fresh start this waits for the catalogue to be seeded so nothing is created twice. */
    private suspend fun catalog(): List<Exercise> {
        var list = exercises.getActive()
        var waited = 0L
        while (list.isEmpty() && waited < CATALOG_WAIT_MS) {
            delay(CATALOG_POLL_MS)
            waited += CATALOG_POLL_MS
            list = exercises.getActive()
        }
        return list
    }

    /** Creates the new own exercises (once per name) and saves the template. Returns the template ID. */
    suspend fun accept(preview: ImportPreview): Long {
        val created = LinkedHashMap<String, String>()
        preview.exercises.mapNotNull { it.newExercise }.forEach { input ->
            created.getOrPut(input.name.lowercase()) { exercises.createCustom(input) }
        }
        return templates.save(ShareMapper.toTemplate(preview, created))
    }

    /** Adds the sample templates that are missing (matched by name). Returns how many were added. */
    suspend fun addSamples(): Int {
        val existing = templates.names().map { it.lowercase() }.toSet()
        var added = 0
        val catalog = catalog()
        for (key in SAMPLE_KEYS) {
            val training = (ShareFormat.fromJson(readSample(key).decodeToString()) as? ShareParse.Ok)?.training ?: continue
            if (training.name.lowercase() in existing) continue
            accept(preview(training, catalog))
            added++
        }
        return added
    }

    companion object {
        val SAMPLE_KEYS = listOf("push", "pull", "legs")
        private const val CATALOG_WAIT_MS = 15_000L
        private const val CATALOG_POLL_MS = 200L
    }
}

/** Example for the AI prompt; also used by tests to prove that an answer in this shape imports. */
object AiPrompt {
    val EXAMPLE: String = """{
  "v": 1,
  "kind": "template",
  "name": "Oberkörper A",
  "exercises": [
    { "name": "Bench Press", "restSec": 150, "sets": [ { "kg": 60, "reps": 8 }, { "kg": 60, "reps": 8 }, { "kg": 60, "reps": 8 } ] },
    { "name": "Pull-up", "restSec": 120, "sets": [ { "reps": 8 }, { "reps": 8 } ] },
    { "name": "Plank", "restSec": 60, "sets": [ { "sec": 45 }, { "sec": 45 } ] }
  ]
}"""
}
