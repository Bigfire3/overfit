package app.overfit.ui.exercises

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.overfit.data.ExerciseRepository
import app.overfit.domain.Equipment
import app.overfit.domain.Exercise
import app.overfit.domain.ExerciseSearch
import app.overfit.domain.Language
import app.overfit.domain.Muscle
import app.overfit.domain.TemplateSummary
import app.overfit.i18n.LocalStrings
import app.overfit.ui.components.Chip
import app.overfit.ui.components.ExerciseImage
import app.overfit.ui.components.Hairline
import app.overfit.ui.share.ShareDependencies
import app.overfit.ui.share.TemplateImportRows
import app.overfit.ui.components.Icon
import app.overfit.ui.components.LineIcon
import app.overfit.ui.components.ListRow
import app.overfit.i18n.LocalExerciseFormStrings
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.Screen
import app.overfit.ui.components.SearchInput
import app.overfit.ui.components.Segmented
import app.overfit.ui.components.Text
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.components.pressableRow
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Search and filters over the catalogue (exercises tab and exercise picker). */
class CatalogViewModel(repository: ExerciseRepository) : ViewModel() {
    private val _filter = MutableStateFlow(ExerciseSearch.Filter())
    val filter: StateFlow<ExerciseSearch.Filter> = _filter.asStateFlow()

    private val index = repository.exercises.map { ExerciseSearch.index(it) }.flowOn(Dispatchers.Default)

    /** null = catalogue still loading. */
    val results: StateFlow<List<Exercise>?> = combine(index, _filter) { idx, f -> ExerciseSearch.search(idx, f) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setQuery(query: String) = _filter.update { it.copy(query = query) }
    fun toggleMuscle(muscle: Muscle) = _filter.update { it.copy(muscle = if (it.muscle == muscle) null else muscle) }
    fun toggleEquipment(equipment: Equipment) =
        _filter.update { it.copy(equipment = if (it.equipment == equipment) null else equipment) }
    fun toggleOnlyCustom() = _filter.update { it.copy(onlyCustom = !it.onlyCustom) }
}

/** Tab "Exercises": catalogue (search, filters, custom exercises). */
@Composable
fun ExercisesScreen(
    catalog: CatalogViewModel,
    language: Language,
    onOpenExercise: (Exercise) -> Unit,
    onNewCustomExercise: () -> Unit,
) {
    val t = LocalStrings.current
    // Only exercises here; the saved workouts live on the Training tab.
    Screen(footer = { PrimaryButton(t.newCustomExercise, onNewCustomExercise) }) {
        Title(t.exercisesTitle)
        Spacer(Modifier.height(Space.m))
        CatalogList(catalog, language, onClick = onOpenExercise)
    }
}

/** Search field, filter chips and the result list. With [selectedIds] it is a multi-select (picker). */
@Composable
fun ColumnScope.CatalogList(
    catalog: CatalogViewModel,
    language: Language,
    onClick: (Exercise) -> Unit,
    selectedIds: List<String>? = null,
    /** Picker: last row "Create new exercise" (with the search text as name). */
    onCreate: ((String) -> Unit)? = null,
) {
    val t = LocalStrings.current
    val filter by catalog.filter.collectAsStateWithLifecycle()
    val results by catalog.results.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(filter) { listState.scrollToItem(0) }

    SearchInput(filter.query, catalog::setQuery, t.searchPlaceholder, clearLabel = t.remove)
    // Chips and count scroll away with the list so the results get the room on small phones.
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(bottom = Space.m)) {
        item(key = "filters") {
            Spacer(Modifier.height(Space.m))
            ChipRow {
                Chip(t.filterCustom, filter.onlyCustom, catalog::toggleOnlyCustom)
                Muscle.entries.forEach { m -> Chip(t.muscle(m), filter.muscle == m, { catalog.toggleMuscle(m) }) }
            }
            Spacer(Modifier.height(Space.s))
            ChipRow {
                Equipment.entries.forEach { e -> Chip(t.equipmentName(e), filter.equipment == e, { catalog.toggleEquipment(e) }) }
            }
        }
        val list = results
        when {
            list == null -> item(key = "loading") { Text(t.catalogLoading, Modifier.padding(top = Space.l), tone = Tone.Muted) }
            list.isEmpty() -> item(key = "empty") {
                Text(
                    if (filter.onlyCustom && filter.query.isBlank()) t.noCustomExercises else t.noResults,
                    Modifier.padding(top = Space.l),
                    tone = Tone.Muted,
                )
            }
            else -> {
                item(key = "count") {
                    Text(t.exerciseCount(list.size), Modifier.padding(top = Space.m), style = Theme.type.caption, tone = Tone.Faint)
                }
                items(list, key = { it.id }) { exercise ->
                    ExerciseRow(exercise, language, selected = selectedIds?.contains(exercise.id)) { onClick(exercise) }
                }
            }
        }
        if (onCreate != null && list != null) item(key = "create") {
            val f = LocalExerciseFormStrings.current
            val query = filter.query.trim()
            Spacer(Modifier.height(Space.s))
            ListRow(if (query.isEmpty()) f.createInPickerEmpty else f.createInPicker(query), onClick = { onCreate(query) })
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) { content() }
}

@Composable
fun ExerciseRow(exercise: Exercise, language: Language, selected: Boolean?, onClick: () -> Unit) {
    val t = LocalStrings.current
    Column(Modifier.fillMaxWidth().pressableRow(onClick)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExerciseImage(exercise)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(exercise.name)
                val sub = listOfNotNull(
                    exercise.subtitle(language).ifEmpty { null },
                    if (exercise.isCustom) t.customExercise else null,
                ).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, style = Theme.type.caption, tone = Tone.Muted)
            }
            if (selected == true) {
                Box(Modifier.semantics { contentDescription = t.selected }) { LineIcon(Icon.Check) }
            }
        }
        Hairline()
    }
}

/**
 * Exercise picker for templates (later also the workout). Multi-select in tap order; in replace
 * mode ([single]) the first tap finishes.
 */
@Composable
fun ExercisePickerScreen(
    catalog: CatalogViewModel,
    language: Language,
    single: Boolean,
    onBack: () -> Unit,
    onPicked: (List<String>) -> Unit,
    /** Opens the custom-exercise form with the search text; null hides the row. */
    onCreate: ((String) -> Unit)? = null,
    /** Exercise just created (or chosen under "Did you mean …?") in the form above; it is added right away. */
    created: String? = null,
    onCreatedConsumed: () -> Unit = {},
) {
    val t = LocalStrings.current
    val selected = rememberSaveable(saver = selectionSaver) { mutableStateListOf<String>() }
    LaunchedEffect(created) {
        val id = created ?: return@LaunchedEffect
        onCreatedConsumed()
        onPicked(if (single) listOf(id) else (selected.toList() - id) + id)
    }
    Screen(
        header = { BackHeader(t.back, onBack) },
        footer = if (single) null else ({
            PrimaryButton(t.addExercises(selected.size), { onPicked(selected.toList()) }, enabled = selected.isNotEmpty())
        }),
    ) {
        Title(if (single) t.replaceExerciseTitle else t.chooseExercises)
        Spacer(Modifier.height(Space.m))
        CatalogList(
            catalog,
            language,
            onClick = { exercise ->
                when {
                    single -> onPicked(listOf(exercise.id))
                    exercise.id in selected -> selected.remove(exercise.id)
                    else -> selected.add(exercise.id)
                }
            },
            selectedIds = selected,
            onCreate = onCreate,
        )
    }
}

private val selectionSaver = androidx.compose.runtime.saveable.listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { mutableStateListOf<String>().apply { addAll(it) } },
)
