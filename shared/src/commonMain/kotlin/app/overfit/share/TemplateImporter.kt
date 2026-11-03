package app.overfit.share

import app.overfit.data.ExerciseRepository
import app.overfit.data.TemplateRepository
import app.overfit.domain.TemplateSummary
import app.overfit.shared.resources.Res
import kotlinx.coroutines.flow.first

/** Import of shared templates (preview, then save) and the bundled sample templates. */
class TemplateImporter(
    private val exercises: ExerciseRepository,
    private val templates: TemplateRepository,
    private val readSample: suspend (String) -> ByteArray = { Res.readBytes("files/samples/$it.json") },
) {
    suspend fun preview(training: SharedTraining): ImportPreview =
        ShareMapper.resolve(training, exercises.exercises.first())

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
        val existing = templates.templates.first().map { it.name.lowercase() }.toSet()
        var added = 0
        for (key in SAMPLE_KEYS) {
            val training = (ShareFormat.fromJson(readSample(key).decodeToString()) as? ShareParse.Ok)?.training ?: continue
            if (training.name.lowercase() in existing) continue
            accept(preview(training))
            added++
        }
        return added
    }

    companion object {
        val SAMPLE_KEYS = listOf("push", "pull", "legs")
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
