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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
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
    Column(
        modifier.fillMaxWidth().clip(BlockShape.block).background(Theme.colors.surface).padding(Space.s),
        content = content,
    )
}

/** Full-width "+ label" action as a grey area (add set, add exercises). [inBlock]: inside a [Block], one shade lighter. */
@Composable
fun BlockButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: Icon? = Icon.Plus, inBlock: Boolean = false) {
    val c = Theme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill = when {
        inBlock -> if (pressed) c.surface else c.surfacePressed
        else -> if (pressed) c.surfacePressed else c.surface
    }
    Row(
        modifier
            .fillMaxWidth()
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
        BasicText(label, style = Theme.type.label.copy(color = c.foreground), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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

/** Number field of a set row: its own rounded area, large value, small uppercase unit ("KG", "WDH") next to it. */
@Composable
fun SetField(
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    description: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Number,
    placeholder: String = "–",
) {
    val c = Theme.colors
    val style = Theme.type.title.copy(color = c.foreground, textAlign = TextAlign.End)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(c.foreground),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .weight(1f)
                .clip(BlockShape.field)
                .background(c.background)
                .semantics { contentDescription = description }
                .padding(horizontal = Space.s, vertical = 6.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterEnd) {
                    if (value.isEmpty()) BasicText(placeholder, style = style.copy(color = c.faint))
                    inner()
                }
            },
        )
        if (unit.isNotEmpty()) {
            Spacer(Modifier.width(Space.xs))
            BasicText(unit.uppercase(), style = Theme.type.caption.copy(color = c.muted))
        }
    }
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
