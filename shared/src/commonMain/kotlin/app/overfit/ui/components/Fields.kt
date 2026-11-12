package app.overfit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import app.overfit.ui.theme.TouchTarget
import app.overfit.ui.theme.Sizes
import app.overfit.ui.theme.IconSize

/** Shape of every chip (filter, choice, context, credit): a pill (UI audit 12.11.). */
val ChipShape = RoundedCornerShape(50)

/** Pill-shaped toggle for filters and single choices. Selected = inverted. */
@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(interaction)
            // 48 dp touch area around the 40 dp chip.
            .clickable(interaction, indication = null, role = Role.Checkbox, onClick = onClick)
            .padding(vertical = (TouchTarget.min - Sizes.chip) / 2)
            .heightIn(min = Sizes.chip)
            .background(if (selected) Theme.colors.inverseBackground else if (LocalInPanel.current) Theme.colors.surfacePressed else Theme.colors.surface, ChipShape)
            .semantics { this.selected = selected }
            .padding(horizontal = Space.m, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Theme.type.caption, tone = if (selected) Tone.Inverse else Tone.Muted)
    }
}

/** Underlined search field with a leading search icon and a clear button. */
@Composable
fun SearchInput(value: String, onValueChange: (String) -> Unit, placeholder: String, clearLabel: String) {
    val focusManager = LocalFocusManager.current
    val style = Theme.type.body.copy(color = Theme.colors.foreground)
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineIcon(Icon.Search, size = IconSize.inline, color = Theme.colors.muted)
            Spacer(Modifier.width(Space.s))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(Theme.colors.foreground),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier.weight(1f).textInputArea().padding(vertical = Space.m),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) BasicText(placeholder, style = style.copy(color = Theme.colors.faint))
                        inner()
                    }
                },
            )
            if (value.isNotEmpty()) {
                Box(
                    Modifier
                        .size(48.dp)
                        .semantics { contentDescription = clearLabel }
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { onValueChange("") },
                    contentAlignment = Alignment.Center,
                ) { LineIcon(Icon.Close, size = IconSize.small, color = Theme.colors.muted) }
            }
        }
        Hairline()
    }
}

/**
 * Compact underlined input for numbers in set rows (large, centred digits). The unit sits as a
 * placeholder while empty and as a small suffix once there is a value.
 */
@Composable
fun SmallInput(
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Number,
    align: TextAlign = TextAlign.Center,
    placeholder: String = unit,
    description: String? = null,
    /** > 1 for short texts (notes): the field grows instead of cutting the text off. */
    maxLines: Int = 1,
) {
    val style = Theme.type.label.copy(color = Theme.colors.foreground, textAlign = align)
    val focusManager = LocalFocusManager.current
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = maxLines == 1,
                maxLines = maxLines,
                textStyle = style,
                cursorBrush = SolidColor(Theme.colors.foreground),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.textInputArea()
                    .weight(1f)
                    // The field itself is the 48 dp touch target (was 10 dp padding around the text line, UI audit 12.11.).
                    .heightIn(min = TouchTarget.min)
                    // Screen readers name the field even when it is empty (the grey placeholder is only drawn).
                    .semantics { contentDescription = description ?: placeholder },
                decorationBox = { inner ->
                    Box(contentAlignment = if (align == TextAlign.Center) Alignment.Center else Alignment.CenterStart) {
                        if (value.isEmpty()) BasicText(placeholder, style = style.copy(color = Theme.colors.faint))
                        inner()
                    }
                },
            )
            if (value.isNotEmpty() && unit.isNotEmpty()) Text(unit, style = Theme.type.caption, tone = Tone.Muted)
        }
        Hairline()
    }
}

/** Multi-line text field (notes) as its own grey area, so it reads as an input. */
@Composable
fun MultilineInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minHeight: androidx.compose.ui.unit.Dp = 96.dp,
) {
    val c = Theme.colors
    val style = Theme.type.body.copy(color = c.foreground)
    val shape = RoundedCornerShape(16.dp)
    val inPanel = LocalInPanel.current
    // Outer box carries the caller's modifier (test tags, weights), the field sits inside it as before.
    Box(modifier.fillMaxWidth()) { BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style,
        cursorBrush = SolidColor(c.foreground),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
        modifier = Modifier
            .textInputArea()
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .semantics { contentDescription = placeholder }
            .then(if (inPanel) Modifier.background(c.surface) else Modifier.clip(shape).background(c.surface))
            .padding(Space.m),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) BasicText(placeholder, style = style.copy(color = c.muted))
                inner()
            }
        },
    ) }
}
