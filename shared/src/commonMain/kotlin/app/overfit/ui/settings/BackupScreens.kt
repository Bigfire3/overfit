package app.overfit.ui.settings

import app.overfit.ui.components.ConfirmButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.overfit.data.backup.BackupError
import app.overfit.data.backup.BackupPreview
import app.overfit.data.backup.BackupService
import app.overfit.i18n.LocalStrings
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.Hairline
import app.overfit.ui.components.ListRow
import app.overfit.ui.components.Panel
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.Screen
import app.overfit.ui.components.Text
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private sealed interface Parsed {
    data object Missing : Parsed
    data class Ok(val preview: BackupPreview) : Parsed
    data class Failed(val error: Throwable) : Parsed
}

/**
 * Preview of the picked backup ([pending]) and the confirmed replacement. The restore runs in [appScope]
 * (survives leaving the screen); [onRestored] is called on the main thread.
 */
@Composable
fun BackupImportScreen(
    backup: BackupService,
    pending: MutableStateFlow<ByteArray?>,
    workoutRunning: Boolean,
    appScope: CoroutineScope,
    onBack: () -> Unit,
    onRestored: () -> Unit,
) {
    val t = LocalStrings.current
    val p = t.photos
    val parsed = remember {
        val bytes = pending.value
        if (bytes == null) Parsed.Missing
        else backup.preview(bytes).fold({ Parsed.Ok(it) }, { Parsed.Failed(it) })
    }
    DisposableEffect(Unit) { onDispose { pending.value = null } }

    var running by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }

    val preview = (parsed as? Parsed.Ok)?.preview
    Screen(
        scroll = true,
        header = { BackHeader(t.back, onBack) },
        footer = {
            failure?.let { Text(it, style = Theme.type.caption) }
            if (preview != null && !workoutRunning) {
                val restore = {
                    running = true
                    failure = null
                    appScope.launch {
                        val error = try {
                            backup.restore(preview)
                            null
                        } catch (e: BackupError.WorkoutRunning) {
                            p.importWorkoutRunning
                        } catch (e: Exception) {
                            p.importFailed
                        }
                        withContext(Dispatchers.Main) {
                            running = false
                            if (error == null) onRestored() else failure = error
                        }
                    }
                }
                ConfirmButton(p.importReplace, onConfirm = { restore() }, primary = true, loading = running)
            }
        },
    ) {
        Title(p.importTitle)
        Spacer(Modifier.height(Space.m))
        when (parsed) {
            Parsed.Missing -> Text(p.importNothing, tone = Tone.Muted)
            is Parsed.Failed -> {
                val tooNew = parsed.error is BackupError.TooNew
                if (tooNew) Text(p.importUpdateApp, style = Theme.type.label)
                Text(if (tooNew) p.importTooNew else p.importUnreadable, tone = Tone.Muted)
            }
            is Parsed.Ok -> {
                val pv = parsed.preview
                val date = Instant.fromEpochMilliseconds(pv.createdAt).toLocalDateTime(TimeZone.currentSystemDefault()).date
                Text(p.importCreated(t.date(date)), tone = Tone.Muted)
                Text(p.importVersion(pv.appVersion), style = Theme.type.caption, tone = Tone.Muted)
                Spacer(Modifier.height(Space.l))
                Panel {
                    ListRow(p.importWorkouts(pv.workouts))
                    ListRow(p.importTemplates(pv.templates))
                    ListRow(p.importExercises(pv.customExercises))
                    ListRow(p.importPhotos(pv.photos))
                    if (pv.hasProfile) ListRow(p.importProfile)
                }
                Spacer(Modifier.height(Space.m))
                Text(if (workoutRunning) p.importWorkoutRunning else p.importWarning, style = Theme.type.caption, tone = if (workoutRunning) Tone.Default else Tone.Muted)
            }
        }
    }
}
