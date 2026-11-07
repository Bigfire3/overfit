package app.overfit.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Lists of exercises and sets (workout editor, running workout) are set apart by grey areas instead of
// hairlines: one [Block] per exercise, one [SetRowSurface] per set, [BlockButton] for "+ add". See DECISIONS.

/** Corner radii of the area-based lists. */
object BlockShape {
    val block = RoundedCornerShape(16.dp)
    val row = RoundedCornerShape(12.dp)
    val field = RoundedCornerShape(10.dp)
}

/** One exercise (or other group) as a grey rounded area. */
@Composable
fun Block(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    // Same card look as [FloatingCard] (fill, no outline): every exercise is its own card, running or not.
    Column(
        modifier.fillMaxWidth().clip(BlockShape.block).background(Theme.colors.surface)
            .padding(Space.s),
        content = content,
    )
}

/**
 * Head of an exercise card, the same in the running workout and in every read-only view: preview image (frame 1,
 * 48 dp, a tap opens the exercise details), name and up to two caption lines. [leading]/[trailing] for the
 * running workout's chevron and menu.
 */
@Composable
fun ExerciseCardHeader(
    exercise: app.overfit.domain.Exercise,
    lines: List<String>,
    onDetails: (() -> Unit)?,
    modifier: Modifier = Modifier,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).then(modifier), verticalAlignment = Alignment.CenterVertically) {
        leading?.invoke(this)
        ExerciseImage(exercise, size = 48.dp, onClick = onDetails)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(exercise.name, style = Theme.type.label, maxLines = 2)
            lines.forEach { Text(it, style = Theme.type.caption, tone = Tone.Muted, maxLines = 2) }
        }
        trailing?.invoke(this)
    }
}

/** Full-width "+ label" action as a grey area (add set, add exercises). [inBlock]: inside a [Block], one shade lighter. */
@Composable
fun BlockButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: Icon? = Icon.Plus, inBlock: Boolean = false) {
    val c = Theme.colors
    val interaction = remember { MutableInteractionSource() }
    val fill = if (inBlock) c.surfacePressed else c.surface
    Row(
        modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .heightIn(min = 52.dp)
            .clip(if (inBlock) BlockShape.row else BlockShape.block)
            .background(fill)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            LineIcon(icon, size = 20.dp)
            Spacer(Modifier.width(Space.xs))
        }
        BasicText(
            label, style = Theme.type.label.copy(color = c.foreground), maxLines = 1, overflow = TextOverflow.Ellipsis,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = Theme.type.label.fontSize),
        )
    }
}

/**
 * One set as its own rounded row inside a [Block]. Swiping it to the left deletes it ([onDelete]); a red
 * area with [deleteLabel] shows behind the row while swiping. Screen readers get [deleteLabel] as an action.
 */
@Composable
fun SetRowSurface(
    deleteLabel: String,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val c = Theme.colors
    val scope = rememberCoroutineScope()
    var dx by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(0) }
    val currentDelete by rememberUpdatedState(onDelete)
    val swipe = if (onDelete == null) Modifier else Modifier
        .semantics { customActions = listOf(CustomAccessibilityAction(deleteLabel) { onDelete(); true }) }
        .pointerInput(Unit) {
            val settle: () -> Unit = {
                val past = dx < -width * 0.4f
                scope.launch {
                    animate(dx, if (past) -width.toFloat() else 0f, animationSpec = tween(if (past) 120 else 160)) { v, _ -> dx = v }
                    if (past) {
                        currentDelete?.invoke()
                        // Callers key rows by set; reset anyway so a reused slot never stays swiped away.
                        dx = 0f
                    }
                }
            }
            detectHorizontalDragGestures(
                onDragEnd = settle,
                onDragCancel = { scope.launch { animate(dx, 0f, animationSpec = tween(160)) { v, _ -> dx = v } } },
            ) { change, amount ->
                change.consume()
                dx = (dx + amount).coerceIn(-width.toFloat(), 0f)
            }
        }
    // The drag sits on the outer box, which does not move: on the sliding row the deltas would shrink with it.
    Box(modifier.fillMaxWidth().clip(BlockShape.row).onSizeChanged { width = it.width }.then(swipe)) {
        if (dx < 0f) {
            Row(
                Modifier.matchParentSize().background(c.negative).padding(horizontal = Space.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                LineIcon(Icon.Delete, size = 20.dp, color = Color.White)
                Spacer(Modifier.width(Space.xs))
                BasicText(deleteLabel, style = Theme.type.caption.copy(color = Color.White), maxLines = 1)
            }
        }
        Row(
            Modifier
                .offset { IntOffset(dx.roundToInt(), 0) }
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .background(c.surfacePressed)
                .padding(horizontal = Space.s, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            content = content,
        )
    }
}

/**
 * Column labels above the set rows ("SATZ  KG  WDH."), so the fields themselves only hold the number.
 * Same paddings and weights as [SetRowSurface] with [SetField]s; [trailing] = width of the check button.
 */
@Composable
fun SetColumnsHeader(numberLabel: String, labels: List<String>, trailing: Dp = 0.dp) {
    val style = Theme.type.caption.copy(color = Theme.colors.muted, textAlign = TextAlign.Center)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.s).padding(top = Space.xs).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        BasicText(numberLabel.uppercase(), Modifier.width(24.dp), style = style, maxLines = 1, overflow = TextOverflow.Visible, softWrap = false)
        labels.forEach { BasicText(it.uppercase(), Modifier.weight(1f), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        if (trailing > 0.dp) Spacer(Modifier.width(trailing))
    }
}

/**
 * Number field of a set row: a plain rounded box (48 dp high, no outline) with large tabular digits,
 * centred. The unit stands once in [SetColumnsHeader] above the rows, so even "1000" at 1.3x font fits on a
 * 360 dp phone. [imeAction] Next moves to [next] (or the next field), Done (and checking off a set) closes
 * the keyboard and removes the cursor.
 */
@Composable
fun SetField(
    value: String,
    onValueChange: (String) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Number,
    placeholder: String = "–",
    focusRequester: FocusRequester? = null,
    next: FocusRequester? = null,
) {
    val c = Theme.colors
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val style = Theme.type.number.copy(color = c.foreground, textAlign = TextAlign.Center)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        maxLines = 1,
        textStyle = style,
        cursorBrush = SolidColor(c.foreground),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (next != null) ImeAction.Next else ImeAction.Done),
        keyboardActions = KeyboardActions(
            onNext = { if (next != null) next.requestFocus() else focusManager.clearFocus() },
            onDone = { focusManager.clearFocus() },
        ),
        modifier = modifier.textInputArea()
            .heightIn(min = 48.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(BlockShape.field)
            .background(c.surfacePressed)
            .semantics { contentDescription = description },
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = Space.xs), contentAlignment = Alignment.Center) {
                // Placeholder = target value: readable grey, regular weight, so it differs from an entered value.
                val hint = style.copy(color = c.muted, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal)
                if (value.isEmpty()) BasicText(placeholder, style = hint, maxLines = 1, softWrap = false)
                inner()
            }
        },
    )
}

class MenuItem(val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

/** ⋮ button with a small menu of [items] below it (replace, move, remove …). */
@Composable
fun MoreMenu(label: String, items: List<MenuItem>, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val c = Theme.colors
    val below = with(LocalDensity.current) { 44.dp.roundToPx() }
    Box(modifier) {
        HeaderIconButton(Icon.More, label, { open = true })
        if (open) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, below),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(Modifier.width(220.dp).clip(BlockShape.row).background(c.surfacePressed).padding(vertical = Space.xs)) {
                    items.forEach { item ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable(role = Role.Button) { open = false; item.onClick() }
                                .padding(horizontal = Space.m),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            BasicText(item.label, style = Theme.type.body.copy(color = if (item.destructive) c.negative else c.foreground))
                        }
                    }
                }
            }
        }
    }
}
