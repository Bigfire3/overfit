package app.overfit.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.unit.dp
import app.overfit.data.WorkoutRepository
import app.overfit.domain.Language
import app.overfit.domain.WeightUnit
import app.overfit.i18n.LocalStrings
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.Block
import app.overfit.ui.components.BlockButton
import app.overfit.ui.components.CheckRow
import app.overfit.ui.components.ExerciseImage
import app.overfit.ui.components.MultilineInput
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.Screen
import app.overfit.ui.components.SectionTitle
import app.overfit.ui.components.Text
import app.overfit.ui.components.BigInput
import app.overfit.ui.components.AnimatedItems
import app.overfit.ui.components.Hint
import app.overfit.ui.components.PanelItem
import app.overfit.ui.components.PanelSection
import app.overfit.ui.components.RevealNotNull
import app.overfit.ui.components.Swap
import app.overfit.ui.components.rememberHaptics
import app.overfit.ui.components.Tone
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import app.overfit.ui.workout.WorkoutSetRow
import app.overfit.ui.workout.setColumnLabels
import app.overfit.ui.components.SetColumnsHeader
import androidx.compose.ui.focus.FocusRequester
import app.overfit.workout.FinishedEdit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Edit a finished training afterwards: name, feeling, sauna, note and the sets (same inputs as during the
 * workout; add, remove by swiping, check off). "Save" writes everything at once; back with changes
 * asks for a second back press (= discard), like the workout editor.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun WorkoutEditScreen(
    workoutId: String,
    workouts: WorkoutRepository,
    unit: WeightUnit,
    language: Language,
    appScope: CoroutineScope,
    onClose: () -> Unit,
    /** Tap on an exercise's image: its details (read only), like in the running workout. */
    onExerciseDetails: (String) -> Unit = {},
) {
    val t = LocalStrings.current
    val h = t.history
    var original by remember(workoutId) { mutableStateOf<FinishedEdit?>(null) }
    var draft by remember(workoutId) { mutableStateOf<FinishedEdit?>(null) }
    LaunchedEffect(workoutId) {
        val finished = workouts.getFinished(workoutId)
        if (finished == null) { onClose(); return@LaunchedEffect }
        val edit = FinishedEdit.of(finished)
        original = edit
        draft = edit
    }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var backArmed by remember { mutableStateOf(false) }
    LaunchedEffect(backArmed) { if (backArmed) { delay(4000); backArmed = false } }
    val dirty = draft != null && draft != original

    val haptics = rememberHaptics()
    val back = { if (dirty && !backArmed) { backArmed = true; haptics.arm() } else onClose() }
    BackHandler(enabled = dirty, onBack = back)
    val save = save@{
        val d = draft ?: return@save
        if (saving) return@save
        if (!dirty) { onClose(); return@save }
        saving = true
        failed = false
        appScope.launch {
            val ok = runCatching { workouts.updateFinished(d.workout, d.note, d.feeling, d.sauna) }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                saving = false
                if (ok) onClose() else failed = true
            }
        }
        Unit
    }

    Screen(
        scroll = true,
        header = { BackHeader(t.back, back) },
        footer = {
            // The hint grows in above the button (and away again) instead of jumping; switching between them fades.
            val message = when {
                backArmed -> t.unsavedHint
                failed -> h.editSaveFailed
                else -> null
            }
            RevealNotNull(message, from = Alignment.Bottom) { m ->
                Swap(m) { Text(it, style = Theme.type.caption, tone = Tone.Muted) }
            }
            PrimaryButton(t.save, save, enabled = draft != null, loading = saving)
        },
    ) {
        // Loaded a moment later: the content fades in instead of popping.
        Swap(draft, contentKey = { it != null }) { loaded ->
            val d = loaded ?: return@Swap
            Column(Modifier.fillMaxWidth()) {
                Text(h.editTitle, tone = Tone.Muted)
                Spacer(Modifier.height(Space.s))
                // The name of this training (not of its saved workout): tap and type, like the workout editor.
                BigInput(d.workout.name, { draft = draft?.withName(it) }, h.trainingNamePlaceholder, Modifier.fillMaxWidth())

                // The sets come first: the pencil is mostly used to correct them (audit 17.11.).
                if (d.workout.exercises.isEmpty()) {
                    Spacer(Modifier.height(Space.l))
                    Text(h.editNoExercises, tone = Tone.Muted)
                }
                d.workout.exercises.forEach { e ->
                    key(e.id) {
                        Spacer(Modifier.height(Space.l))
                        Block {
                            // Same card head as in the running workout (48 dp image, name, sets).
                            app.overfit.ui.components.ExerciseCardHeader(e.exercise, listOf(t.setsDone(e.completedSets, e.sets.size)), onDetails = { onExerciseDetails(e.exercise.id) })
                            Column(Modifier.padding(top = Space.xs)) {
                                SetColumnsHeader(t.setColumn, setColumnLabels(e.exercise.trackingType, unit, t), trailing = 48.dp)
                                Spacer(Modifier.height(Space.xs))
                                val focus = remember(e.id) { mutableMapOf<String, FocusRequester>() }
                                fun focusOf(id: String) = focus.getOrPut(id) { FocusRequester() }
                                // A new set grows in, a removed one shrinks away; the rows and cards below slide.
                                AnimatedItems(e.sets, key = { it.id }, spacing = Space.xs) { i, set ->
                                    WorkoutSetRow(
                                        focusRequester = focusOf(set.id),
                                        next = e.sets.getOrNull(i + 1)?.let { focusOf(it.id) },
                                        number = i + 1,
                                        type = e.exercise.trackingType,
                                        unit = unit,
                                        language = language,
                                        set = set,
                                        onValues = { v -> draft = draft?.setValues(set.id, v) },
                                        onToggle = { draft = draft?.toggleSet(set.id) },
                                        onRemove = { draft = draft?.removeSet(set.id) },
                                    )
                                }
                                BlockButton(t.addSet, { haptics.tick(); draft = draft?.addSet(e.id) }, Modifier.fillMaxWidth(), inBlock = true)
                            }
                        }
                    }
                }

                // Same block as on the finish screen.
                Spacer(Modifier.height(Space.l))
                PanelSection(h.feeling) {
                    PanelItem { FeelingPicker(d.feeling, h.feelingValue) { draft = draft?.withFeeling(it) } }
                    CheckRow(h.sauna, d.sauna) { draft = draft?.withSauna(it) }
                }

                SectionTitle(h.note)
                MultilineInput(d.note, { draft = draft?.withNote(it) }, h.notePlaceholder)

                Hint(h.editHint)
            }
        }
    }
}
