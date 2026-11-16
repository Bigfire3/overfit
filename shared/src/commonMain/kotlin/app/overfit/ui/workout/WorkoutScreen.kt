package app.overfit.ui.workout

import app.overfit.ui.components.ExerciseCardHeader
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import app.overfit.ui.components.motionItem
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import app.overfit.ui.components.PressScale
import app.overfit.ui.components.RollingText
import app.overfit.ui.components.Swap
import app.overfit.ui.components.pressScale
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.graphics.graphicsLayer
import app.overfit.ui.components.AnimatedItems
import app.overfit.ui.components.PopIn
import app.overfit.ui.components.ProgressLine
import app.overfit.ui.components.Reveal
import app.overfit.ui.components.RevealNotNull
import app.overfit.ui.components.motionColor
import app.overfit.ui.components.rememberHaptics
import app.overfit.ui.theme.Motion
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import app.overfit.ui.components.SetColumnsHeader
import app.overfit.ui.components.Block
import app.overfit.ui.components.BlockButton
import app.overfit.ui.components.BlockShape
import app.overfit.ui.components.MenuItem
import app.overfit.ui.components.MoreMenu
import app.overfit.ui.components.SetField
import app.overfit.ui.components.SetRowSurface
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import app.overfit.ui.components.Chip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.overfit.domain.Language
import app.overfit.domain.Numbers
import app.overfit.domain.SetValues
import app.overfit.domain.WeightUnit
import app.overfit.domain.Workout
import app.overfit.domain.WorkoutExercise
import app.overfit.domain.WorkoutSet
import app.overfit.i18n.LocalStrings
import app.overfit.ui.components.BigInput
import app.overfit.ui.components.ConfirmButton
import app.overfit.ui.components.ConfirmKind
import app.overfit.ui.components.SecondaryButton
import app.overfit.ui.components.ExerciseImage
import app.overfit.ui.components.Hairline
import app.overfit.ui.components.Icon
import app.overfit.ui.components.IconButton
import app.overfit.ui.components.LineIcon
import app.overfit.ui.components.ListRow
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.ReorderState
import app.overfit.ui.components.Screen
import app.overfit.ui.components.SmallInput
import app.overfit.ui.components.Text
import app.overfit.ui.components.SmallButton
import app.overfit.ui.components.Tone
import app.overfit.ui.components.dragHandle
import app.overfit.ui.components.pressableRow
import app.overfit.ui.components.rememberReorderState
import app.overfit.ui.components.reorderableItem
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import app.overfit.workout.WorkoutController
import app.overfit.workout.WorkoutFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The running workout: exercises with sets (check off, edit values), name, finish. The rest
 * timer and the "log next set" button sit at the bottom and never cover the list.
 */
@Composable
fun WorkoutScreen(
    controller: WorkoutController,
    unit: WeightUnit,
    language: Language,
    clock: () -> Long,
    onAddExercises: () -> Unit,
    onReplaceExercise: (entryId: String) -> Unit,
    /** Tap on an exercise image: opens the exercise details (read-only) on top of the running workout. */
    onExerciseDetails: (exerciseId: String) -> Unit = {},
    onClosed: () -> Unit,
    /** Hides the workout without touching it (the mini bar takes over); also what the system back gesture does. */
    onMinimize: () -> Unit = {},
    /** After "Finish": opens the finish flow (summary, feeling, note, photos) for this workout. */
    onFinished: (workoutId: String) -> Unit = { onClosed() },
    /** Coach icon: opens the coach over this screen with the running workout attached (null = no icon). */
    onCoach: (() -> Unit)? = null,
) {
    val t = LocalStrings.current
    val state by controller.state.collectAsStateWithLifecycle()
    val current = state.workout
    LaunchedEffect(state.loaded, current == null) {
        if (!state.loaded || current != null) return@LaunchedEffect
        val finished = state.finishedId
        if (finished != null) {
            controller.consumeFinished()
            onFinished(finished)
        } else {
            onClosed()
        }
    }
    // Finished or discarded: the screen is on its way out. It keeps showing the workout as it was instead of going
    // black while the next screen comes in (edits no longer reach the controller, it has no workout any more).
    val last = remember { arrayOfNulls<Workout>(1) }
    if (current != null) last[0] = current
    val workout = current ?: last[0] ?: return

    val scope = rememberCoroutineScope()
    val now = produceState(clock()) { while (true) { value = clock(); delay(250) } }
    var collapsed by rememberSaveable { mutableStateOf(listOf<String>()) }

    // Last swiped-away set: "Undo" restores the workout as it was, as long as nothing else changed since.
    var undo by remember { mutableStateOf<Pair<Workout, Workout>?>(null) }
    LaunchedEffect(undo) { if (undo != null) { delay(5000); undo = null } }
    val removeSet: (String) -> Unit = { setId ->
        val before = workout
        val after = before.removeSet(setId)
        controller.edit { it.removeSet(setId) }
        undo = before to after
    }
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(listState, canMove = { it is String && workout.exercise(it) != null }) { from, to ->
        controller.edit { w ->
            w.moveExercise(w.exercises.indexOfFirst { it.id == from }, w.exercises.indexOfFirst { it.id == to })
        }
    }
    // The set that is next right now (what the main button logs): marked in the list, named in the button.
    val upNext = workout.nextToLog()
    val showExercise: (String) -> Unit = { id ->
        collapsed = collapsed - id
        val index = workout.exercises.indexOfFirst { it.id == id }
        if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
    }
    // The screen shows what is to do, not what is done: an exercise whose last set was just checked off folds up and
    // the list moves on to the exercise that is next. Only at that moment; opened again by hand, it stays open.
    val finished = workout.exercises.filter { !it.skipped && it.sets.isNotEmpty() && it.sets.all { s -> s.completed } }.map { it.id }
    var knownFinished by rememberSaveable { mutableStateOf(finished) }
    LaunchedEffect(finished) {
        val fresh = finished - knownFinished.toSet()
        knownFinished = finished
        val next = upNext?.exercise?.id
        // Nothing open any more: stay, the button below now finishes the training.
        if (fresh.isEmpty() || next == null) return@LaunchedEffect
        collapsed = collapsed + fresh - next
        val index = workout.exercises.indexOfFirst { it.id == next }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    // Exercises that were just added (picker) land below the fold: the list goes there.
    val exerciseIds = workout.exercises.map { it.id }
    var knownExercises by rememberSaveable { mutableStateOf(exerciseIds) }
    LaunchedEffect(exerciseIds) {
        val added = exerciseIds.firstOrNull { it !in knownExercises }
        val grew = exerciseIds.size > knownExercises.size
        knownExercises = exerciseIds
        if (added != null && grew) listState.animateScrollToItem(exerciseIds.indexOf(added))
    }
    // While a number is typed, the keyboard takes half the screen: the main button steps aside (the set is checked
    // off at its row), only the rest timer stays.
    val typing = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val background = Theme.colors.background

    Screen(
        compact = true,
        footer = {
            // One column without gaps of its own: what comes and goes brings its gap along, so nothing jumps at the end.
            Column(
                // The list does not end at a hard edge: its last 24 dp fade into the dock.
                Modifier.drawBehind {
                    val fade = 24.dp.toPx()
                    val gap = Space.s.toPx()
                    drawRect(
                        Brush.verticalGradient(listOf(Color.Transparent, background), startY = -gap - fade, endY = -gap),
                        topLeft = Offset(0f, -gap - fade), size = Size(size.width, fade),
                    )
                },
            ) {
                RevealNotNull(undo?.takeIf { (_, after) -> workout == after }, from = Alignment.Bottom) { (before, after) ->
                    Row(Modifier.fillMaxWidth().padding(bottom = Space.s), verticalAlignment = Alignment.CenterVertically) {
                        Text(t.setDeleted, Modifier.weight(1f), style = Theme.type.caption, tone = Tone.Muted)
                        SmallButton(t.undo, { undo = null; controller.edit { w -> if (w == after) before else w } })
                    }
                }
                WorkoutFooter(
                    workout = workout,
                    upNext = upNext,
                    now = now,
                    unit = unit,
                    language = language,
                    controller = controller,
                    typing = typing,
                    onShowExercise = showExercise,
                )
            }
        },
    ) {
        // One slim header row: minimize, name + clock, [finish] [coach] (coach in the corner like on every screen).
        // Pulling the header down minimizes as well: the workout came up from the bottom and goes back there.
        val minimize by rememberUpdatedState(onMinimize)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).pointerInput(Unit) {
                var pulled = 0f
                detectVerticalDragGestures(
                    onDragStart = { pulled = 0f },
                    onVerticalDrag = { _, delta -> pulled += delta },
                    onDragEnd = { if (pulled > 40.dp.toPx()) minimize() },
                )
            },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            IconButton(Icon.ChevronDown, t.minimizeWorkout, onMinimize)
            Column(Modifier.weight(1f)) {
                NameField(workout.id, workout.name, { v -> controller.edit { it.withName(v.take(60)) } }, Modifier.fillMaxWidth())
                ProgressCaption(workout, now)
            }
            // The text button stays a direct child of this row: inside a nested row its auto-sized label was squeezed.
            ConfirmButton(t.finishWorkout, onConfirm = { scope.launch { controller.finish() } }, modifier = Modifier, compact = true, kind = ConfirmKind.Complete)
            if (onCoach != null) app.overfit.ui.components.HeaderActions(coach = onCoach)
        }
        Spacer(Modifier.height(Space.xs))
        // How far the training is: fills with every set that is checked off.
        ProgressLine(if (workout.totalSets > 0) workout.completedSets.toFloat() / workout.totalSets else 0f)

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(top = Space.s, bottom = Space.l),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            if (workout.exercises.isEmpty()) {
                item(key = "empty") { Text(t.workoutEmpty, Modifier.then(motionItem()).padding(vertical = Space.m), tone = Tone.Muted) }
            }
            items(workout.exercises, key = { it.id }) { e ->
                ExerciseSection(
                    modifier = Modifier.then(reorderableItem(reorder, e.id)),
                    reorder = reorder,
                    entry = e,
                    lastTime = state.lastTime[e.exercise.id].orEmpty(),
                    unit = unit,
                    language = language,
                    expanded = e.id !in collapsed,
                    upNextSetId = upNext?.set?.id,
                    onToggle = { collapsed = if (e.id in collapsed) collapsed - e.id else collapsed + e.id },
                    controller = controller,
                    onRemoveSet = removeSet,
                    onReplace = { onReplaceExercise(e.id) },
                    onDetails = { onExerciseDetails(e.exercise.id) },
                )
            }
            item(key = "actions") {
                // Moves along when an exercise is added, removed or folded, like the cards above it.
                Column(motionItem()) {
                    BlockButton(t.addExercise, onAddExercises)
                    Spacer(Modifier.height(Space.l))
                    ConfirmButton(t.discardWorkout, onConfirm = { scope.launch { controller.discard() } })
                }
            }
        }
    }
}

/** Name with its own text state, so the cursor does not jump while the workout state round-trips. */
@Composable
private fun NameField(id: String, name: String, onChange: (String) -> Unit, modifier: Modifier) {
    val t = LocalStrings.current
    var text by remember(id) { mutableStateOf(name) }
    Box(modifier) {
        BigInput(text, { text = it.take(60); onChange(it) }, t.workoutNamePlaceholder, Modifier.fillMaxWidth(), textStyle = Theme.type.label)
    }
}

@Composable
private fun ProgressCaption(workout: Workout, now: State<Long>) {
    val t = LocalStrings.current
    // The clock ticks four times a second, the text changes once: only then this recomposes.
    val elapsed by remember(workout.startedAt) { derivedStateOf { WorkoutFormat.elapsed(now.value - workout.startedAt) } }
    val style = Theme.type.tabular(Theme.type.caption)
    // Shown short ("12:05 · 3/20": the bar below says the same), read out in full ("… 3/20 erledigt"). One line, so the
    // header keeps its height next to "Finish" and the coach icon on a 360 dp phone.
    Row(Modifier.clearAndSetSemantics { text = AnnotatedString("$elapsed · ${t.setsDone(workout.completedSets, workout.totalSets)}") }) {
        // Ticks every second: tabular digits, so the caption does not jitter.
        Text("$elapsed · ", style = style, tone = Tone.Muted, maxLines = 1)
        // The number of finished sets rolls up when a set is checked off.
        RollingText(workout.completedSets.toString(), style = style, tone = Tone.Muted)
        Text("/${workout.totalSets}", style = style, tone = Tone.Muted, maxLines = 1)
    }
}

/**
 * The dock at the bottom of the running workout: the rest timer (only while a rest runs; it rises out of the button
 * and sinks back into it) above the one main button. The button says what it will do: its second line names the set
 * that is next, so that set is visible with or without a rest.
 */
@Composable
private fun WorkoutFooter(
    workout: Workout,
    upNext: Workout.SetRef?,
    now: State<Long>,
    unit: WeightUnit,
    language: Language,
    controller: WorkoutController,
    typing: Boolean,
    onShowExercise: (String) -> Unit,
) {
    val t = LocalStrings.current
    val finishScope = rememberCoroutineScope()
    val nextText = upNext?.let { WorkoutFormat.next(it, unit, language, t) }
    // The clock ticks four times a second; this only changes when a rest begins or ends.
    val rest = workout.rest
    val resting by remember(rest) { derivedStateOf { rest != null && !rest.isOver(now.value) } }
    RevealNotNull(rest.takeIf { resting }, from = Alignment.Bottom) { running ->
        Column {
            RestBar(
                rest = running,
                now = now,
                nextLine = nextText?.let(t.nextUp),
                onAdjust = controller::adjustRest,
                onSkip = controller::skipRest,
                onShowNext = upNext?.let { ref -> { onShowExercise(ref.exercise.id) } },
            )
            Spacer(Modifier.height(Space.s))
        }
    }
    Reveal(!typing, from = Alignment.Bottom) {
        // Nothing open any more (every set done or its exercise skipped): the button finishes the training, green on
        // the first tap, saved on the second (same double tap as "Finish" at the top). "Log" and "finish" change
        // softly, and the finish button only takes taps after a moment: tapping "log" quickly must not finish.
        Swap(workout.allDone, contentAlignment = Alignment.BottomCenter) { allDone ->
            if (allDone) {
                var ready by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { delay(FINISH_GUARD_MS); ready = true }
                ConfirmButton(
                    t.training.finishTraining,
                    onConfirm = { finishScope.launch { controller.finish() } },
                    modifier = Modifier.fillMaxWidth().testTag(FINISH_BUTTON_TAG),
                    enabled = ready,
                    primary = true,
                    kind = ConfirmKind.Complete,
                )
            } else {
                PrimaryButton(t.logNextSet, onClick = { controller.logNextSet() }, enabled = workout.totalSets > 0, caption = nextText)
            }
        }
    }
}

/** How long the "finish training" button ignores taps after it replaced "log next set". */
private const val FINISH_GUARD_MS = 450L

/** Test tag of the floating "Finish workout" button (shown once nothing is open). */
const val FINISH_BUTTON_TAG = "workout-finish-bottom"


@Composable
private fun ExerciseSection(
    modifier: Modifier,
    reorder: ReorderState,
    entry: WorkoutExercise,
    lastTime: List<SetValues>,
    unit: WeightUnit,
    language: Language,
    expanded: Boolean,
    /** Id of the set that is next in the whole workout (marked in the "now" colour), if it is one of this exercise. */
    upNextSetId: String?,
    onToggle: () -> Unit,
    controller: WorkoutController,
    onRemoveSet: (setId: String) -> Unit,
    onReplace: () -> Unit,
    onDetails: () -> Unit,
) {
    val t = LocalStrings.current
    val haptics = rememberHaptics()
    val type = entry.exercise.trackingType
    // Skipping and taking back in: the card changes softly and the cards below slide, nothing jumps.
    Swap(entry.skipped, modifier) { skipped ->
    if (skipped) {
        SkippedSection(Modifier, entry, onUnskip = { controller.edit { it.setSkipped(entry.id, false) } }, onDetails = onDetails)
    } else Block {
        ExerciseCardHeader(
            entry.exercise,
            listOfNotNull(
                t.setsDone(entry.completedSets, entry.sets.size),
                lastTime.takeIf { it.isNotEmpty() }?.let { t.lastTime(WorkoutFormat.lastTime(it, type, unit, language, t)) },
            ),
            onDetails = onDetails,
            modifier = Modifier.clip(BlockShape.row).pressableRow(onToggle)
                .dragHandle(reorder, entry.id) { haptics.threshold() }.semantics { contentDescription = t.reorder },
            leading = {
                // One chevron that turns, instead of two icons that swap.
                val turn by animateFloatAsState(if (expanded) 90f else 0f, Motion.snappy(), label = "chevron")
                Box(Modifier.graphicsLayer { rotationZ = turn }) {
                    LineIcon(Icon.ChevronRight, size = app.overfit.ui.theme.IconSize.inline, color = Theme.colors.muted)
                }
                Spacer(Modifier.width(Space.s))
            },
        ) {
            MoreMenu(
                t.moreActions(entry.exercise.name),
                listOf(
                    MenuItem(t.training.skipToday) { controller.edit { it.setSkipped(entry.id, true) } },
                    MenuItem(t.replace, onClick = onReplace),
                    // Two taps: removing takes the sets logged so far with it.
                    MenuItem(t.remove, destructive = true, confirm = true) { controller.edit { it.removeExercise(entry.id) } },
                ),
            )
        }
        // Alternatives (A/B): one chip per variant, the current one filled. Switching is possible until a set of
        // it is done; the default is the variant not done last time (chosen at the start).
        Reveal(entry.variants.size > 1 && entry.sets.none { it.completed }) {
            val scope = rememberCoroutineScope()
            androidx.compose.runtime.CompositionLocalProvider(app.overfit.ui.components.LocalInPanel provides true) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("variants:${entry.id}"),
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    entry.variants.forEachIndexed { i, v ->
                        Chip(v.exercise.name, i == entry.variantIndex, { scope.launch { controller.switchVariant(entry.id, i) } })
                    }
                }
            }
        }
        // Folding and unfolding moves the cards below along instead of making them jump.
        Reveal(expanded) {
            Column(Modifier.padding(top = Space.xs)) {
                SetColumnsHeader(t.setColumn, setColumnLabels(type, unit, t), trailing = 48.dp)
                Spacer(Modifier.height(Space.xs))
                val focus = remember { mutableMapOf<String, FocusRequester>() }
                fun focusOf(id: String) = focus.getOrPut(id) { FocusRequester() }
                // A new set grows in, a deleted one shrinks away; the rows below slide.
                AnimatedItems(entry.sets, key = { it.id }, spacing = Space.xs) { i, set ->
                    WorkoutSetRow(
                        focusRequester = focusOf(set.id),
                        next = entry.sets.getOrNull(i + 1)?.let { focusOf(it.id) },
                        number = i + 1,
                        type = type,
                        unit = unit,
                        language = language,
                        set = set,
                        upNext = set.id == upNextSetId,
                        onValues = { controller.setValues(set.id, it) },
                        onToggle = { controller.toggleSet(set.id) },
                        onRemove = { onRemoveSet(set.id) },
                    )
                }
                // One button: a new set takes over the values of the last one (what "duplicate" did as well).
                BlockButton(t.addSet, { controller.edit { it.addSet(entry.id) } }, inBlock = true)
                Spacer(Modifier.height(Space.xs))
                NoteField(entry.id, entry.note) { v -> controller.edit { it.setExerciseNote(entry.id, v.take(300)) } }
            }
        }
    }
    }
}

/**
 * A skipped exercise: collapsed and dimmed, "Skipped" (plus the sets done before), "Don't skip" brings it back.
 * Its open sets no longer count, so the training can be finished without them.
 */
@Composable
private fun SkippedSection(modifier: Modifier, entry: WorkoutExercise, onUnskip: () -> Unit, onDetails: () -> Unit) {
    val t = LocalStrings.current
    Block(modifier.testTag("skipped:${entry.id}")) {
        // Only the exercise is dimmed; the button to take it back in stays fully visible, below the name so both fit.
        Box(Modifier.fillMaxWidth().alpha(0.45f)) {
            ExerciseCardHeader(
                entry.exercise,
                listOf(listOfNotNull(t.training.skipped, entry.completedSets.takeIf { it > 0 }?.let { t.setsDone(it, entry.sets.size) }).joinToString(" · ")),
                onDetails = onDetails,
            )
        }
        BlockButton(t.training.unskip, onUnskip, Modifier.padding(top = Space.xs), icon = null, inBlock = true)
    }
}

@Composable
private fun NoteField(id: String, note: String, onChange: (String) -> Unit) {
    val t = LocalStrings.current
    var text by remember(id) { mutableStateOf(note) }
    SmallInput(
        text,
        { text = it.take(300); onChange(it) },
        unit = "",
        placeholder = t.note,
        keyboardType = KeyboardType.Text,
        align = TextAlign.Start,
        maxLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Column labels for [SetColumnsHeader]: unit per used field ("KG", "WDH.", "SEK."). */
fun setColumnLabels(type: app.overfit.domain.TrackingType, unit: WeightUnit, t: app.overfit.i18n.Strings): List<String> = buildList {
    if (type.usesWeight) add(unit.symbol)
    if (type.usesReps) add(t.repsUnit)
    if (type.usesTime) add(t.timeColumn)
}

/**
 * One set: weight / reps / time inputs and the check button, swipe left removes it. Shared by the
 * running workout and the edit screen of a finished training (same inputs, weights are never negative).
 */
@Composable
fun WorkoutSetRow(
    number: Int,
    type: app.overfit.domain.TrackingType,
    unit: WeightUnit,
    language: Language,
    set: WorkoutSet,
    onValues: (SetValues) -> Unit,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() },
    next: FocusRequester? = null,
    /** This is the set that is next in the running workout: its number stands in the "now" colour. */
    upNext: Boolean = false,
) {
    val t = LocalStrings.current
    val focusManager = LocalFocusManager.current
    val done = set.completed
    // Keyboard order: weight -> reps/time -> first field of the next set; the last field says "Done".
    val fieldCount = listOf(type.usesWeight, type.usesReps, type.usesTime).count { it }
    val more = remember(fieldCount) { List((fieldCount - 1).coerceAtLeast(0)) { FocusRequester() } }
    val requesters = listOf(focusRequester) + more
    var field = 0
    fun req(): FocusRequester = requesters[field]
    fun nextOf(): FocusRequester? = requesters.getOrNull(field + 1) ?: next
    val actual = set.actual
    val target = set.target
    // Checked off = the whole row turns green (owner wish), fields and text stay white on it. The green spreads from
    // the check button over the row (and draws back into it when the set is opened again).
    val spread by animateFloatAsState(if (done) 1f else 0f, Motion.smooth(), label = "setDone")
    val numberColor by motionColor(
        when {
            done -> Theme.colors.foreground
            upNext -> Theme.colors.accent
            else -> Theme.colors.muted
        },
        "setNumber",
    )
    SetRowSurface(
        t.removeSet(number), onRemove, Modifier.testTag("set-row:$number"),
        spreadFill = Theme.colors.confirmFill, spread = { spread },
    ) {
        BasicText("$number", Modifier.width(24.dp), style = Theme.type.label.copy(textAlign = TextAlign.Center), color = { numberColor })
        if (type.usesWeight) {
            NumberInput(
                description = t.weightOfSet(number),
                value = actual.weightKg,
                resetKey = set.id,
                format = { Numbers.formatWeight(it, unit, language) },
                parse = { Numbers.parseWeight(it, unit) },
                onCommit = { onValues(actual.copy(weightKg = it)) },
                filter = { s -> s.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(8) },
                keyboardType = KeyboardType.Decimal,
                placeholder = target.weightKg?.let { Numbers.formatWeight(it, unit, language) } ?: "–",
                focusRequester = req(),
                next = nextOf(),
                onConfirm = done,
                modifier = Modifier.weight(1f),
            )
            field++
        }
        if (type.usesReps) {
            NumberInput(
                description = t.repsOfSet(number),
                value = actual.reps,
                resetKey = set.id,
                format = { it.toString() },
                parse = { Numbers.parseCount(it) },
                onCommit = { onValues(actual.copy(reps = it)) },
                filter = { s -> s.filter(Char::isDigit).take(4) },
                keyboardType = KeyboardType.Number,
                focusRequester = req(),
                next = nextOf(),
                placeholder = target.reps?.toString() ?: "–",
                onConfirm = done,
                modifier = Modifier.weight(1f),
            )
            field++
        }
        if (type.usesTime) {
            NumberInput(
                description = t.durationOfSet(number),
                value = actual.durationSec,
                resetKey = set.id,
                format = { it.toString() },
                parse = { Numbers.parseDuration(it) },
                onCommit = { onValues(actual.copy(durationSec = it)) },
                filter = { s -> s.filter { c -> c.isDigit() || c == ':' }.take(6) },
                keyboardType = KeyboardType.Number,
                focusRequester = req(),
                next = nextOf(),
                placeholder = target.durationSec?.toString() ?: "–",
                onConfirm = done,
                modifier = Modifier.weight(1f),
            )
        }
        CheckButton(done, if (done) t.reopenSet(number) else t.completeSet(number)) { focusManager.clearFocus(); onToggle() }
    }
}

/**
 * Round check-off button. Open: a faint tick in the circle says "tap here when done". Done: the tick turns white and
 * lands with a small bounce (the row around it turns green). Press feedback is the circle itself shrinking.
 */
@Composable
private fun CheckButton(done: Boolean, description: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val tick by animateFloatAsState(if (done) 1f else 0.26f, Motion.snappy(), label = "tick")
    // 1 at rest; drops and springs back the moment the set is checked off.
    val pop = remember { Animatable(1f) }
    var wasDone by remember { mutableStateOf(done) }
    LaunchedEffect(done) {
        if (done && !wasDone) { pop.snapTo(0.5f); pop.animateTo(1f, Motion.bouncy()) }
        wasDone = done
    }
    Box(
        Modifier
            .size(48.dp)
            .semantics { contentDescription = description; role = Role.Checkbox }
            .pressScale(interaction, PressScale.icon)
            .clickable(interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(app.overfit.ui.theme.Sizes.checkCircle)
                .background(Theme.colors.inputFill, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.graphicsLayer { alpha = tick; scaleX = pop.value; scaleY = pop.value }) {
                LineIcon(Icon.Check, size = app.overfit.ui.theme.IconSize.inline, color = Color.White)
            }
        }
    }
}

/**
 * Number field with its own text, so half-typed input ("92,") survives. Valid text is passed to
 * [onCommit] right away; empty text commits null. If [value] changes from outside (e.g. checking
 * off a prefilled set), the text follows.
 */
@Composable
private fun <N : Any> NumberInput(
    description: String,
    value: N?,
    resetKey: Any,
    format: (N) -> String,
    parse: (String) -> N?,
    onCommit: (N?) -> Unit,
    filter: (String) -> String,
    keyboardType: KeyboardType,
    placeholder: String,
    focusRequester: FocusRequester,
    next: FocusRequester?,
    onConfirm: Boolean,
    modifier: Modifier = Modifier,
) {
    var text by remember(resetKey) { mutableStateOf(value?.let(format).orEmpty()) }
    LaunchedEffect(value) {
        if (parse(text) != value) text = value?.let(format).orEmpty()
    }
    SetField(
        text,
        { input ->
            val filtered = filter(input)
            text = filtered
            if (filtered.isBlank()) onCommit(null) else parse(filtered)?.let(onCommit)
        },
        description = description,
        modifier = modifier,
        keyboardType = keyboardType,
        placeholder = placeholder,
        focusRequester = focusRequester,
        next = next,
        onConfirm = onConfirm,
    )
}
