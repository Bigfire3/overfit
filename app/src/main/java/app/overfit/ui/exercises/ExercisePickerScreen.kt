package app.overfit.ui.exercises

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Übungsauswahl für Vorlagen (später auch Workout-Modus). Mehrfachauswahl in Tipp-Reihenfolge;
 * im Ersetzen-Modus ([single]) beendet der erste Tipp die Auswahl.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerScreen(
    single: Boolean,
    onBack: () -> Unit,
    onPicked: (List<String>) -> Unit,
    viewModel: CatalogViewModel = viewModel(factory = CatalogViewModel.Factory),
) {
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val selected = rememberSaveable(saver = listSaver()) { mutableStateListOf<String>() }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (single) "Übung ersetzen" else "Übungen hinzufügen") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
                },
                actions = {
                    IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, contentDescription = "Eigene Übung") }
                },
            )
        },
        bottomBar = {
            if (!single && selected.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { onPicked(selected.toList()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp)
                            .height(56.dp),
                    ) { Text("${selected.size} hinzufügen") }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            CatalogList(
                filter = filter,
                results = results,
                onQueryChange = viewModel::setQuery,
                onMuscleChange = viewModel::setMuscle,
                onEquipmentChange = viewModel::setEquipment,
                onOnlyCustomChange = viewModel::setOnlyCustom,
                onExerciseClick = { exercise ->
                    when {
                        single -> onPicked(listOf(exercise.id))
                        exercise.id in selected -> selected.remove(exercise.id)
                        else -> selected.add(exercise.id)
                    }
                },
                selectedIds = selected.toSet(),
            )
        }
    }

    if (creating) {
        CustomExerciseDialog(
            initial = null,
            initialName = filter.query.trim(),
            onDismiss = { creating = false },
            onSave = { input ->
                creating = false
                viewModel.saveCustom(null, input) { id ->
                    if (single) onPicked(listOf(id)) else selected.add(id)
                }
            },
        )
    }
}

private fun listSaver() = androidx.compose.runtime.saveable.listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { mutableStateListOf<String>().apply { addAll(it) } },
)
