package app.overfit.ui.muscles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.overfit.data.KeyValueStore
import app.overfit.domain.Muscle
import app.overfit.domain.MuscleLoad
import app.overfit.domain.MuscleMap
import app.overfit.domain.MusclePeriod
import app.overfit.domain.MuscleSetRow
import app.overfit.i18n.LocalStrings
import app.overfit.ui.components.Chip
import app.overfit.ui.components.HeaderIconButton
import app.overfit.ui.components.Icon
import app.overfit.ui.components.LocalInPanel
import app.overfit.ui.components.Text
import app.overfit.ui.components.Tone
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.math.min
import kotlin.math.roundToInt

/** Shade per level: 0 = untrained (dark grey) … [MuscleMap.LEVELS] = most (white). No accent colour. */
internal val LevelColors = listOf(
    Color(0xFF48484A), Color(0xFF707074), Color(0xFF9C9CA0), Color(0xFFCDCDD0), Color(0xFFFFFFFF),
)

/** Remembers the period of the calendar's muscle sheet. */
class MusclePeriodPref(private val kv: KeyValueStore) {
    var period: MusclePeriod
        get() = MusclePeriod.fromKey(kv.get(KEY)) ?: MusclePeriod.WEEK
        set(value) = kv.put(KEY, value.key)

    private companion object { const val KEY = "muscles.period" }
}

/**
 * Where front and back sit in the canvas: both figures side by side ([BodyShapes.viewport] each, a gap between),
 * scaled to fit and centred.
 */
internal class BodyLayout(size: Size) {
    private val vp = BodyShapes.viewport
    private val gap = 10f
    private val unitsWide = vp.width * 2 + gap
    val scale: Float = min(size.width / unitsWide, size.height / vp.height)
    private val left = (size.width - unitsWide * scale) / 2
    private val top = (size.height - vp.height * scale) / 2

    /** Canvas position of figure point [p] ([back] = right figure). */
    fun toCanvas(p: Offset, back: Boolean): Offset {
        val shift = if (back) vp.width + gap else 0f
        return Offset(left + (p.x - vp.left + shift) * scale, top + (p.y - vp.top) * scale)
    }

    /** Figure and figure point under canvas position [c]. */
    fun toFigure(c: Offset): Pair<Boolean, Offset> {
        val ux = (c.x - left) / scale
        val back = ux > vp.width + gap / 2
        val x = ux - (if (back) vp.width + gap else 0f) + vp.left
        return back to Offset(x, (c.y - top) / scale + vp.top)
    }

    /** Centre x of the front / back figure in the canvas (for the labels). */
    fun figureCenterX(back: Boolean): Float = toCanvas(Offset(50f, 0f), back).x
}

/** Canvas point in the middle of [muscle]'s first region (tests tap it); null if the figure has none. */
fun bodyMapPoint(muscle: Muscle, back: Boolean, size: Size): Offset? {
    val shape = (if (back) BodyShapes.back else BodyShapes.front).firstOrNull { it.muscle == muscle } ?: return null
    return BodyLayout(size).toCanvas(shape.center, back)
}

/** Rounded polygon: straight edges, each corner replaced by a curve over 38 % of the adjoining edges. */
private fun BodyShape.path(layout: BodyLayout, back: Boolean): Path {
    val path = Path()
    val o = oval
    if (o != null) {
        val tl = layout.toCanvas(o.topLeft, back)
        path.addOval(androidx.compose.ui.geometry.Rect(tl, Size(o.width * layout.scale, o.height * layout.scale)))
        return path
    }
    val pts = points.map { layout.toCanvas(it, back) }
    val r = 0.38f
    val n = pts.size
    fun lerp(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    pts.indices.forEach { i ->
        val prev = pts[(i - 1 + n) % n]
        val cur = pts[i]
        val next = pts[(i + 1) % n]
        val a = lerp(cur, prev, r)
        val b = lerp(cur, next, r)
        if (i == 0) path.moveTo(a.x, a.y) else path.lineTo(a.x, a.y)
        path.quadraticTo(cur.x, cur.y, b.x, b.y)
    }
    path.close()
    return path
}

/**
 * Front and back figure, each muscle region shaded by [levels] (missing = untrained). With [onSelect] a tap on a
 * region selects it (again = deselect); the others then fade a little so the selection stands out without an outline.
 */
@Composable
fun BodyMap(
    levels: Map<Muscle, Int>,
    description: String,
    modifier: Modifier = Modifier,
    selected: Muscle? = null,
    onSelect: ((Muscle?) -> Unit)? = null,
) {
    val m = LocalStrings.current.muscles
    val base = if (LocalInPanel.current) Theme.colors.surface else Theme.colors.surfacePressed
    val labelHeight = 20.dp
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val size = with(density) { Size(maxWidth.toPx(), (maxHeight - labelHeight).toPx().coerceAtLeast(1f)) }
        val layout = remember(size) { BodyLayout(size) }
        // Latest selection/callback without restarting the gesture detector on every recomposition.
        val current by androidx.compose.runtime.rememberUpdatedState(selected)
        val select by androidx.compose.runtime.rememberUpdatedState(onSelect)
        val tap = if (onSelect == null) Modifier else Modifier.pointerInput(layout) {
            detectTapGestures { c ->
                val (back, p) = layout.toFigure(c)
                val hit = BodyShapes.hit(if (back) BodyShapes.back else BodyShapes.front, p)
                select?.invoke(if (hit == current) null else hit)
            }
        }
        Canvas(
            Modifier.fillMaxWidth().height(maxHeight - labelHeight).testTag("body-map")
                .semantics { contentDescription = description }.then(tap),
        ) {
            listOf(false to BodyShapes.front, true to BodyShapes.back).forEach { (back, shapes) ->
                shapes.forEach { shape ->
                    val muscle = shape.muscle
                    val color = if (muscle == null) base else LevelColors[levels[muscle] ?: 0]
                    val faded = selected != null && muscle != selected
                    drawPath(shape.path(layout, back), color, alpha = if (faded) 0.45f else 1f)
                }
            }
        }
        // Labels under each figure.
        val labelWidth = 96.dp
        listOf(false to m.front, true to m.back).forEach { (back, label) ->
            val x = (layout.figureCenterX(back) - with(density) { labelWidth.toPx() } / 2).roundToInt()
            Text(
                label,
                Modifier.align(Alignment.BottomStart).offset { IntOffset(x, 0) }.width(labelWidth),
                style = Theme.type.caption,
                tone = Tone.Muted,
                align = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** "Viel ▬▬▬▬▬ Wenig": the five shades, brightest first. */
@Composable
fun MuscleLegend(modifier: Modifier = Modifier) {
    val m = LocalStrings.current.muscles
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Text(m.high, style = Theme.type.caption, tone = Tone.Muted)
        Spacer(Modifier.width(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            LevelColors.reversed().forEach { Box(Modifier.size(20.dp, 8.dp).background(it, RoundedCornerShape(4.dp))) }
        }
        Spacer(Modifier.width(Space.s))
        Text(m.low, style = Theme.type.caption, tone = Tone.Muted)
    }
}

/** Figure, legend and the line for the tapped muscle; shared by the calendar sheet and the training detail. */
@Composable
fun MuscleMapBody(loads: Map<Muscle, MuscleLoad>, figureHeight: Dp, modifier: Modifier = Modifier, emptyText: String? = null) {
    val m = LocalStrings.current.muscles
    var selected by remember { mutableStateOf<Muscle?>(null) }
    val levels = remember(loads) { MuscleMap.levels(loads) }
    val trained = remember(loads) {
        loads.filterValues { it.score > 0 }.entries.sortedByDescending { it.value.score }.map { m.muscle(it.key) }
    }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BodyMap(
            levels,
            m.summary(trained),
            Modifier.fillMaxWidth().height(figureHeight + 20.dp),
            selected = selected,
            onSelect = { selected = it },
        )
        Spacer(Modifier.height(Space.m))
        MuscleLegend()
        Spacer(Modifier.height(Space.s))
        val s = selected
        val line = if (s == null) (if (loads.isEmpty() && emptyText != null) emptyText else m.tapHint) else (loads[s] ?: MuscleLoad()).let { m.load(m.muscle(s), it.primary, it.secondary) }
        Text(
            line,
            Modifier.fillMaxWidth().testTag("muscle-line"),
            style = if (s == null) Theme.type.caption else Theme.type.body,
            tone = if (s == null) Tone.Muted else Tone.Default,
            align = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/**
 * Content of the calendar's muscle sheet: title with close button, period chips, the figure for that period.
 * [rows] null = still loading.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuscleMapSheetContent(
    rows: List<MuscleSetRow>?,
    period: MusclePeriod,
    today: LocalDate,
    weekStart: DayOfWeek,
    onPeriod: (MusclePeriod) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val m = LocalStrings.current.muscles
    val loads = remember(rows, period, today, weekStart) {
        rows?.let { MuscleMap.forPeriod(it, period, today, weekStart) }
    }
    var dragged by remember { mutableFloatStateOf(0f) }
    val closeAfter = with(LocalDensity.current) { 96.dp.toPx() }
    Column(
        modifier
            .fillMaxWidth()
            .offset { IntOffset(0, dragged.coerceAtLeast(0f).roundToInt()) }
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Theme.colors.surface)
            // Swallows taps on the sheet so only the dimmed area above closes it.
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("muscle-sheet"),
    ) {
        // Handle + title: dragging down closes the sheet.
        Column(
            Modifier.fillMaxWidth().draggable(
                rememberDraggableState { dragged += it },
                Orientation.Vertical,
                onDragStopped = { if (dragged > closeAfter) onClose() else dragged = 0f },
            ),
        ) {
            Box(
                Modifier.padding(top = Space.s).size(36.dp, 4.dp).align(Alignment.CenterHorizontally)
                    .background(Theme.colors.faint, RoundedCornerShape(2.dp)),
            )
            Row(
                Modifier.fillMaxWidth().padding(start = Space.l, end = Space.s, top = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    m.title,
                    Modifier.weight(1f).semantics { heading() },
                    style = Theme.type.label.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
                )
                HeaderIconButton(Icon.Close, m.close, onClose)
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = Space.l, end = Space.l, bottom = Space.l)) {
            CompositionLocalProvider(LocalInPanel provides true) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    MusclePeriod.entries.forEach { p -> Chip(m.period(p), p == period, onClick = { onPeriod(p) }) }
                }
            }
            Spacer(Modifier.height(Space.m))
            if (loads != null) {
                // A new period starts without a selection; "nothing yet" replaces the tap hint (no layout jump).
                androidx.compose.runtime.key(period) { MuscleMapBody(loads, figureHeight = 320.dp, emptyText = m.emptyPeriod(period)) }
            } else {
                Spacer(Modifier.height(360.dp))
            }
        }
    }
}

/** The calendar's muscle sheet: slides up from the bottom; back, a tap above it, ✕ or dragging it down closes it. */
@Composable
fun MuscleMapSheet(
    rows: List<MuscleSetRow>?,
    period: MusclePeriod,
    today: LocalDate,
    weekStart: DayOfWeek,
    onPeriod: (MusclePeriod) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shown = remember { MutableTransitionState(false).apply { targetState = true } }
        BoxWithConstraints(
            Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.BottomCenter,
        ) {
            val maxSheet = maxHeight * 0.92f
            AnimatedVisibility(shown, enter = slideInVertically { it }) {
                MuscleMapSheetContent(rows, period, today, weekStart, onPeriod, onDismiss, Modifier.heightIn(max = maxSheet))
            }
        }
    }
}
