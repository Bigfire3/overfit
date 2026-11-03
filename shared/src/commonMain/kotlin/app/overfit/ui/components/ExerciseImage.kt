package app.overfit.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overfit.domain.Exercise
import app.overfit.shared.resources.Res
import app.overfit.ui.theme.Theme
import coil3.compose.AsyncImage

/** Where the image of an exercise comes from. */
object ExerciseImages {
    /** Workout Guide frames are bundled SVGs (tinted when drawn). */
    fun isBundledVector(exercise: Exercise): Boolean = exercise.imageRef?.endsWith(".svg") == true

    /**
     * Coil model: URI of a bundled resource, or null (→ initials tile). Only bundled images exist since the
     * free-exercise-db photos were removed; nothing is loaded over the network.
     */
    fun model(exercise: Exercise): String? {
        val ref = exercise.imageRef ?: return null
        return if (ref.startsWith("http")) null else Res.getUri("files/$ref")
    }
}

/**
 * Still image of an exercise: Workout Guide SVG tinted in the foreground colour (black/white
 * design) or – without image or if loading fails – an initials tile.
 */
@Composable
fun ExerciseImage(exercise: Exercise, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val model = remember(exercise.id, exercise.imageRef) { ExerciseImages.model(exercise) }
    var failed by remember(model) { mutableStateOf(false) }
    val vector = ExerciseImages.isBundledVector(exercise)
    val shape = RoundedCornerShape(size / 6)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .border(0.5.dp, Theme.colors.hairline, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (model == null || failed) {
            Text(
                exercise.initials,
                style = Theme.type.label.copy(fontSize = (size.value * 0.34f).sp, fontWeight = FontWeight.SemiBold),
                tone = Tone.Muted,
            )
        } else {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = if (vector) ContentScale.Fit else ContentScale.Crop,
                colorFilter = if (vector) ColorFilter.tint(Theme.colors.foreground) else null,
                onError = { failed = true },
                modifier = Modifier.size(size).padding(if (vector) size / 12 else 0.dp),
            )
        }
    }
}
