package app.overfit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.draw.clipToBounds
import app.overfit.ui.theme.Space
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.produceState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.overfit.i18n.LocalStrings
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

    /** Workout Guide slug of a bundled frame-1 image (`catalog/wg/<slug>.svg`), else null. */
    fun slug(imageRef: String?): String? =
        imageRef?.takeIf { it.startsWith(WG_DIR) && it.endsWith(".svg") }?.removePrefix(WG_DIR)?.removeSuffix(".svg")

    /** File (relative to `files/`) of source frame [frame] of a Workout Guide image: `slug.svg` (1) or `slug-N.svg`. */
    fun frameRef(imageRef: String, frame: Int): String =
        if (frame == 1) imageRef else imageRef.removeSuffix(".svg") + "-$frame.svg"

    /**
     * Start and end pose of [exercise] for the detail view, in that order (frame files and crop), or null when it
     * shows a single image (holds, static stretches, custom exercises, not loaded yet).
     */
    fun poses(exercise: Exercise, pairs: Map<String, PosePair>?): List<Pose>? {
        val ref = exercise.imageRef ?: return null
        val pair = pairs?.get(slug(ref) ?: return null) ?: return null
        return pair.frames.mapIndexed { i, frame ->
            Pose(frameRef(ref, frame), pair.origin[i][0], pair.origin[i][1], pair.size[0], pair.size[1])
        }
    }

    const val CANVAS = 512f
    private const val WG_DIR = "catalog/wg/"
}

/** One pose: frame file plus the crop (SVG units of the 512 x 512 canvas) that shows it. */
data class Pose(val ref: String, val x: Float, val y: Float, val width: Float, val height: Float)

/** Entry of files/catalog/poses.json (tools/catalog/import_catalog.py): start/end frame, shared crop size, origins. */
@Serializable
data class PosePair(val frames: List<Int>, val size: List<Float>, val origin: List<List<Float>>)

/** Loads files/catalog/poses.json once (small, about 25 KB) and keeps it for the app's lifetime. */
object ExercisePoses {
    private var cache: Map<String, PosePair>? = null
    private val json = Json { ignoreUnknownKeys = true }

    fun cached(): Map<String, PosePair>? = cache

    fun parse(bytes: ByteArray): Map<String, PosePair> = json.decodeFromString(bytes.decodeToString())

    suspend fun load(): Map<String, PosePair> =
        cache ?: parse(Res.readBytes("files/catalog/poses.json")).also { cache = it }
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
 * Large image for the detail screen, in a [Panel]: start and end pose next to each other (stacked for wide, lying
 * poses), both crops the same size and scale with the figure standing on the same line (data from poses.json), small
 * "Start"/"End" captions. Holds, static stretches and anything without a pose pair show frame 1 as one still image;
 * custom exercises without an image get the large initials tile. Theme colour, no animation.
 */
@Composable
fun ExerciseHero(exercise: Exercise, modifier: Modifier = Modifier) {
    if (!ExerciseImages.isBundledVector(exercise) || ExerciseImages.model(exercise) == null) {
        BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ExerciseImage(exercise, size = maxWidth)
        }
        return
    }
    val t = LocalStrings.current
    val pairs by produceState(ExercisePoses.cached()) { value = runCatching { ExercisePoses.load() }.getOrElse { emptyMap() } }
    if (pairs == null) {
        // poses.json not read yet (first detail view after start, a few ms): keep the space, avoid a jump.
        Box(modifier.fillMaxWidth().aspectRatio(1f))
        return
    }
    val poses = remember(exercise.id, exercise.imageRef, pairs) { ExerciseImages.poses(exercise, pairs) }
    Panel(modifier) {
        if (poses == null) {
            PanelItem {
                AsyncImage(
                    model = ExerciseImages.model(exercise),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(Theme.colors.foreground),
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
            }
            return@Panel
        }
        val labels = listOf(t.poseStart, t.poseEnd)
        val wide = poses[0].width / poses[0].height > WIDE_POSE
        if (wide) {
            poses.forEachIndexed { i, pose -> PoseCell(pose, labels[i], Modifier.fillMaxWidth()) }
        } else {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                poses.forEachIndexed { i, pose -> PoseCell(pose, labels[i], Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

/** Crops wider than this (width / height) are stacked instead of side by side, so lying poses stay large enough. */
private const val WIDE_POSE = 1.4f

@Composable
private fun PoseCell(pose: Pose, label: String, modifier: Modifier) {
    Column(
        modifier.background(Theme.colors.surface).padding(Space.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(pose.width / pose.height).clipToBounds()) {
            // Draw the whole 512 x 512 frame at the crop's scale and shift it so only the crop is visible.
            val scale = maxWidth / pose.width
            AsyncImage(
                model = remember(pose.ref) { Res.getUri("files/${pose.ref}") },
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                colorFilter = ColorFilter.tint(Theme.colors.foreground),
                modifier = Modifier
                    .offset(x = -scale * pose.x, y = -scale * pose.y)
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(scale * ExerciseImages.CANVAS),
            )
        }
        Spacer(Modifier.height(Space.s))
        Text(label, style = Theme.type.caption, tone = Tone.Muted)
    }
}
