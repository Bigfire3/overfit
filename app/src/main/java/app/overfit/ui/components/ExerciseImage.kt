package app.overfit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overfit.data.images.ExerciseImages
import app.overfit.domain.Exercise
import coil3.compose.AsyncImage

/**
 * Standbild einer Übung: Workout-Guide-SVG in der Theme-Farbe eingefärbt, free-exercise-db-Foto
 * oder – ohne Bild bzw. bei Ladefehler – eine Initialen-Kachel.
 */
@Composable
fun ExerciseImage(exercise: Exercise, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val context = LocalContext.current
    val model = remember(exercise.id, exercise.imageRef) { ExerciseImages.model(context, exercise) }
    var failed by remember(model) { mutableStateOf(false) }
    val shape = RoundedCornerShape(size / 6)
    val vector = ExerciseImages.isBundledVector(exercise)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(if (vector || model == null || failed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (model == null || failed) {
            InitialsTile(exercise.initials, size)
        } else {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = if (vector) ContentScale.Fit else ContentScale.Crop,
                colorFilter = if (vector) ColorFilter.tint(MaterialTheme.colorScheme.primary) else null,
                onError = { failed = true },
                modifier = Modifier
                    .size(size)
                    .padding(if (vector) size / 14 else 0.dp),
            )
        }
    }
}

@Composable
private fun InitialsTile(initials: String, size: Dp) {
    Text(
        initials,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        fontSize = (size.value * 0.36f).sp,
    )
}
