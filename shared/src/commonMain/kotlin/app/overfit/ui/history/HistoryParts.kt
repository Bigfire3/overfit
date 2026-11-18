package app.overfit.ui.history

import androidx.compose.foundation.layout.padding
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.overfit.ui.components.Icon
import app.overfit.ui.components.RevealNotNull
import app.overfit.ui.components.Swap
import app.overfit.ui.components.motionColor
import app.overfit.ui.components.pressScale
import app.overfit.ui.components.rememberHaptics
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
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(app.overfit.ui.theme.Sizes.badge).background(Theme.colors.background, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                    LineIcon(Icon.Close, size = app.overfit.ui.theme.IconSize.small)
                }
            }
        }
    }
}

/**
 * Feeling 1-5 as five boxes; the selected one is inverted, tapping it again clears the choice. Fill and digit follow
 * the choice softly, each box gives way under the finger. [showMeaning] writes the chosen value under the boxes
 * ("Gefühl 4 von 5"), so the scale is readable without a screen reader.
 */
@Composable
internal fun FeelingPicker(value: Int?, label: (Int) -> String, showMeaning: Boolean = false, onChange: (Int?) -> Unit) {
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.s)) {
            (1..5).forEach { n ->
                val active = value == n
                val interaction = remember { MutableInteractionSource() }
                val fill = motionColor(if (active) Theme.colors.inverseBackground else Theme.colors.surfacePressed, "feelingFill")
                val digit = motionColor(if (active) Theme.colors.inverseForeground else Theme.colors.muted, "feelingDigit")
                Box(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .pressScale(interaction)
                        .drawBehind { drawRoundRect(fill.value, cornerRadius = CornerRadius(8.dp.toPx())) }
                        .semantics { selected = active; contentDescription = label(n) }
                        .clickable(interaction, indication = null, role = Role.RadioButton) {
                            haptics.tick()
                            onChange(if (active) null else n)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(n.toString(), style = Theme.type.label, color = { digit.value })
                }
            }
        }
        if (showMeaning) {
            // Visible twin of the boxes' screen-reader text; hidden from it, the selected box already says it.
            RevealNotNull(value) { v ->
                Swap(v, Modifier.fillMaxWidth().clearAndSetSemantics { }) { shown ->
                    Text(label(shown), Modifier.padding(top = Space.s), style = Theme.type.caption, tone = Tone.Muted)
                }
            }
        }
    }
}
