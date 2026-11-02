package app.overfit.data

import androidx.room.withTransaction
import app.overfit.data.db.OverfitDatabase
import app.overfit.data.db.TemplateExerciseEntity
import app.overfit.data.db.TemplateSetEntity
import app.overfit.data.db.WorkoutTemplateEntity
import app.overfit.domain.PlannedSet
import app.overfit.domain.Template
import app.overfit.domain.TemplateExercise
import app.overfit.domain.TemplateSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class TemplateRepository(private val db: OverfitDatabase, private val clock: () -> Long = System::currentTimeMillis) {
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
     * Speichert eine Vorlage komplett (neu bei id = 0) in einer Transaktion und gibt die ID zurück.
     * Übungen und Sätze werden dabei neu geschrieben; Trainings speichern ihre Planwerte selbst
     * und hängen nicht an diesen Zeilen.
     */
    suspend fun save(template: Template): Long = db.withTransaction {
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
            val existing = requireNotNull(dao.getWithExercises(template.id)) { "Vorlage ${template.id} fehlt" }.template
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
