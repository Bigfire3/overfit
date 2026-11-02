package app.overfit.ui.exercises

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.overfit.domain.Exercise
import app.overfit.ui.templates.TemplateList
import app.overfit.ui.templates.TemplateListViewModel

/** Tab „Übungen“: Katalog (Suche, Filter, eigene Übungen) und Vorlagen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen(
    onOpenTemplate: (Long) -> Unit,
    onNewTemplate: () -> Unit,
    catalogViewModel: CatalogViewModel = viewModel(factory = CatalogViewModel.Factory),
    templatesViewModel: TemplateListViewModel = viewModel(factory = TemplateListViewModel.Factory),
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val filter by catalogViewModel.filter.collectAsStateWithLifecycle()
    val results by catalogViewModel.results.collectAsStateWithLifecycle()
    val templates by templatesViewModel.templates.collectAsStateWithLifecycle()
    var detail by remember { mutableStateOf<Exercise?>(null) }
    var editing by remember { mutableStateOf<Exercise?>(null) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { catalogViewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Übungen") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (tab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { creating = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Eigene Übung") },
                )
            } else {
                ExtendedFloatingActionButton(
                    onClick = onNewTemplate,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Neue Vorlage") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Katalog") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Vorlagen") })
            }
            if (tab == 0) {
                CatalogList(
                    filter = filter,
                    results = results,
                    onQueryChange = catalogViewModel::setQuery,
                    onMuscleChange = catalogViewModel::setMuscle,
                    onEquipmentChange = catalogViewModel::setEquipment,
                    onOnlyCustomChange = catalogViewModel::setOnlyCustom,
                    onExerciseClick = { detail = it },
                )
            } else {
                TemplateList(templates, onOpen = onOpenTemplate)
            }
        }
    }

    detail?.let { exercise ->
        ExerciseDetailSheet(
            exercise = exercise,
            onDismiss = { detail = null },
            onSaveSettings = { type, rest ->
                catalogViewModel.updateSettings(exercise.id, type, rest)
                detail = null
            },
            onEditCustom = { detail = null; editing = exercise },
            onDeleteCustom = { detail = null; catalogViewModel.deleteCustom(exercise) },
        )
    }
    if (creating || editing != null) {
        CustomExerciseDialog(
            initial = editing,
            initialName = if (creating) filter.query.trim() else "",
            onDismiss = { creating = false; editing = null },
            onSave = { input ->
                catalogViewModel.saveCustom(editing?.id, input)
                creating = false
                editing = null
            },
        )
    }
}
