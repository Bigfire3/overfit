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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** Pill-shaped toggle for filters and single choices. Selected = inverted. */
@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 40.dp)
            .background(if (selected) Theme.colors.inverseBackground else Theme.colors.surface, RoundedCornerShape(50))
            .semantics { this.selected = selected }
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = Space.m, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Theme.type.caption, tone = if (selected) Tone.Inverse else Tone.Muted)
    }
}

/** Underlined search field with a leading search icon and a clear button. */
@Composable
fun SearchInput(value: String, onValueChange: (String) -> Unit, placeholder: String, clearLabel: String) {
    val style = Theme.type.body.copy(color = Theme.colors.foreground)
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineIcon(Icon.Search, size = 20.dp, color = Theme.colors.muted)
            Spacer(Modifier.width(Space.s))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(Theme.colors.foreground),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                modifier = Modifier.weight(1f).padding(vertical = Space.m),
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
                        .size(32.dp)
                        .semantics { contentDescription = clearLabel }
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { onValueChange("") },
                    contentAlignment = Alignment.Center,
                ) { LineIcon(Icon.Close, size = 18.dp, color = Theme.colors.muted) }
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
) {
    val style = Theme.type.label.copy(color = Theme.colors.foreground, textAlign = align)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(Theme.colors.foreground),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp)
                    .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
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

/** Multi-line, borderless text field with a hairline underneath (notes). */
@Composable
fun MultilineInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minHeight: androidx.compose.ui.unit.Dp = 96.dp,
) {
    val style = Theme.type.body.copy(color = Theme.colors.foreground)
    Column(modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = style,
            cursorBrush = SolidColor(Theme.colors.foreground),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            modifier = Modifier.fillMaxWidth().heightIn(min = minHeight).padding(vertical = Space.m),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) BasicText(placeholder, style = style.copy(color = Theme.colors.faint))
                    inner()
                }
            },
        )
        Hairline()
    }
}
