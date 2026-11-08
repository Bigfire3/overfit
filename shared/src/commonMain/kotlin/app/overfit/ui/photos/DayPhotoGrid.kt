package app.overfit.ui.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.overfit.data.WorkoutPhoto
import app.overfit.i18n.LocalStrings
import app.overfit.platform.rememberPhotoPicker
import app.overfit.ui.components.ConfirmTags
import app.overfit.ui.components.Icon
import app.overfit.ui.components.LineIcon
import app.overfit.ui.components.PressScale
import app.overfit.ui.components.Text
import app.overfit.ui.components.Tone
import app.overfit.ui.components.pressScale
import app.overfit.ui.components.rememberConfirmArm
import app.overfit.ui.history.PhotoThumb
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme

/** Test tags: the add tiles of the photo grid. */
const val PHOTO_ADD_CAMERA_TAG = "photo-add-camera"
const val PHOTO_ADD_LIBRARY_TAG = "photo-add-library"

/**
 * The photos of one calendar day as a grid of three equal tiles per row, followed by the add tiles (camera, library).
 * The same grid sits in the day view, the training detail and the finish screen: photos belong to the day (v14), so
 * wherever one is added, it lands on that day. A photo opens on tap ([onOpen], null = not tappable) and is removed with
 * the two-tap "x" ([onRemove], null = no "x").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayPhotoGrid(
    photos: List<WorkoutPhoto>,
    /** Picked or taken image, still unprocessed; the caller prepares and stores it for the day. */
    onAdd: (ByteArray) -> Unit,
    onOpen: ((photoId: String) -> Unit)?,
    onRemove: ((photoId: String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val h = LocalStrings.current.history
    val picker = rememberPhotoPicker(onAdd)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val tile = (maxWidth - Space.s * (COLUMNS - 1)) / COLUMNS
        Column {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                photos.forEachIndexed { i, photo ->
                    GridPhoto(
                        photo, h.photoDescription(i + 1), tile,
                        onOpen = onOpen?.let { { it(photo.id) } },
                        onRemove = onRemove?.let { { it(photo.id) } },
                    )
                }
                if (picker.canTakePhoto) AddTile(Icon.Photos, h.takePhoto, tile, picker.takePhoto, Modifier.testTag(PHOTO_ADD_CAMERA_TAG))
                AddTile(Icon.Plus, h.pickPhoto, tile, picker.pickFromLibrary, Modifier.testTag(PHOTO_ADD_LIBRARY_TAG))
            }
            Spacer(Modifier.height(Space.xs))
        }
    }
}

private const val COLUMNS = 3

/** Square add tile (same size as a photo): icon and a short caption on the surface colour. */
@Composable
private fun AddTile(icon: Icon, label: String, size: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .size(size)
            .pressScale(interaction, PressScale.button)
            .background(Theme.colors.surface, RoundedCornerShape(TILE_RADIUS))
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(Space.s),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LineIcon(icon)
        Spacer(Modifier.height(Space.xs))
        Text(label, Modifier.fillMaxWidth(), style = Theme.type.caption, tone = Tone.Muted, align = TextAlign.Center)
    }
}

private val TILE_RADIUS = 8.dp

/** Thumbnail with an "x" in the corner, two-tap pattern: the first tap turns the "x" red, the second removes. */
@Composable
private fun GridPhoto(photo: WorkoutPhoto, description: String, size: Dp, onOpen: (() -> Unit)?, onRemove: (() -> Unit)?) {
    val t = LocalStrings.current
    val arm = rememberConfirmArm()
    val armed = arm.armed
    val c = Theme.colors
    val haptics = LocalHapticFeedback.current
    Box {
        PhotoThumb(photo, description, size = size, onClick = onOpen)
        if (onRemove != null) Box(
            Modifier
                .align(Alignment.TopEnd)
                .then(arm.modifier)
                .size(40.dp)
                .then(if (armed) Modifier.testTag(ConfirmTags.armed) else Modifier)
                .semantics {
                    contentDescription = if (armed) "${t.history.removePhoto} – ${t.tapAgainToConfirm}" else t.history.removePhoto
                    if (armed) stateDescription = t.tapAgainToConfirm
                }
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                    if (armed) { arm.disarm(); onRemove() }
                    else { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); arm.arm() }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(24.dp).background(c.background, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                LineIcon(Icon.Close, size = 16.dp, color = if (armed) c.armed else c.foreground)
            }
        }
    }
}
