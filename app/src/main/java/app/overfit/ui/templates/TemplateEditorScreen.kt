package app.overfit.ui.templates

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.overfit.domain.Numbers
import app.overfit.domain.TrackingType
import app.overfit.ui.components.DurationField
import app.overfit.ui.components.ExerciseImage
import app.overfit.ui.components.RepsField
import app.overfit.ui.components.WeightField
import app.overfit.ui.templates.TemplateDraft.DraftExercise

/**
 * Vorlage anlegen/bearbeiten: Name, Übungen (einklappbare Karten) mit geplanten Sätzen und
 * Zielwerten, Pausenzeit und Notiz je Übung. Speichern schreibt alles in einer Transaktion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(
    viewModel: TemplateEditorViewModel,
    onClose: () -> Unit,
    onAddExercises: () -> Unit,
    onReplaceExercise: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var collapsed by rememberSaveable { mutableStateOf(setOf<Long>()) }
    val draft = state.draft

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TemplateEditorViewModel.Event.Closed -> {
                    event.message?.let(onMessage)
                    onClose()
                }
                is TemplateEditorViewModel.Event.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }
    val back = { if (state.dirty) confirmDiscard = true else onClose() }
    BackHandler(enabled = state.dirty) { confirmDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (draft.isNew) "Neue Vorlage" else "Vorlage bearbeiten") },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") } },
                actions = {
                    IconButton(onClick = viewModel::save, enabled = !state.loading && !state.saving) {
                        Icon(Icons.Filled.Check, contentDescription = "Speichern")
                    }
                    if (!draft.isNew) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Mehr") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Vorlage löschen") },
                                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                    onClick = { menuOpen = false; confirmDelete = true },
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "name") {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { v -> viewModel.edit { it.withName(v.take(60)) } },
                    label = { Text("Name der Vorlage") },
                    placeholder = { Text("z. B. Push, Pull, Ganzkörper A") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            itemsIndexed(draft.exercises, key = { _, e -> e.key }) { index, entry ->
                ExerciseCard(
                    entry = entry,
                    expanded = entry.key !in collapsed,
                    isFirst = index == 0,
                    isLast = index == draft.exercises.lastIndex,
                    onToggle = { collapsed = if (entry.key in collapsed) collapsed - entry.key else collapsed + entry.key },
                    onEdit = viewModel::edit,
                    onReplace = { viewModel.pendingReplaceKey = entry.key; onReplaceExercise() },
                )
            }
            item(key = "add") {
                OutlinedButton(onClick = onAddExercises, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Übung hinzufügen", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Änderungen verwerfen?") },
            text = { Text("Die Vorlage wurde geändert, aber noch nicht gespeichert.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text("Verwerfen") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false; viewModel.save() }) { Text("Speichern") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Vorlage löschen?") },
            text = { Text("„${draft.name}“ wird gelöscht. Deine Übungen bleiben erhalten.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun ExerciseCard(
    entry: DraftExercise,
    expanded: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onToggle: () -> Unit,
    onEdit: ((TemplateDraft) -> TemplateDraft) -> Unit,
    onReplace: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val key = entry.key
    val type = entry.exercise.trackingType
    Card(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExerciseImage(entry.exercise, size = 48.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(entry.exercise.name, style = MaterialTheme.typography.titleMedium)
                val sets = entry.sets.size
                Text(
                    "$sets ${if (sets == 1) "Satz" else "Sätze"}" + (entry.rest.toIntOrNull()?.let { " · Pause ${Numbers.formatDuration(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Einklappen" else "Ausklappen",
            )
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Optionen für ${entry.exercise.name}") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (!isFirst) DropdownMenuItem(text = { Text("Nach oben") }, onClick = { menuOpen = false; onEdit { it.moveExercise(key, -1) } })
                    if (!isLast) DropdownMenuItem(text = { Text("Nach unten") }, onClick = { menuOpen = false; onEdit { it.moveExercise(key, 1) } })
                    DropdownMenuItem(text = { Text("Ersetzen") }, onClick = { menuOpen = false; onReplace() })
                    DropdownMenuItem(text = { Text("Entfernen") }, onClick = { menuOpen = false; onEdit { it.removeExercise(key) } })
                }
            }
        }
        if (expanded) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                entry.sets.forEachIndexed { i, set ->
                    SetRow(
                        number = i + 1,
                        type = type,
                        set = set,
                        onChange = { transform -> onEdit { it.updateSet(key, set.key, transform) } },
                        onRemove = { onEdit { it.removeSet(key, set.key) } },
                    )
                }
                TextButton(onClick = { onEdit { it.addSet(key) } }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Satz hinzufügen", modifier = Modifier.padding(start = 4.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = entry.rest,
                        onValueChange = { v -> onEdit { it.setRest(key, v) } },
                        label = { Text("Pause (s)") },
                        placeholder = { Text("Standard") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(120.dp),
                    )
                    OutlinedTextField(
                        value = entry.note,
                        onValueChange = { v -> onEdit { it.setNote(key, v.take(300)) } },
                        label = { Text("Notiz") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SetRow(
    number: Int,
    type: TrackingType,
    set: TemplateDraft.DraftSet,
    onChange: ((TemplateDraft.DraftSet) -> TemplateDraft.DraftSet) -> Unit,
    onRemove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "$number",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(20.dp),
        )
        if (type.usesWeight) {
            WeightField(set.weight, { v -> onChange { it.copy(weight = v) } }, Modifier.weight(1.3f))
        }
        if (type.usesReps) {
            RepsField(set.reps, { v -> onChange { it.copy(reps = v) } }, Modifier.weight(1f))
        }
        if (type.usesTime) {
            DurationField(set.duration, { v -> onChange { it.copy(duration = v) } }, Modifier.weight(1f))
        }
        IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Satz $number entfernen") }
    }
}
