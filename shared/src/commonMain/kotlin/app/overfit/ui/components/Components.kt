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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
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
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text,
        modifier,
        style = style.copy(color = tone.color(), textAlign = align ?: TextAlign.Unspecified),
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
        maxLines = maxLines,
    )
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
    /** Dense layout for the running workout: no top gap, tighter footer. */
    compact: Boolean = false,
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
        Spacer(Modifier.height(if (compact) Space.xs else Space.m))
        header?.invoke()
        val body = Modifier.weight(1f).fillMaxWidth()
        Column(if (scroll) body.verticalScroll(rememberScrollState()) else body) {
            content()
            if (scroll) Spacer(Modifier.height(Space.xxl))
        }
        if (footer != null) {
            Column(
                Modifier.fillMaxWidth().padding(top = if (compact) Space.s else Space.m, bottom = if (compact) Space.s else Space.m),
                verticalArrangement = Arrangement.spacedBy(if (compact) Space.s else Space.m),
                content = footer,
            )
        }
    }
}

/** Header row with a back button. */
@Composable
fun BackHeader(label: String, onBack: () -> Unit) {
    Box(Modifier.height(44.dp).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
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

/** Press feedback for whole rows: grey [surface] fill while pressed. */
@Composable
fun Modifier.pressableRow(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    return this
        .background(if (pressed) Theme.colors.surface else Color.Transparent)
        .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
}

/** Standard button height; [ButtonHeight.compact] for inline actions. Touch targets stay >= 44 dp. */
object ButtonHeight {
    val standard = 48.dp
    val compact = 40.dp
}

/** Meaning of a button in its confirm state: red = destructive, green = positive (done, saved). */
enum class Confirm { Destructive, Positive }

@Composable
private fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    compact: Boolean,
    haptic: Boolean,
    fill: Color,
    pressedFill: Color,
    contentTone: Color,
    border: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .heightIn(min = if (compact) ButtonHeight.compact else ButtonHeight.standard)
            .alpha(if (enabled) 1f else 0.3f)
            .background(if (pressed) pressedFill else fill, shape)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button) {
                if (haptic) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = Space.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        BasicText(label, style = Theme.type.label.copy(color = contentTone), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) { Spacer(Modifier.width(Space.s)); trailing() }
    }
}

/** The one primary action on a screen: inverted, full width, pill, 48 dp. */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth(),
    compact: Boolean = false,
) {
    val c = Theme.colors
    PillButton(
        if (loading) "…" else label, onClick, modifier, enabled && !loading, compact, haptic = true,
        fill = c.inverseBackground, pressedFill = c.inverseBackground.copy(alpha = 0.75f), contentTone = c.inverseForeground,
    )
}

/** Second-most important action: grey [surface] fill, clearly a button. */
@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val c = Theme.colors
    PillButton(label, onClick, modifier, enabled, compact, haptic = false, fill = c.surface, pressedFill = c.surfacePressed, contentTone = c.foreground)
}

/** Destructive action (delete, discard): [negativeSubtle] fill, red label. */
@Composable
fun DestructiveButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val c = Theme.colors
    PillButton(label, onClick, modifier, enabled, compact, haptic = false, fill = c.negativeSubtle, pressedFill = c.negativeSubtle.copy(alpha = 0.6f), contentTone = c.negative)
}

/**
 * Confirm pattern: first tap changes the text ([confirmLabel]) and the colours, the second tap runs
 * [onConfirm]; without a second tap the button resets after 4 s. [kind] picks red (destructive) or
 * green (positive) for the confirm state.
 */
@Composable
fun ConfirmButton(
    label: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    kind: Confirm = Confirm.Destructive,
    compact: Boolean = false,
    enabled: Boolean = true,
    /** Idle look: secondary (grey pill) or plain underlined text. */
    textOnly: Boolean = false,
    /** Lets a crowded parent make room while the longer confirm text shows. */
    onConfirmingChange: (Boolean) -> Unit = {},
) {
    var confirming by remember { mutableStateOf(false) }
    LaunchedEffect(confirming) {
        onConfirmingChange(confirming)
        if (confirming) { delay(4000); confirming = false }
    }
    val c = Theme.colors
    val action = { if (confirming) { confirming = false; onConfirm() } else confirming = true }
    when {
        confirming -> {
            val (fill, tone) = if (kind == Confirm.Destructive) c.negativeSubtle to c.negative else c.positiveSubtle to c.positive
            PillButton(confirmLabel, action, modifier, enabled, compact, haptic = true, fill = fill, pressedFill = fill.copy(alpha = 0.6f), contentTone = tone, border = tone)
        }
        textOnly -> TertiaryButton(label, action, modifier)
        else -> SecondaryButton(label, action, modifier, enabled, compact)
    }
}

/** Text link: underlined, so it reads as clickable; at least 44 dp tall. Optional arrow. */
@Composable
fun TertiaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Default,
    arrow: Boolean = false,
) {
    Row(
        modifier.heightIn(min = 44.dp).pressable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            label,
            style = Theme.type.label.copy(color = tone.color(), textDecoration = TextDecoration.Underline),
        )
        if (arrow) { Spacer(Modifier.width(Space.xs)); LineIcon(Icon.ChevronRight, size = 18.dp, color = tone.color()) }
    }
}

@Composable
fun TextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Default,
) {
    TertiaryButton(label, onClick, modifier, tone)
}

@Composable
fun IconButton(icon: Icon, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
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
    textStyle: TextStyle = Theme.type.title,
) {
    val style = textStyle.copy(color = Theme.colors.foreground)
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
            Text(suffix, style = textStyle, tone = Tone.Muted)
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
            .background(Theme.colors.surface, RoundedCornerShape(50))
            .padding(3.dp),
    ) {
        options.forEach { (option, label) ->
            val active = option == value
            Box(
                Modifier
                    .background(if (active) Theme.colors.inverseBackground else Color.Transparent, RoundedCornerShape(50))
                    .semantics { selected = active }
                    .pressable(role = Role.Tab, haptic = !active) { onChange(option) }
                    .padding(horizontal = Space.m, vertical = 9.dp),
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
    Column(if (onClick != null) Modifier.pressableRow(onClick) else Modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, Modifier.weight(1f, fill = false), tone = tone)
            if (trailing != null) {
                Spacer(Modifier.width(Space.m))
                trailing()
            } else if (onClick != null) {
                Spacer(Modifier.width(Space.m))
                LineIcon(Icon.ChevronRight, size = 20.dp, color = Theme.colors.muted)
            }
        }
        Hairline()
    }
}

@Composable
fun ListRow(label: String, value: String, tone: Tone = Tone.Default, onClick: (() -> Unit)? = null) {
    ListRow(label, tone, onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, tone = Tone.Muted)
            if (onClick != null) { Spacer(Modifier.width(Space.xs)); LineIcon(Icon.ChevronRight, size = 20.dp, color = Theme.colors.muted) }
        }
    }
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
