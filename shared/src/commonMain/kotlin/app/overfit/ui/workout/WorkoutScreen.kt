package app.overfit.ui.workout

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
import app.overfit.ui.components.TextButton
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
    onClosed: () -> Unit,
) {
    val t = LocalStrings.current
    val state by controller.state.collectAsStateWithLifecycle()
    val workout = state.workout
    LaunchedEffect(state.loaded, workout == null) { if (state.loaded && workout == null) onClosed() }
    if (workout == null) return

    val scope = rememberCoroutineScope()
    val now = produceState(clock()) { while (true) { value = clock(); delay(250) } }
    var collapsed by rememberSaveable { mutableStateOf(listOf<String>()) }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    LaunchedEffect(confirmFinish) { if (confirmFinish) { delay(4000); confirmFinish = false } }
    LaunchedEffect(confirmDiscard) { if (confirmDiscard) { delay(4000); confirmDiscard = false } }

    val listState = rememberLazyListState()
    val reorder = rememberReorderState(listState, canMove = { it is String && workout.exercise(it) != null }) { from, to ->
        controller.edit { w ->
            w.moveExercise(w.exercises.indexOfFirst { it.id == from }, w.exercises.indexOfFirst { it.id == to })
        }
    }

    Screen(
        footer = {
            WorkoutFooter(
                workout = workout,
                now = now,
                unit = unit,
                language = language,
                controller = controller,
                onJumpToNext = { id ->
                    collapsed = collapsed - id
                    val index = workout.exercises.indexOfFirst { it.id == id }
                    if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
                },
            )
        },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            NameField(workout.id, workout.name, { v -> controller.edit { it.withName(v.take(60)) } }, Modifier.weight(1f))
            TextButton(
                if (confirmFinish) t.finishConfirm else t.finishWorkout,
                { if (confirmFinish) scope.launch { controller.finish() } else confirmFinish = true },
                tone = if (confirmFinish) Tone.Default else Tone.Muted,
            )
        }
        ProgressCaption(workout, now)
        Spacer(Modifier.height(Space.m))
        Hairline()

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(bottom = Space.l)) {
            if (workout.exercises.isEmpty()) {
                item(key = "empty") { Text(t.workoutEmpty, Modifier.padding(vertical = Space.m), tone = Tone.Muted) }
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
                    onToggle = { collapsed = if (e.id in collapsed) collapsed - e.id else collapsed + e.id },
                    controller = controller,
                    onReplace = { onReplaceExercise(e.id) },
                )
            }
            item(key = "actions") {
                ListRow(t.addExercise, onClick = onAddExercises) { LineIcon(Icon.Plus) }
                ListRow(
                    if (confirmDiscard) t.discardConfirm else t.discardWorkout,
                    tone = if (confirmDiscard) Tone.Default else Tone.Muted,
                    onClick = { if (confirmDiscard) scope.launch { controller.discard() } else confirmDiscard = true },
                )
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
        BigInput(text, { text = it.take(60); onChange(it) }, t.workoutNamePlaceholder, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ProgressCaption(workout: Workout, now: State<Long>) {
    val t = LocalStrings.current
    Text(
        WorkoutFormat.elapsed(now.value - workout.startedAt) + " · " + t.setsDone(workout.completedSets, workout.totalSets),
        style = Theme.type.caption,
        tone = Tone.Muted,
    )
}

@Composable
private fun WorkoutFooter(
    workout: Workout,
    now: State<Long>,
    unit: WeightUnit,
    language: Language,
    controller: WorkoutController,
    onJumpToNext: (String) -> Unit,
) {
    val t = LocalStrings.current
    val rest = workout.rest
    if (rest != null && !rest.isOver(now.value)) {
        RestBar(
            rest = rest,
            now = now.value,
            next = workout.nextOpenSet(rest.afterSetId),
            unit = unit,
            language = language,
            onAdjust = controller::adjustRest,
            onSkip = controller::skipRest,
            onJumpToNext = onJumpToNext,
        )
    }
    val allDone = workout.totalSets > 0 && workout.nextOpenSet() == null
    PrimaryButton(
        if (allDone) t.allSetsDone else t.logNextSet,
        onClick = { controller.logNextSet() },
        enabled = workout.totalSets > 0 && !allDone,
    )
}

@Composable
private fun ExerciseSection(
    modifier: Modifier,
    reorder: ReorderState,
    entry: WorkoutExercise,
    lastTime: List<SetValues>,
    unit: WeightUnit,
    language: Language,
    expanded: Boolean,
    onToggle: () -> Unit,
    controller: WorkoutController,
    onReplace: () -> Unit,
) {
    val t = LocalStrings.current
    val type = entry.exercise.trackingType
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).pressableRow(onToggle).padding(vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExerciseImage(entry.exercise, size = 40.dp)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(entry.exercise.name, style = Theme.type.label)
                Text(t.setsDone(entry.completedSets, entry.sets.size), style = Theme.type.caption, tone = Tone.Muted)
                if (lastTime.isNotEmpty()) {
                    Text(
                        t.lastTime(WorkoutFormat.lastTime(lastTime, type, unit, language, t)),
                        style = Theme.type.caption,
                        tone = Tone.Muted,
                        maxLines = 2,
                    )
                }
            }
            Box(
                Modifier.size(40.dp).dragHandle(reorder, entry.id).semantics { contentDescription = t.reorder },
                contentAlignment = Alignment.Center,
            ) { LineIcon(Icon.Drag, color = Theme.colors.muted) }
        }
        if (expanded) {
            entry.sets.forEachIndexed { i, set ->
                key(set.id) {
                    SetRow(
                        number = i + 1,
                        type = type,
                        unit = unit,
                        language = language,
                        set = set,
                        onValues = { controller.setValues(set.id, it) },
                        onToggle = { controller.toggleSet(set.id) },
                        onRemove = { controller.edit { it.removeSet(set.id) } },
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                TextButton(t.addSet, { controller.edit { it.addSet(entry.id) } }, tone = Tone.Default)
                entry.sets.lastOrNull()?.let { last ->
                    TextButton(t.duplicateSet, { controller.edit { it.duplicateSet(last.id) } })
                }
            }
            NoteField(entry.id, entry.note) { v -> controller.edit { it.setExerciseNote(entry.id, v.take(300)) } }
            Row(Modifier.fillMaxWidth().padding(vertical = Space.m), horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                TextButton(t.replace, onReplace)
                TextButton(t.remove, { controller.edit { it.removeExercise(entry.id) } })
            }
        }
        Hairline()
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
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SetRow(
    number: Int,
    type: app.overfit.domain.TrackingType,
    unit: WeightUnit,
    language: Language,
    set: WorkoutSet,
    onValues: (SetValues) -> Unit,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    val t = LocalStrings.current
    val done = set.completed
    val actual = set.actual
    val target = set.target
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Text("$number", Modifier.width(20.dp), style = Theme.type.caption, tone = if (done) Tone.Default else Tone.Muted)
        if (type.usesWeight) {
            Row(Modifier.weight(1.3f), verticalAlignment = Alignment.CenterVertically) {
                NumberInput(
                    value = actual.weightKg,
                    resetKey = set.id,
                    format = { Numbers.formatWeight(it, unit, language) },
                    parse = { Numbers.parseWeight(it, unit) },
                    onCommit = { onValues(actual.copy(weightKg = it)) },
                    filter = { s -> s.filter { c -> c.isDigit() || c == ',' || c == '.' || c == '-' }.take(8) },
                    unit = unit.symbol,
                    keyboardType = KeyboardType.Decimal,
                    placeholder = target.weightKg?.let { Numbers.formatWeight(it, unit, language) } ?: unit.symbol,
                    modifier = Modifier.weight(1f),
                )
                // Many number keyboards have no minus key: negative weight = assistance (e.g. a band).
                TextButton("±", {
                    (actual.weightKg ?: target.weightKg)?.let { onValues(actual.copy(weightKg = -it)) }
                }, Modifier.padding(start = Space.xs))
            }
        }
        if (type.usesReps) {
            NumberInput(
                value = actual.reps,
                resetKey = set.id,
                format = { it.toString() },
                parse = { Numbers.parseCount(it) },
                onCommit = { onValues(actual.copy(reps = it)) },
                filter = { s -> s.filter(Char::isDigit).take(4) },
                unit = t.repsUnit,
                keyboardType = KeyboardType.Number,
                placeholder = target.reps?.toString() ?: t.repsUnit,
                modifier = Modifier.weight(1f),
            )
        }
        if (type.usesTime) {
            NumberInput(
                value = actual.durationSec,
                resetKey = set.id,
                format = { it.toString() },
                parse = { Numbers.parseDuration(it) },
                onCommit = { onValues(actual.copy(durationSec = it)) },
                filter = { s -> s.filter { c -> c.isDigit() || c == ':' }.take(6) },
                unit = t.secondsUnit,
                keyboardType = KeyboardType.Number,
                placeholder = target.durationSec?.toString() ?: t.secondsUnit,
                modifier = Modifier.weight(1f),
            )
        }
        CheckButton(done, if (done) t.reopenSet(number) else t.completeSet(number), onToggle)
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            IconButton(Icon.Close, t.removeSet(number), onRemove)
        }
    }
}

/** Round check-off button: open = outline, done = filled (tone, not colour). */
@Composable
private fun CheckButton(done: Boolean, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .semantics { contentDescription = description; role = Role.Checkbox }
            .pressableRow(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .then(
                    if (done) Modifier.background(Theme.colors.inverseBackground, CircleShape)
                    else Modifier.border(1.dp, Theme.colors.muted, CircleShape),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (done) LineIcon(Icon.Check, size = 20.dp, color = Theme.colors.inverseForeground)
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
    value: N?,
    resetKey: Any,
    format: (N) -> String,
    parse: (String) -> N?,
    onCommit: (N?) -> Unit,
    filter: (String) -> String,
    unit: String,
    keyboardType: KeyboardType,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    var text by remember(resetKey) { mutableStateOf(value?.let(format).orEmpty()) }
    LaunchedEffect(value) {
        if (parse(text) != value) text = value?.let(format).orEmpty()
    }
    SmallInput(
        text,
        { input ->
            val filtered = filter(input)
            text = filtered
            if (filtered.isBlank()) onCommit(null) else parse(filtered)?.let(onCommit)
        },
        unit = unit,
        modifier = modifier,
        keyboardType = keyboardType,
        placeholder = placeholder,
    )
}
