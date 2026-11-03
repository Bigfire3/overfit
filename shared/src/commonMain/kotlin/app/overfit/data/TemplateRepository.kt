package app.overfit.data

import app.overfit.data.db.OverfitDatabase
import app.overfit.data.db.TemplateExerciseEntity
import app.overfit.data.db.TemplateSetEntity
import app.overfit.data.db.WorkoutTemplateEntity
import app.overfit.data.db.transaction
import app.overfit.domain.PlannedSet
import app.overfit.domain.Template
import app.overfit.domain.TemplateExercise
import app.overfit.domain.TemplateSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.time.Clock

class TemplateRepository(
    private val db: OverfitDatabase,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val dao = db.templateDao()

    val templates: Flow<List<TemplateSummary>> =
        combine(dao.observeTemplates(), dao.observeTemplateExerciseNames()) { templates, names ->
            val byTemplate = names.groupBy({ it.templateId }, { it.name })
            templates.map { TemplateSummary(it.id, it.name, byTemplate[it.id].orEmpty()) }
        }

    suspend fun get(id: Long): Template? {
        val full = dao.getWithExercises(id) ?: return null
        return Template(
            id = full.template.id,
            name = full.template.name,
            note = full.template.note,
            exercises = full.exercises.sortedBy { it.entry.position }.map { e ->
                TemplateExercise(
                    exercise = e.exercise.toDomain(),
                    restSec = e.entry.restSec,
                    note = e.entry.note,
                    sets = e.sets.sortedBy { it.position }.map { PlannedSet(it.targetWeightKg, it.targetReps, it.targetDurationSec) },
                )
            },
        )
    }

    /**
     * Saves a complete template (new if id = 0) in one transaction and returns its ID. Exercises
     * and sets are rewritten; workouts store their planned values themselves and do not reference
     * these rows.
     */
    suspend fun save(template: Template): Long = db.transaction {
        val now = clock()
        val id = if (template.id == 0L) {
            dao.insertTemplate(
                WorkoutTemplateEntity(
                    name = template.name.trim(),
                    note = template.note?.takeIf { it.isNotBlank() },
                    position = dao.nextPosition(),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } else {
            val existing = requireNotNull(dao.getWithExercises(template.id)) { "Template ${template.id} missing" }.template
            dao.updateTemplate(
                existing.copy(name = template.name.trim(), note = template.note?.takeIf { it.isNotBlank() }, updatedAt = now),
            )
            dao.deleteExercisesOf(template.id)
            template.id
        }
        template.exercises.forEachIndexed { index, te ->
            val entryId = dao.insertExercise(
                TemplateExerciseEntity(
                    templateId = id,
                    exerciseId = te.exercise.id,
                    position = index,
                    restSec = te.restSec,
                    note = te.note?.takeIf { it.isNotBlank() },
                ),
            )
            dao.insertSets(
                te.sets.mapIndexed { setIndex, s ->
                    TemplateSetEntity(
                        templateExerciseId = entryId,
                        position = setIndex,
                        targetWeightKg = s.weightKg,
                        targetReps = s.reps,
                        targetDurationSec = s.durationSec,
                    )
                },
            )
        }
        id
    }

    suspend fun delete(id: Long) = dao.deleteTemplate(id)
}
