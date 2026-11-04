package app.overfit.ui.photos

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import app.overfit.ui.components.ConfirmDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.overfit.data.DiaryPhoto
import app.overfit.data.db.HistoryRow
import app.overfit.domain.Language
import app.overfit.domain.WeightUnit
import app.overfit.i18n.LocalStrings
import app.overfit.platform.rememberPhotoPicker
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.Hairline
import app.overfit.ui.components.Icon
import app.overfit.ui.components.LineIcon
import app.overfit.ui.components.Screen
import app.overfit.ui.components.SectionTitle
import app.overfit.ui.components.Panel
import app.overfit.ui.components.Hint
import app.overfit.ui.components.SmallButton
import app.overfit.ui.components.Text
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.history.PhotoThumb
import app.overfit.ui.history.TrainingRow
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.datetime.LocalDate

/**
 * One calendar day (tap on a day in the calendar): its photos (view, add with camera or library,
 * remove with a second tap) and its trainings (tap = training detail, where it can be edited).
 * Works for days without a training too, to add photos.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayScreen(
    date: LocalDate,
    photos: List<DiaryPhoto>?,
    history: List<HistoryRow>?,
    unit: WeightUnit,
    language: Language,
    onBack: () -> Unit,
    onOpenPhoto: (photoId: String) -> Unit,
    onOpenWorkout: (sessionId: String) -> Unit,
    /** Picked or taken image, still unprocessed; the caller prepares and stores it for this day. */
    onAddPhoto: (bytes: ByteArray) -> Unit,
    onRemovePhoto: (photoId: String) -> Unit,
) {
    val t = LocalStrings.current
    val p = t.photos
    val h = t.history
    val dayPhotos = remember(photos, date) { photos.orEmpty().filter { dateOf(it.photo.takenAt) == date }.sortedBy { it.photo.takenAt } }
    val trainings = remember(history, date) { history.orEmpty().filter { dateOf(it.startedAt) == date }.sortedBy { it.startedAt } }
    val picker = rememberPhotoPicker(onAddPhoto)

    Screen(scroll = true, header = { BackHeader(t.back, onBack) }) {
        Title(t.date(date))

        SectionTitle(p.dayTrainings)
        if (history != null && trainings.isEmpty()) Text(p.dayNoTraining, tone = Tone.Muted)
        if (trainings.isNotEmpty()) Panel { trainings.forEach { TrainingRow(it, unit, language, onOpenWorkout) } }

        SectionTitle(p.dayPhotos)
        if (photos != null && dayPhotos.isEmpty()) {
            Text(p.dayNoPhotos, tone = Tone.Muted)
            Spacer(Modifier.height(Space.s))
        }
        if (dayPhotos.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                dayPhotos.forEachIndexed { i, item ->
                    DayPhoto(item, h.photoDescription(i + 1), onOpen = { onOpenPhoto(item.photo.id) }, onRemove = { onRemovePhoto(item.photo.id) })
                }
            }
            Spacer(Modifier.height(Space.m))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.l)) {
            if (picker.canTakePhoto) SmallButton(h.takePhoto, picker.takePhoto, tone = Tone.Default)
            SmallButton(h.pickPhoto, picker.pickFromLibrary, tone = Tone.Default)
        }
        Hint(h.photosPrivate)
    }
}

/** Thumbnail with an "x" in the corner: it asks in a popup ("Foto entfernen?") before removing. */
@Composable
private fun DayPhoto(item: DiaryPhoto, description: String, onOpen: () -> Unit, onRemove: () -> Unit) {
    val t = LocalStrings.current
    var asking by remember { mutableStateOf(false) }
    val c = Theme.colors
    Box {
        PhotoThumb(item.photo, description, size = 104.dp, onClick = onOpen)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(36.dp)
                    .semantics { contentDescription = t.history.removePhoto }
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { asking = true },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(24.dp).background(c.background, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                    LineIcon(Icon.Close, size = 16.dp)
                }
            }
    }
    if (asking) ConfirmDialog(t.photos.removePhotoConfirm, t.remove, onConfirm = onRemove, onDismiss = { asking = false })
}
