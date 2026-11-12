package app.overfit.ui.components

import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.BiasAlignment
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import app.overfit.i18n.LocalStrings
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overfit.ui.theme.IconSize
import app.overfit.ui.theme.Sizes
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.TouchTarget
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
        val footerModifier = Modifier.fillMaxWidth().padding(top = if (compact) Space.s else Space.m, bottom = if (compact) Space.s else Space.m)
        val footerSpacing = Arrangement.spacedBy(if (compact) Space.s else Space.m)
        if (footer != null && scroll) {
            // Floating main button: no bar. The content scrolls underneath (a short fade keeps the label readable), the
            // trailing spacer in the scroll content is as tall as the footer so nothing stays covered at the end.
            val density = LocalDensity.current
            var footerHeight by remember { mutableStateOf(0.dp) }
            Box(body) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    content()
                    Spacer(Modifier.height(maxOf(footerHeight, Space.xxl)))
                }
                val bg = Theme.colors.background
                Column(
                    Modifier.align(Alignment.BottomCenter)
                        .onSizeChanged { footerHeight = with(density) { it.height.toDp() } }
                        .drawBehind {
                            // One soft gradient over the whole footer area (no hard edge, no flat bar).
                            drawRect(Brush.verticalGradient(listOf(Color.Transparent, bg.copy(alpha = 0.85f)), startY = 0f, endY = size.height))
                        }
                        .padding(top = 24.dp)
                        .then(footerModifier),
                    verticalArrangement = footerSpacing,
                    content = footer,
                )
            }
        } else {
            Column(if (scroll) body.verticalScroll(rememberScrollState()) else body) {
                content()
                if (scroll) Spacer(Modifier.height(Space.xxl))
            }
            if (footer != null) Column(footerModifier, verticalArrangement = footerSpacing, content = footer)
        }
    }
}

/** Test tag of the right-hand action group of every header ([HeaderActions]). */
const val HEADER_ACTIONS_TAG = "header:actions"

/** At most this many tappable things stand in a header's action group; rarer actions go into the ⋮ menu. */
const val MAX_HEADER_ACTIONS = 3

/**
 * Header row with a back button. Detail screens put their actions into [HeaderActions] (via [coach], [menu] and
 * [actions]): always top right, coach icon in the corner. Edit modes show none.
 */
@Composable
fun BackHeader(
    label: String,
    onBack: () -> Unit,
    coach: (() -> Unit)? = null,
    menu: List<MenuItem> = emptyList(),
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(Modifier.height(TouchTarget.min).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(Icon.Back, label, onBack)
        Spacer(Modifier.weight(1f))
        HeaderActions(coach = coach, menu = menu, actions = actions)
    }
    Spacer(Modifier.height(Space.l))
}

/**
 * The right-hand side of every header, one order everywhere (UI audit 12.11.):
 * [frequent icon actions] [⋮ with the rare ones] [coach]. The coach icon always sits in the top right corner,
 * so it is in the same place on every screen. Each icon is a [TouchTarget.min] square without gaps (the icons are
 * [IconSize.action], so 24 dp of air lies between two of them). At most [MAX_HEADER_ACTIONS] (test).
 */
@Composable
fun HeaderActions(
    coach: (() -> Unit)? = null,
    menu: List<MenuItem> = emptyList(),
    menuLabel: String = LocalStrings.current.moreOptions,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.testTag(HEADER_ACTIONS_TAG), verticalAlignment = Alignment.CenterVertically) {
        actions()
        if (menu.isNotEmpty()) MoreMenu(menuLabel, menu)
        if (coach != null) CoachIconButton(coach)
    }
}

/** Icon action in a header (share, edit): [TouchTarget.min] square, [IconSize.action] icon, [label] for screen readers. */
@Composable
fun HeaderIconButton(icon: Icon, label: String, onClick: () -> Unit, color: Color = Theme.colors.foreground) {
    Box(
        Modifier.size(TouchTarget.min).semantics { contentDescription = label }.pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { LineIcon(icon, size = IconSize.action, color = color) }
}

/**
 * The one way into the AI from a screen (Fabian 8.11.: no scattered "Fill in with AI" buttons): the coach icon top
 * right, the same everywhere. It opens the coach over the screen with the screen's context attached.
 * Use it through [HeaderActions] (`coach = …`), which puts it in the corner.
 */
@Composable
fun CoachIconButton(onClick: () -> Unit) {
    val label = app.overfit.i18n.coachChatStringsFor(app.overfit.i18n.LocalFuelStrings.current.language).openCoach
    Box(Modifier.testTag("coach:open")) { HeaderIconButton(Icon.Coach, label, onClick) }
}

/**
 * Delete in a [BackHeader], two-tap pattern: the first tap turns the bin red (armed, screen readers hear
 * "Löschen – zum Bestätigen erneut tippen"), a second tap within 3 s runs [onConfirm]. A tap elsewhere,
 * scrolling or the timeout disarm it ([ConfirmArea]). No popup, no extra text.
 */
@Composable
fun HeaderDeleteButton(
    label: String,
    onConfirm: () -> Unit,
    color: Color = Theme.colors.foreground,
) {
    val arm = rememberConfirmArm()
    val haptics = LocalHapticFeedback.current
    val again = LocalStrings.current.tapAgainToConfirm
    val armed = arm.armed
    Box(
        Modifier
            .then(arm.modifier)
            .size(TouchTarget.min)
            .then(if (armed) Modifier.testTag(ConfirmTags.armed) else Modifier)
            .semantics {
                contentDescription = if (armed) "$label – $again" else label
                if (armed) stateDescription = again
            }
            .pressable {
                if (armed) { arm.disarm(); onConfirm() }
                else { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); arm.arm() }
            },
        contentAlignment = Alignment.Center,
    ) { LineIcon(Icon.Delete, size = IconSize.action, color = if (armed) Theme.colors.armed else color) }
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
    val haptics = LocalHapticFeedback.current
    return this
        .pressScale(interaction, PressScale.icon)
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
    val pressedFill = if (LocalInPanel.current) Theme.colors.surfacePressed else Theme.colors.surface
    return this
        .background(if (pressed) pressedFill else Color.Transparent)
        .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
}

/** Scale factors of [pressScale]: big buttons and cards shrink a little, small icons a bit more. */
object PressScale {
    const val button = 0.96f
    const val card = 0.98f
    const val icon = 0.88f
}

/**
 * Tap feedback of every tappable button and card (no ripple, no colour change): while the finger is down
 * the element shrinks to [scale] in ~100 ms and springs back on release. The system animation scale (0 =
 * "remove animations") is respected by Compose itself: the animation then jumps instead of running.
 */
@Composable
fun Modifier.pressScale(interaction: InteractionSource, scale: Float = PressScale.button): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val value by animateFloatAsState(
        if (pressed) scale else 1f,
        animationSpec = if (pressed) tween(durationMillis = 100) else spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "pressScale",
    )
    return graphicsLayer { scaleX = value; scaleY = value }
}

/** Big rounded corner of all filled buttons (nearly a pill, fully round on compact ones). */
val ButtonShape = RoundedCornerShape(24.dp)

/** Standard button height (64 dp, like the banking-app template); [ButtonHeight.compact] for inline actions (44 dp visible, 48 dp touch area via padding). */
object ButtonHeight {
    val standard = 64.dp
    val compact = 44.dp
}

@Composable
private fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    compact: Boolean,
    haptic: Boolean,
    fill: Color,
    contentTone: Color,
    icon: Icon? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    // A grey button inside a grey panel gets one shade lighter, so it still reads as a button.
    val c = Theme.colors
    val fill = if (LocalInPanel.current && fill == c.surface) c.surfacePressed else fill
    val textStyle = Theme.type.label.copy(
        color = contentTone,
        fontWeight = if (compact) FontWeight.SemiBold else FontWeight.Bold,
        fontSize = if (compact) 16.sp else 18.sp,
    )
    Row(
        modifier
            .pressScale(interaction)
            // Touch area first: compact buttons are 44 dp visible, the extra 2 dp above and below keep 48 dp to tap.
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button) {
                if (haptic) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(vertical = if (compact) 2.dp else 0.dp)
            .heightIn(min = if (compact) ButtonHeight.compact else ButtonHeight.standard)
            .alpha(if (enabled) 1f else 0.3f)
            .background(fill, ButtonShape)
            .padding(horizontal = if (compact) Space.m else Space.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // Centred (text + icon as one group), bold; long labels on narrow phones shrink a little before they would be cut off.
        BasicText(
            label, modifier = Modifier.weight(1f, fill = false),
            style = textStyle, maxLines = 1, overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = textStyle.fontSize),
        )
        if (icon != null) { Spacer(Modifier.width(Space.s)); LineIcon(icon, color = contentTone) }
    }
}

/** The one primary action on a screen: white fill without border, black bold label left, optional [icon] right, 64 dp, large corners. */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth(),
    compact: Boolean = false,
    icon: Icon? = null,
) {
    val c = Theme.colors
    PillButton(
        if (loading) "…" else label, onClick, modifier, enabled && !loading, compact, haptic = true,
        fill = c.inverseBackground, contentTone = c.inverseForeground, icon = icon,
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
    icon: Icon? = null,
) {
    val c = Theme.colors
    PillButton(label, onClick, modifier, enabled, compact, haptic = false, fill = c.surface, contentTone = c.foreground, icon = icon)
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
    PillButton(label, onClick, modifier, enabled, compact, haptic = false, fill = c.negativeSubtle, contentTone = c.negative)
}

/** What a two-tap button does: delete/remove/reset (red when armed) or finish/complete (green when armed). */
enum class ConfirmKind { Destructive, Complete }

/**
 * Two-tap button for actions that need a confirmation (finish, discard, reset, delete all): the first tap
 * arms it (the button fills solid red ([Colors.armedFill]; green [Colors.confirmFill] for [ConfirmKind.Complete]) with a white label, the text stays the same; screen readers hear
 * "zum Bestätigen erneut tippen"), a second tap within 3 s runs [onConfirm]. A tap elsewhere, scrolling
 * or the timeout disarm it ([ConfirmArea]). [primary] = black main action of a screen (backup restore).
 */
@Composable
fun ConfirmButton(
    label: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    compact: Boolean = false,
    enabled: Boolean = true,
    primary: Boolean = false,
    loading: Boolean = false,
    /** [ConfirmKind.Destructive] (default) arms red, [ConfirmKind.Complete] green. */
    kind: ConfirmKind = ConfirmKind.Destructive,
) {
    val arm = rememberConfirmArm()
    val haptics = LocalHapticFeedback.current
    val again = LocalStrings.current.tapAgainToConfirm
    val armed = arm.armed
    val c = Theme.colors
    // Armed: solid red fill, white label (no border), whatever the button looked like before.
    val (fill, tone) = when {
        armed -> (if (kind == ConfirmKind.Complete) c.confirmFill else c.armedFill) to c.foreground
        primary -> c.inverseBackground to c.inverseForeground
        else -> c.surface to c.foreground
    }
    PillButton(
        if (loading) "…" else label,
        onClick = {
            if (armed) { arm.disarm(); onConfirm() }
            else { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); arm.arm() }
        },
        modifier = modifier
            .then(arm.modifier)
            .then(if (armed) Modifier.testTag(ConfirmTags.armed) else Modifier)
            .semantics { if (armed) stateDescription = again },
        enabled = enabled && !loading, compact = compact, haptic = false,
        fill = fill, contentTone = tone,
    )
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
    val interaction = remember { MutableInteractionSource() }
    val fill = if (LocalInPanel.current) c.surfacePressed else c.surface
    Box(
        modifier
            .pressScale(interaction)
            // 44 dp visible, 48 dp to tap (like the compact buttons).
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(vertical = 2.dp)
            .heightIn(min = ButtonHeight.compact)
            .background(fill, ButtonShape)
            .padding(horizontal = Space.m),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label, style = Theme.type.label.copy(color = tone.color(), fontWeight = FontWeight.SemiBold, fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 16.sp),
        )
    }
}

@Composable
fun IconButton(icon: Icon, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(TouchTarget.min)
            .semantics { contentDescription = label }
            .pressable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) { LineIcon(icon, size = IconSize.action) }
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
            modifier = (if (suffix != null) Modifier.width(IntrinsicTextWidth(value.ifEmpty { placeholder }, style)) else Modifier.fillMaxWidth()).textInputArea()
                .heightIn(min = TouchTarget.min)
                .semantics { contentDescription = placeholder },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
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
            modifier = Modifier.fillMaxWidth().textInputArea().padding(vertical = Space.m),
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
    /** Shown while empty, so an empty row still reads as editable. */
    placeholder: String = "–",
) {
    // Done removes the cursor; losing focus commits the value.
    val focusManager = LocalFocusManager.current
    val style = Theme.type.body.copy(color = Theme.colors.muted, textAlign = TextAlign.End)
    // As wide as the value (at least a comfortable tap target), so a label and its value share one line.
    val width = (IntrinsicTextWidth(value.ifEmpty { placeholder }, style) + 8.dp).coerceIn(64.dp, 200.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(Theme.colors.foreground),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = Modifier.width(width).textInputArea().onFocusLost(onCommit),
        // 48 dp high tap area, text vertically centred.
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.CenterEnd) {
                if (value.isEmpty()) BasicText(placeholder, style = style.copy(color = Theme.colors.faint))
                inner()
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Segmented control

@Composable
fun <T> Segmented(value: T, options: List<Pair<T, String>>, onChange: (T) -> Unit) {
    Row(
        Modifier
            .background(if (LocalInPanel.current) Theme.colors.surfacePressed else Theme.colors.surface, RoundedCornerShape(50))
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
// Panels

/** True inside a [Panel]: rows paint the panel fill and leave the dividers to the panel. */
val LocalInPanel = staticCompositionLocalOf { false }

val PanelShape = RoundedCornerShape(16.dp)

/** Corners of a [FloatingCard]: as large as the buttons, like the cards of the banking-app template. */
val CardShape = RoundedCornerShape(24.dp)

/**
 * Grouped area that stands out from the background: grey [Colors.surface] fill, no outline,
 * rounded corners, 1 dp dividers between its children (the black background shows through the gaps). Rows
 * ([ListRow], [CheckRow]) adapt by themselves; other content goes into [PanelItem].
 */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Theme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(PanelShape)
            // No outline: the black background shows through the 1 dp gaps between rows as a hairline divider.
            .background(c.background),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        CompositionLocalProvider(LocalInPanel provides true) { content() }
    }
}

/**
 * One standalone item that floats above the background (a workout in the list): own [Colors.surface] fill,
 * no outline and large rounded corners (no shadow: it would be invisible on black, the lighter fill carries
 * the lift). Pressable when [onClick] is set (shrinks slightly while pressed, [pressScale]);
 * put cards in a Column with [Space.m] gaps. Black and white only.
 */
@Composable
fun FloatingCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = Theme.colors
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressScale(interaction, PressScale.card) else Modifier)
            .clip(CardShape)
            .background(c.surface)
            .then(if (onClick != null) Modifier.clickable(interaction, indication = null, role = Role.Button, onClick = onClick) else Modifier),
        content = content,
    )
}

/** Free content inside a [Panel] (text, chart, buttons): panel fill and inner padding. */
@Composable
fun PanelItem(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().background(Theme.colors.surface).padding(horizontal = Space.m, vertical = Space.m),
        content = content,
    )
}

/**
 * For custom rows that should work both in a [Panel] and on the plain background: in a panel the row gets
 * the panel fill and inner padding (press feedback spans the full width), [onClick] makes it pressable.
 */
@Composable
fun Modifier.panelRow(onClick: (() -> Unit)? = null): Modifier {
    val inPanel = LocalInPanel.current
    return this
        .then(if (inPanel) Modifier.background(Theme.colors.surface) else Modifier)
        .then(if (onClick != null) Modifier.pressableRow(onClick) else Modifier)
        .then(if (inPanel) Modifier.padding(horizontal = Space.m) else Modifier)
}

/** Hairline under a custom row, left out inside a [Panel] (the panel draws its own dividers). */
@Composable
fun RowDivider() {
    if (!LocalInPanel.current) Hairline()
}

/** Short inline explanation under a panel or row: small, muted, a little indented to the panel's text. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(start = Space.m, end = Space.m, top = Space.s), style = Theme.type.caption, tone = Tone.Muted)
}

/** Section heading plus a [Panel] with its rows. */
@Composable
fun PanelSection(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    if (title != null) SectionTitle(title)
    Panel(modifier, content = content)
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
    val inPanel = LocalInPanel.current
    val base = if (inPanel) Modifier.background(Theme.colors.surface) else Modifier
    Column(base.then(if (onClick != null) Modifier.pressableRow(onClick) else Modifier)) {
        // Label and control share one line; if both do not fit (e.g. a long label + several segments on a
        // 360 dp phone), the control moves below the label instead of squeezing the label mid-word.
        if (trailing != null) {
            FlowRow(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .then(if (inPanel) Modifier.padding(horizontal = Space.m, vertical = Space.xs) else Modifier.padding(vertical = Space.xs)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(Space.xs, Alignment.CenterVertically),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, Modifier.padding(end = Space.m), tone = tone)
                trailing()
            }
        } else Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).then(if (inPanel) Modifier.padding(horizontal = Space.m, vertical = Space.xs) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, Modifier.weight(1f, fill = false), tone = tone)
            if (onClick != null) {
                Spacer(Modifier.width(Space.m))
                LineIcon(Icon.ChevronRight, size = app.overfit.ui.theme.IconSize.inline, color = Theme.colors.muted)
            }
        }
        if (!inPanel) Hairline()
    }
}

@Composable
fun ListRow(label: String, value: String, tone: Tone = Tone.Default, onClick: (() -> Unit)? = null) {
    ListRow(label, tone, onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, tone = Tone.Muted)
            if (onClick != null) { Spacer(Modifier.width(Space.xs)); LineIcon(Icon.ChevronRight, size = app.overfit.ui.theme.IconSize.inline, color = Theme.colors.muted) }
        }
    }
}

/**
 * A list row with a square checkbox at the end (e.g. "Sauna" after the workout). Unchecked = grey fill,
 * checked = filled (inverted) with a check mark. The whole row toggles.
 */
@Composable
fun CheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val inPanel = LocalInPanel.current
    Column(
        (if (inPanel) Modifier.background(Theme.colors.surface).padding(horizontal = Space.m) else Modifier).toggleable(
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
                        else Modifier.background(Theme.colors.surfacePressed, shape),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) LineIcon(Icon.Check, size = app.overfit.ui.theme.IconSize.small, color = Theme.colors.inverseForeground)
            }
        }
        if (!inPanel) Hairline()
    }
}

/**
 * A list row with a plain on/off switch at the end (vibration, ticks, photos …). No "An/Aus" text: knob left =
 * off (grey track [Colors.surfacePressed], grey knob), knob right = on (filled light track, black knob); mirrored in RTL.
 * The whole row toggles; screen readers get role Switch and the on/off state from the semantics.
 */
@Composable
fun SwitchRow(label: String, checked: Boolean, modifier: Modifier = Modifier, onCheckedChange: (Boolean) -> Unit) {
    val inPanel = LocalInPanel.current
    Column(
        modifier.then(if (inPanel) Modifier.background(Theme.colors.surface).padding(horizontal = Space.m) else Modifier).toggleable(
            value = checked,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
    ) {
        Row(
            // Vertical padding inside the 56 dp minimum: a label over several lines keeps its distance to the edges.
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(Space.m))
            SwitchTrack(checked)
        }
        if (!inPanel) Hairline()
    }
}

/** Test tag of the knob inside a [SwitchRow] (tests read its position). */
const val SWITCH_KNOB_TAG = "switch:knob"

/** The visual switch of [SwitchRow]: 52 × 32 dp track, 24 dp knob that slides from start (off) to end (on). */
@Composable
private fun SwitchTrack(checked: Boolean) {
    val c = Theme.colors
    val shape = RoundedCornerShape(50)
    // BiasAlignment follows the layout direction: -1 = start (left in LTR), 1 = end.
    val bias by animateFloatAsState(if (checked) 1f else -1f, label = "switch")
    Box(
        Modifier
            .size(width = 52.dp, height = 32.dp)
            .background(if (checked) c.foreground else c.surfacePressed, shape)
            .padding(4.dp),
        contentAlignment = BiasAlignment(bias, 0f),
    ) {
        Box(Modifier.testTag(SWITCH_KNOB_TAG).size(24.dp).background(if (checked) c.background else c.muted, CircleShape))
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
