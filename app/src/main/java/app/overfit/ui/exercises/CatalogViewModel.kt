package app.overfit.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overfit.data.CustomExerciseInput
import app.overfit.data.ExerciseRepository
import app.overfit.domain.Equipment
import app.overfit.domain.Exercise
import app.overfit.domain.ExerciseSearch
import app.overfit.domain.Muscle
import app.overfit.domain.TrackingType
import app.overfit.ui.appViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Suche, Filter und Bearbeitung im Übungskatalog (Tab „Übungen“ und Übungsauswahl). */
class CatalogViewModel(private val repository: ExerciseRepository) : ViewModel() {

    private val _filter = MutableStateFlow(ExerciseSearch.Filter())
    val filter: StateFlow<ExerciseSearch.Filter> = _filter.asStateFlow()

    private val index = repository.exercises
        .map { ExerciseSearch.index(it) }
        .flowOn(Dispatchers.Default)

    /** null = Katalog wird noch geladen. */
    val results: StateFlow<List<Exercise>?> = combine(index, _filter) { idx, f -> ExerciseSearch.search(idx, f) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun setQuery(query: String) = _filter.update { it.copy(query = query) }
    fun setMuscle(muscle: Muscle?) = _filter.update { it.copy(muscle = muscle) }
    fun setEquipment(equipment: Equipment?) = _filter.update { it.copy(equipment = equipment) }
    fun setOnlyCustom(onlyCustom: Boolean) = _filter.update { it.copy(onlyCustom = onlyCustom) }

    /** Legt eine eigene Übung an und meldet die neue ID (z. B. zum direkten Auswählen im Picker). */
    fun saveCustom(id: String?, input: CustomExerciseInput, onSaved: (String) -> Unit = {}) {
        viewModelScope.launch {
            val savedId = if (id == null) repository.createCustom(input) else id.also { repository.updateCustom(it, input) }
            _messages.send(if (id == null) "„${input.name.trim()}“ angelegt" else "Gespeichert")
            onSaved(savedId)
        }
    }

    fun updateSettings(id: String, trackingType: TrackingType, restSec: Int?) {
        viewModelScope.launch {
            repository.updateSettings(id, trackingType, restSec)
            _messages.send("Gespeichert")
        }
    }

    fun deleteCustom(exercise: Exercise) {
        viewModelScope.launch {
            val msg = when (repository.deleteCustom(exercise.id)) {
                ExerciseRepository.DeleteResult.DELETED -> "„${exercise.name}“ gelöscht"
                ExerciseRepository.DeleteResult.ARCHIVED -> "„${exercise.name}“ ausgeblendet (wird noch in einer Vorlage verwendet)"
            }
            _messages.send(msg)
        }
    }

    companion object {
        val Factory = appViewModelFactory { c, _ -> CatalogViewModel(c.exerciseRepository) }
    }
}
