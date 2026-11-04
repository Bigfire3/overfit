package app.overfit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
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

/**
 * Header row with a back button. Detail screens put their icon actions (share, delete, edit) into
 * [actions]: always top right, as [HeaderIconButton] / [HeaderDeleteButton]. Edit modes show none.
 */
@Composable
fun BackHeader(label: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.height(44.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(Icon.Back, label, onBack)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs), content = actions)
    }
    Spacer(Modifier.height(Space.l))
}

/** Icon action in a [BackHeader] (share, edit): 44 dp touch target, [label] for screen readers. */
@Composable
fun HeaderIconButton(icon: Icon, label: String, onClick: () -> Unit, color: Color = Theme.colors.foreground) {
    Box(
        Modifier.size(44.dp).semantics { contentDescription = label }.pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { LineIcon(icon, color = color) }
}

/**
 * Delete in a [BackHeader]: a bin icon; the first tap turns it into a red "Delete?" pill
 * ([confirmLabel]), the second tap deletes. Same arming rules as [ConfirmButton].
 */
@Composable
fun HeaderDeleteButton(
    label: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onConfirmingChange: (Boolean) -> Unit = {},
    color: Color = Theme.colors.foreground,
) {
    val arm = rememberConfirmArm(onConfirmingChange)
    if (arm.armed) {
        val c = Theme.colors
        PillButton(
            confirmLabel, { arm.disarm(); onConfirm() }, arm.modifier, enabled = true, compact = true, haptic = true,
            fill = c.negativeSubtle, pressedFill = c.negativeSubtle.copy(alpha = 0.6f), contentTone = c.negative,
        )
    } else {
        HeaderIconButton(Icon.Delete, label, { arm.arm() }, color)
    }
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
 * [onConfirm]. The armed state ends after 4 s, when another confirm button is armed, or on a tap
 * anywhere else ([ConfirmArea]). [kind] picks red (destructive) or green (positive), without a border.
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
    /** Lets a crowded parent make room while the longer confirm text shows. */
    onConfirmingChange: (Boolean) -> Unit = {},
) {
    val arm = rememberConfirmArm(onConfirmingChange)
    val c = Theme.colors
    if (arm.armed) {
        val (fill, tone) = if (kind == Confirm.Destructive) c.negativeSubtle to c.negative else c.positiveSubtle to c.positive
        PillButton(
            confirmLabel, { arm.disarm(); onConfirm() }, modifier.then(arm.modifier), enabled, compact, haptic = true,
            fill = fill, pressedFill = fill.copy(alpha = 0.6f), contentTone = tone,
        )
    } else {
        SecondaryButton(label, { arm.arm() }, modifier, enabled, compact)
    }
}

/**
 * Small inline action (add set, retry, cancel …): a compact grey pill that is as wide as its text.
 * Replaces the former underlined text links: buttons look like buttons. [tone] Muted = less important.
 */
@Composable
fun SmallButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Default,
) {
    val c = Theme.colors
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .heightIn(min = ButtonHeight.compact)
            .background(if (pressed) c.surfacePressed else c.surface, shape)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.m),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = Theme.type.label.copy(color = tone.color()), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
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
    val focusManager = LocalFocusManager.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(Theme.colors.foreground),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); onDone() }),
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
    val focusManager = LocalFocusManager.current
    Column(modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(Theme.colors.foreground),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text, autoCorrectEnabled = false, imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); onDone() }),
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
    // Done removes the cursor; losing focus commits the value.
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = Theme.type.body.copy(color = Theme.colors.muted, textAlign = TextAlign.End),
        cursorBrush = SolidColor(Theme.colors.foreground),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
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

/**
 * A list row with a square checkbox at the end (e.g. "Sauna" after the workout). Unchecked = hairline outline,
 * checked = filled (inverted) with a check mark. The whole row toggles.
 */
@Composable
fun CheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Column(
        Modifier.toggleable(
            value = checked,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Checkbox,
            onValueChange = onCheckedChange,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(Space.m))
            val shape = RoundedCornerShape(4.dp)
            Box(
                Modifier
                    .size(22.dp)
                    .then(
                        if (checked) Modifier.background(Theme.colors.inverseBackground, shape)
                        else Modifier.border(1.dp, Theme.colors.muted, shape),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) LineIcon(Icon.Check, size = 18.dp, color = Theme.colors.inverseForeground)
            }
        }
        Hairline()
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
