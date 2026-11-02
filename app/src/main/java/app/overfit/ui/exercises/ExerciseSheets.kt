package app.overfit.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import app.overfit.data.CustomExerciseInput
import app.overfit.domain.Equipment
import app.overfit.domain.Exercise
import app.overfit.domain.Muscle
import app.overfit.domain.Numbers
import app.overfit.domain.TrackingType
import app.overfit.ui.components.ExerciseImage

/**
 * Details einer Übung (keine Anleitungstexte, nur Bild, Name, Muskelgruppen). Tracking-Typ und
 * Pausenzeit sind auch für Katalogübungen änderbar; eigene Übungen lassen sich bearbeiten/löschen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailSheet(
    exercise: Exercise,
    onDismiss: () -> Unit,
    onSaveSettings: (TrackingType, Int?) -> Unit,
    onEditCustom: () -> Unit,
    onDeleteCustom: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tracking by remember(exercise.id) { mutableStateOf(exercise.trackingType) }
    var rest by remember(exercise.id) { mutableStateOf(exercise.defaultRestSec?.toString().orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ExerciseImage(exercise, size = 120.dp)
                Column(Modifier.weight(1f)) {
                    Text(exercise.name, style = MaterialTheme.typography.titleLarge)
                    if (exercise.primaryMuscles.isNotEmpty()) {
                        Text(exercise.primaryMuscles.joinToString { it.label }, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (exercise.secondaryMuscles.isNotEmpty()) {
                        Text(
                            "Auch: " + exercise.secondaryMuscles.joinToString { it.label },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    exercise.equipment?.let {
                        Text(it.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (exercise.isCustom) {
                Text(
                    "Erfassung: ${exercise.trackingType.label}" +
                        (exercise.defaultRestSec?.let { " · Pause ${Numbers.formatDuration(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onEditCustom, modifier = Modifier.weight(1f)) { Text("Bearbeiten") }
                    OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) { Text("Löschen") }
                }
            } else {
                Text("Erfassung", style = MaterialTheme.typography.titleSmall)
                TrackingTypeChooser(tracking, onChange = { tracking = it })
                RestField(rest, onChange = { rest = it })
                Button(
                    onClick = { onSaveSettings(tracking, rest.toIntOrNull()) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Speichern") }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Übung löschen?") },
            text = { Text("„${exercise.name}“ wird gelöscht. Wird sie noch in einer Vorlage verwendet, wird sie nur ausgeblendet.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDeleteCustom() }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

/** Anlegen oder Bearbeiten einer eigenen Übung. */
@Composable
fun CustomExerciseDialog(
    initial: Exercise?,
    initialName: String = "",
    onDismiss: () -> Unit,
    onSave: (CustomExerciseInput) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: initialName) }
    var tracking by rememberSaveable { mutableStateOf(initial?.trackingType ?: TrackingType.WEIGHT_REPS) }
    var muscle by rememberSaveable { mutableStateOf(initial?.primaryMuscles?.firstOrNull()) }
    var equipment by rememberSaveable { mutableStateOf(initial?.equipment) }
    var rest by rememberSaveable { mutableStateOf(initial?.defaultRestSec?.toString().orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Eigene Übung" else "Übung bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Erfassung", style = MaterialTheme.typography.titleSmall)
                TrackingTypeChooser(tracking, onChange = { tracking = it })
                OptionDropdown("Muskelgruppe (optional)", muscle, Muscle.entries, { it.label }, { muscle = it })
                OptionDropdown("Equipment (optional)", equipment, Equipment.entries, { it.label }, { equipment = it })
                RestField(rest, onChange = { rest = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(CustomExerciseInput(name, tracking, rest.toIntOrNull(), muscle, equipment)) },
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrackingTypeChooser(selected: TrackingType, onChange: (TrackingType) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TrackingType.entries.forEach { type ->
            FilterChip(selected = type == selected, onClick = { onChange(type) }, label = { Text(type.label) })
        }
    }
}

@Composable
private fun RestField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
        label = { Text("Pause in Sekunden") },
        placeholder = { Text("Standard aus den Einstellungen") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> OptionDropdown(
    label: String,
    selected: T?,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.let(optionLabel) ?: "–",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("–") }, onClick = { onSelect(null); expanded = false })
            options.forEach { option ->
                DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}
