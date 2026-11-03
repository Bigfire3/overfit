package app.overfit.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.overfit.i18n.LocalStrings
import kotlinx.coroutines.delay
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
    fun model(exercise: Exercise): String? = uriOf(exercise.imageRef)

    private fun uriOf(ref: String?): String? =
        if (ref == null || ref.startsWith("http")) null else Res.getUri("files/$ref")

    /**
     * Files (relative to `files/`) of an exercise's animation frames, in file order: `slug.svg` (frame 1, also the still
     * image), `slug-2.svg`, `slug-3.svg`. Only bundled Workout Guide vectors have more than one frame (import script
     * writes all three for every exercise); anything else is a single image or none.
     */
    fun frameRefs(imageRef: String?): List<String> {
        if (imageRef == null || !imageRef.endsWith(".svg")) return listOfNotNull(imageRef)
        val base = imageRef.removeSuffix(".svg")
        return listOf(imageRef) + (2..FRAME_COUNT).map { "$base-$it.svg" }
    }

    /** Frame index per animation step: 1→2→3→2→1 … (ping-pong), or a still image if there is only one frame. */
    fun sequence(frameCount: Int): List<Int> =
        if (frameCount <= 1) listOf(0) else (0 until frameCount) + (frameCount - 2 downTo 1)

    fun frameModels(exercise: Exercise): List<String> = frameRefs(exercise.imageRef).mapNotNull(::uriOf)

    const val FRAME_COUNT = 3
    const val FRAME_MILLIS = 700L
}

/**
 * Still image of an exercise: Workout Guide SVG tinted in the foreground colour (black/white
 * design) or – without image or if loading fails – an initials tile.
 */
@Composable
fun ExerciseImage(exercise: Exercise, modifier: Modifier = Modifier, size: Dp = 48.dp, onClick: (() -> Unit)? = null) {
    val t = LocalStrings.current
    val model = remember(exercise.id, exercise.imageRef) { ExerciseImages.model(exercise) }
    var failed by remember(model) { mutableStateOf(false) }
    val vector = ExerciseImages.isBundledVector(exercise)
    val shape = RoundedCornerShape(size / 6)
    // Tap area at least 48 dp, even if the image itself is smaller.
    Box(
        modifier
            .let {
                if (onClick == null) it.size(size) else it
                    .size(maxOf(size, 48.dp))
                    .semantics { contentDescription = t.exerciseDetails(exercise.name) }
                    .pressableRow(onClick)
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(size).clip(shape).border(0.5.dp, Theme.colors.hairline, shape),
            contentAlignment = Alignment.Center,
        ) {
            ExerciseImageContent(exercise, model, vector, size, failed) { failed = true }
        }
    }
}

@Composable
private fun ExerciseImageContent(exercise: Exercise, model: String?, vector: Boolean, size: Dp, failed: Boolean, onFail: () -> Unit) {
    run {
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
                onError = { onFail() },
                modifier = Modifier.size(size).padding(if (vector) size / 12 else 0.dp),
            )
        }
    }
}

/**
 * Large image for the detail screen: full width, square. With several frames it loops 1→2→3→2→1 (hard switch,
 * 700 ms per frame, theme colour); without more frames, or when [animate] is off, it is a still image of frame 1.
 * All frames stay composed (only one visible) so a switch never waits for decoding.
 */
@Composable
fun ExerciseHero(exercise: Exercise, modifier: Modifier = Modifier, animate: Boolean = true) {
    val frames = remember(exercise.id, exercise.imageRef) { ExerciseImages.frameModels(exercise) }
    val vector = ExerciseImages.isBundledVector(exercise)
    if (frames.isEmpty() || !vector) {
        BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ExerciseImage(exercise, size = maxWidth)
        }
        return
    }
    val steps = remember(frames.size) { ExerciseImages.sequence(frames.size) }
    var step by remember(frames) { mutableStateOf(0) }
    LaunchedEffect(frames, animate) {
        step = 0
        if (!animate || steps.size < 2) return@LaunchedEffect
        while (true) {
            delay(ExerciseImages.FRAME_MILLIS)
            step = (step + 1) % steps.size
        }
    }
    val current = steps[step]
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier.fillMaxWidth().aspectRatio(1f).clip(shape).border(0.5.dp, Theme.colors.hairline, shape),
        contentAlignment = Alignment.Center,
    ) {
        frames.forEachIndexed { i, model ->
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(Theme.colors.foreground),
                modifier = Modifier.fillMaxSize().padding(24.dp).alpha(if (i == current) 1f else 0f),
            )
        }
    }
}
