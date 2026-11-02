package app.overfit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme

enum class Tone { Default, Muted, Faint, Inverse }

@Composable
private fun Tone.color(): Color = when (this) {
    Tone.Default -> Theme.colors.foreground
    Tone.Muted -> Theme.colors.muted
    Tone.Faint -> Theme.colors.faint
    Tone.Inverse -> Theme.colors.inverseForeground
}

// ---------------------------------------------------------------------------
// Text

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Theme.type.body,
    tone: Tone = Tone.Default,
    align: TextAlign? = null,
) {
    BasicText(text, modifier, style = style.copy(color = tone.color(), textAlign = align ?: TextAlign.Unspecified))
}

@Composable
fun Title(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.semantics { heading() }, style = Theme.type.title)
}

// ---------------------------------------------------------------------------
// Screen

/**
 * Base layout: safe-area aware, 24 dp side gutter, optional header and a footer
 * pinned to the bottom above the keyboard (for the one primary action).
 */
@Composable
fun Screen(
    modifier: Modifier = Modifier,
    scroll: Boolean = false,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Theme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .padding(horizontal = Space.l),
    ) {
        Spacer(Modifier.height(Space.m))
        header?.invoke()
        val body = Modifier.weight(1f).fillMaxWidth()
        Column(if (scroll) body.verticalScroll(rememberScrollState()) else body) {
            content()
            if (scroll) Spacer(Modifier.height(Space.xxl))
        }
        if (footer != null) {
            Column(
                Modifier.fillMaxWidth().padding(top = Space.m, bottom = Space.m),
                verticalArrangement = Arrangement.spacedBy(Space.m),
                content = footer,
            )
        }
    }
}

/** Header row with a back button. */
@Composable
fun BackHeader(label: String, onBack: () -> Unit) {
    Box(Modifier.height(40.dp).fillMaxWidth().padding(bottom = 0.dp), contentAlignment = Alignment.CenterStart) {
        IconButton(Icon.Back, label, onBack)
    }
    Spacer(Modifier.height(Space.l))
}

// ---------------------------------------------------------------------------
// Buttons

@Composable
private fun Modifier.pressable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    haptic: Boolean = false,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    return this
        .alpha(if (pressed) 0.7f else 1f)
        .clickable(interaction, indication = null, enabled = enabled, role = role) {
            if (haptic) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/** The one primary action on a screen: inverted, full width, pill. */
@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, enabled: Boolean = true, loading: Boolean = false) {
    val active = enabled && !loading
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.25f)
            .background(Theme.colors.inverseBackground, RoundedCornerShape(50))
            .pressable(enabled = active, haptic = true, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (loading) "…" else label, style = Theme.type.label, tone = Tone.Inverse)
    }
}

@Composable
fun TextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Muted,
) {
    Text(label, modifier.pressable(onClick = onClick).padding(vertical = Space.xs), style = Theme.type.label, tone = tone)
}

@Composable
fun IconButton(icon: Icon, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .semantics { contentDescription = label }
            .pressable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) { LineIcon(icon) }
}

// ---------------------------------------------------------------------------
// Inputs

/** Borderless, large input: the text itself is the UI. A suffix sits right next to the value. */
@Composable
fun BigInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    onDone: () -> Unit = {},
) {
    val style = Theme.type.title.copy(color = Theme.colors.foreground)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(Theme.colors.foreground),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            keyboardActions = KeyboardActions(onDone = { onDone() }, onNext = { onDone() }),
            modifier = if (suffix != null) Modifier.width(IntrinsicTextWidth(value.ifEmpty { placeholder }, style)) else Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) BasicText(placeholder, style = style.copy(color = Theme.colors.faint))
                    inner()
                }
            },
        )
        if (suffix != null) {
            Spacer(Modifier.width(Space.s))
            Text(suffix, style = Theme.type.title, tone = Tone.Muted)
        }
    }
}

@Composable
fun KeyInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UnderlinedInput(value, onValueChange, placeholder, modifier, secret = true, onDone = onDone)
}

@Composable
private fun UnderlinedInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier,
    secret: Boolean,
    onDone: () -> Unit,
) {
    val style = Theme.type.body.copy(color = Theme.colors.foreground)
    Column(modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(Theme.colors.foreground),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth().padding(vertical = Space.m),
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

/** Right-aligned inline input for settings rows. */
@Composable
fun RowInput(
    value: String,
    onValueChange: (String) -> Unit,
    onCommit: () -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = Theme.type.body.copy(color = Theme.colors.muted, textAlign = TextAlign.End),
        cursorBrush = SolidColor(Theme.colors.foreground),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        keyboardActions = KeyboardActions(onDone = { onCommit() }),
        modifier = Modifier.widthIn(min = 120.dp).onFocusLost(onCommit),
    )
}

// ---------------------------------------------------------------------------
// Segmented control

@Composable
fun <T> Segmented(value: T, options: List<Pair<T, String>>, onChange: (T) -> Unit) {
    Row(
        Modifier
            .border(0.5.dp, Theme.colors.hairline, RoundedCornerShape(50))
            .padding(2.dp),
    ) {
        options.forEach { (option, label) ->
            val active = option == value
            Box(
                Modifier
                    .background(if (active) Theme.colors.inverseBackground else Color.Transparent, RoundedCornerShape(50))
                    .semantics { selected = active }
                    .pressable(role = Role.Tab, haptic = !active) { onChange(option) }
                    .padding(horizontal = Space.m, vertical = 6.dp),
            ) {
                Text(label, style = Theme.type.caption, tone = if (active) Tone.Inverse else Tone.Muted)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Lists

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        Modifier.padding(top = Space.xl, bottom = Space.s),
        style = Theme.type.caption,
        tone = Tone.Muted,
    )
}

@Composable
fun Hairline() {
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(Theme.colors.hairline))
}

/** A list row separated by a hairline. No cards, no shadows. */
@Composable
fun ListRow(
    label: String,
    tone: Tone = Tone.Default,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, Modifier.weight(1f, fill = false), tone = tone)
            if (trailing != null) {
                Spacer(Modifier.width(Space.m))
                trailing()
            }
        }
        Hairline()
    }
}

@Composable
fun ListRow(label: String, value: String, tone: Tone = Tone.Default, onClick: (() -> Unit)? = null) {
    ListRow(label, tone, onClick) { Text(value, tone = Tone.Muted) }
}

// ---------------------------------------------------------------------------
// Progress

@Composable
fun Progress(step: Int, total: Int) {
    Row(Modifier.fillMaxWidth().padding(bottom = Space.xl), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        repeat(total) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(if (i < step) Theme.colors.foreground else Theme.colors.hairline, RoundedCornerShape(1.dp)),
            )
        }
    }
}
