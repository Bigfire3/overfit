package app.overfit.data.images

import android.content.Context
import app.overfit.domain.Exercise
import java.io.File

/**
 * Bildquellen für Übungen:
 * - Workout Guide: SVG aus den App-Assets (offline mitgebündelt), wird beim Anzeigen eingefärbt.
 * - free-exercise-db: JPG per URL (Coil-Disk-Cache) oder – nach „Alle Bilder offline laden“ –
 *   dauerhaft im app-privaten Speicher.
 * - ohne Bild: null → Initialen-Kachel.
 */
object ExerciseImages {
    private const val DIR = "exercise-images"

    fun isBundledVector(exercise: Exercise): Boolean = exercise.imageRef?.endsWith(".svg") == true

    /** Modell für Coil (String-URI oder File) oder null. */
    fun model(context: Context, exercise: Exercise): Any? {
        val ref = exercise.imageRef ?: return null
        return when {
            ref.startsWith("http") -> offlineFile(context, exercise.id).takeIf { it.exists() } ?: ref
            else -> "file:///android_asset/$ref"
        }
    }

    fun offlineDir(context: Context): File = File(context.filesDir, DIR)

    fun offlineFile(context: Context, exerciseId: String): File =
        File(offlineDir(context), exerciseId.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".jpg")
}
