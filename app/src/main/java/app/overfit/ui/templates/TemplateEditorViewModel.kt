package app.overfit.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overfit.data.ExerciseRepository
import app.overfit.data.TemplateRepository
import app.overfit.ui.appViewModelFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TemplateEditorViewModel(
    private val templateId: Long,
    private val templates: TemplateRepository,
    private val exercises: ExerciseRepository,
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val draft: TemplateDraft = TemplateDraft(),
        val dirty: Boolean = false,
        val saving: Boolean = false,
    )

    sealed interface Event {
        /** Editor schließen; [message] zeigt der vorherige Screen an. */
        data class Closed(val message: String? = null) : Event
        data class Message(val text: String) : Event
    }

    private var original = TemplateDraft()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Übung, die nach der Auswahl ersetzt werden soll (Ersetzen-Modus des Pickers). */
    var pendingReplaceKey: Long? = null

    init {
        viewModelScope.launch {
            val draft = if (templateId == 0L) {
                TemplateDraft()
            } else {
                templates.get(templateId)?.let(TemplateDraft::from) ?: run {
                    _events.send(Event.Closed("Vorlage nicht gefunden"))
                    TemplateDraft()
                }
            }
            original = draft
            _state.value = State(loading = false, draft = draft)
        }
    }

    fun edit(transform: (TemplateDraft) -> TemplateDraft) {
        _state.update { s ->
            val draft = transform(s.draft)
            s.copy(draft = draft, dirty = draft != original)
        }
    }

    /** Ergebnis der Übungsauswahl: hinzufügen oder (bei [pendingReplaceKey]) ersetzen. */
    fun onExercisesPicked(ids: List<String>) {
        if (ids.isEmpty()) return
        val replaceKey = pendingReplaceKey
        pendingReplaceKey = null
        viewModelScope.launch {
            val picked = exercises.getAll(ids)
            if (replaceKey != null) {
                picked.firstOrNull()?.let { ex -> edit { it.replaceExercise(replaceKey, ex) } }
            } else {
                edit { it.addExercises(picked) }
            }
        }
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        s.draft.validationError()?.let { msg ->
            viewModelScope.launch { _events.send(Event.Message(msg)) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching { templates.save(s.draft.toTemplate()) }
                .onSuccess {
                    _events.send(Event.Closed("Vorlage gespeichert"))
                }
                .onFailure {
                    _state.update { st -> st.copy(saving = false) }
                    _events.send(Event.Message("Speichern fehlgeschlagen: ${it.message}"))
                }
        }
    }

    fun delete() {
        viewModelScope.launch {
            if (templateId != 0L) templates.delete(templateId)
            _events.send(Event.Closed("Vorlage gelöscht"))
        }
    }

    companion object {
        fun factory(templateId: Long) = appViewModelFactory { c, _ ->
            TemplateEditorViewModel(templateId, c.templateRepository, c.exerciseRepository)
        }
    }
}
