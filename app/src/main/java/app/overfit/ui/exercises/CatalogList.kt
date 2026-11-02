package app.overfit.ui.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import app.overfit.domain.Equipment
import app.overfit.domain.Exercise
import app.overfit.domain.ExerciseSearch
import app.overfit.domain.Muscle
import app.overfit.ui.components.EmptyState
import app.overfit.ui.components.ExerciseImage

/** Suchfeld, Filter-Chips und Ergebnisliste. Mit [selectedIds] als Mehrfachauswahl (Picker). */
@Composable
fun CatalogList(
    filter: ExerciseSearch.Filter,
    results: List<Exercise>?,
    onQueryChange: (String) -> Unit,
    onMuscleChange: (Muscle?) -> Unit,
    onEquipmentChange: (Equipment?) -> Unit,
    onOnlyCustomChange: (Boolean) -> Unit,
    onExerciseClick: (Exercise) -> Unit,
    modifier: Modifier = Modifier,
    selectedIds: Set<String>? = null,
    contentPadding: PaddingValues = PaddingValues(bottom = 88.dp),
) {
    val listState = rememberLazyListState()
    LaunchedEffect(filter) { listState.scrollToItem(0) }
    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = filter.query,
            onValueChange = onQueryChange,
            placeholder = { Text("Übung suchen (auch deutsch, z. B. Klimmzug)") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (filter.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Filled.Clear, contentDescription = "Suche leeren") }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DropdownFilterChip(
                allLabel = "Muskelgruppe",
                selected = filter.muscle,
                options = Muscle.entries,
                label = { it.label },
                onSelect = onMuscleChange,
            )
            DropdownFilterChip(
                allLabel = "Equipment",
                selected = filter.equipment,
                options = Equipment.entries,
                label = { it.label },
                onSelect = onEquipmentChange,
            )
            FilterChip(
                selected = filter.onlyCustom,
                onClick = { onOnlyCustomChange(!filter.onlyCustom) },
                label = { Text("Eigene") },
            )
        }
        when {
            results == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            results.isEmpty() -> EmptyState(
                if (filter.onlyCustom && filter.query.isBlank()) "Noch keine eigenen Übungen. Lege über „Eigene Übung“ eine an."
                else "Keine passende Übung gefunden. Du kannst sie als eigene Übung anlegen.",
            )
            else -> LazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
                item(key = "count") {
                    Text(
                        "${results.size} Übungen",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                items(results, key = { it.id }) { exercise ->
                    ExerciseRow(
                        exercise = exercise,
                        selected = selectedIds?.contains(exercise.id),
                        onClick = { onExerciseClick(exercise) },
                    )
                }
            }
        }
    }
}

@Composable
fun ExerciseRow(exercise: Exercise, selected: Boolean?, onClick: () -> Unit) {
    ListItem(
        leadingContent = { ExerciseImage(exercise) },
        headlineContent = { Text(exercise.name) },
        supportingContent = {
            val sub = listOfNotNull(exercise.subtitle.ifEmpty { null }, if (exercise.isCustom) "Eigene" else null).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub)
        },
        trailingContent = if (selected == true) {
            { Icon(Icons.Filled.CheckCircle, contentDescription = "Ausgewählt", tint = MaterialTheme.colorScheme.primary) }
        } else {
            null
        },
        colors = if (selected == true) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
        } else {
            ListItemDefaults.colors()
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun <T> DropdownFilterChip(
    allLabel: String,
    selected: T?,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { open = true },
            label = { Text(selected?.let(label) ?: allLabel) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Alle") }, onClick = { onSelect(null); open = false })
            options.forEach { option ->
                DropdownMenuItem(text = { Text(label(option)) }, onClick = { onSelect(option); open = false })
            }
        }
    }
}
