package app.overfit.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.overfit.data.WorkoutPhoto
import app.overfit.domain.Language
import app.overfit.domain.Numbers
import app.overfit.domain.WeightUnit
import app.overfit.ui.components.Icon
import app.overfit.ui.components.LineIcon
import app.overfit.ui.components.Text
import app.overfit.ui.components.Tone
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import coil3.compose.AsyncImage
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToLong
import kotlin.time.Instant

/** Epoch ms in the system time zone. */
internal fun localDateTime(ms: Long) = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())

/** "14:05" */
internal fun clockTime(ms: Long): String {
    val t = localDateTime(ms)
    return "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
}

/** Volume in the user's unit with thousands separators: "3.450 kg" (DE) / "3,450 kg" (EN). */
internal fun formatVolume(kg: Double, unit: WeightUnit, language: Language): String {
    val value = if (unit == WeightUnit.KG) kg else kg * 2.2046226218
    val digits = value.roundToLong().toString()
    val sep = if (language == Language.DE) '.' else ','
    val grouped = digits.reversed().chunked(3).joinToString(sep.toString()).reversed()
    return "$grouped ${unit.symbol}"
}

internal fun formatKg(kg: Double, unit: WeightUnit, language: Language): String =
    "${Numbers.formatWeight(kg, unit, language)} ${unit.symbol}"

/** Square photo thumbnail, optionally with a remove button in the corner. */
@Composable
internal fun PhotoThumb(
    photo: WorkoutPhoto,
    description: String,
    size: Dp = 88.dp,
    onClick: (() -> Unit)? = null,
    removeLabel: String? = null,
    onRemove: (() -> Unit)? = null,
) {
    Box(Modifier.size(size)) {
        AsyncImage(
            model = photo.path,
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(4.dp))
                .background(Theme.colors.hairline)
                .then(
                    if (onClick != null) Modifier.clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
                    else Modifier,
                ),
        )
        if (onRemove != null && removeLabel != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(app.overfit.ui.theme.TouchTarget.min)
                    .semantics { contentDescription = removeLabel }
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(app.overfit.ui.theme.Sizes.badge).background(Theme.colors.background, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                    LineIcon(Icon.Close, size = app.overfit.ui.theme.IconSize.small)
                }
            }
        }
    }
}

/** Feeling 1-5 as five boxes; the selected one is inverted, tapping it again clears the choice. */
@Composable
internal fun FeelingPicker(value: Int?, label: (Int) -> String, onChange: (Int?) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.s)) {
        (1..5).forEach { n ->
            val active = value == n
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .background(if (active) Theme.colors.inverseBackground else Theme.colors.surfacePressed, RoundedCornerShape(8.dp))
                    .semantics { selected = active; contentDescription = label(n) }
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton) {
                        onChange(if (active) null else n)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(n.toString(), style = Theme.type.label, tone = if (active) Tone.Inverse else Tone.Muted)
            }
        }
    }
}
