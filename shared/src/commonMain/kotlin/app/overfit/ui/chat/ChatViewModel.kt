package app.overfit.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overfit.chat.ChatFeature
import app.overfit.chat.ChatStatus
import app.overfit.chat.PairingCode
import app.overfit.fuel.FuelException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ChatError { NETWORK, NOT_CONFIGURED, GENERIC }

fun Throwable.toChatError(): ChatError = when ((this as? FuelException)?.code) {
    "network" -> ChatError.NETWORK
    "not_configured" -> ChatError.NOT_CONFIGURED
    else -> ChatError.GENERIC
}

/** State of the "connect AI chat" section: server status, pairing code, errors. Uploading itself lives in ChatSync. */
class ChatViewModel(
    private val chat: ChatFeature,
    /** App-lifetime scope: switching on and disconnecting must finish even when the screen is left. */
    private val appScope: CoroutineScope,
    private val clock: () -> Long,
    private val pollMs: Long = 3_000,
) : ViewModel() {
    data class UiState(
        val status: ChatStatus? = null,
        val pairing: PairingCode? = null,
        /** Local time (ms) the pairing code was received; the countdown runs from here. */
        val pairingAt: Long = 0,
        val newClient: String? = null,
        val busy: Boolean = false,
        val error: ChatError? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var poll: Job? = null
    private var baseline: Set<String> = emptySet()

    fun refresh() {
        val api = chat.service() ?: return
        viewModelScope.launch {
            try {
                val status = api.status()
                _state.update { it.copy(status = status, error = null) }
                if (!status.enabled && chat.sync.state.value.enabled) chat.sync.serverSaysOff()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.toChatError()) }
            }
        }
    }

    fun enable(photos: Boolean) {
        _state.update { it.copy(busy = true, error = null) }
        appScope.launch {
            try {
                chat.sync.enable(photos)
                _state.update { it.copy(busy = false) }
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.toChatError()) }
            }
        }
    }

    fun setPhotos(on: Boolean) {
        _state.update { it.copy(busy = true, error = null) }
        appScope.launch {
            try {
                chat.sync.setPhotosEnabled(on)
                _state.update { it.copy(busy = false) }
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.toChatError()) }
            }
        }
    }

    fun disable() {
        poll?.cancel()
        _state.update { it.copy(busy = true, error = null) }
        appScope.launch {
            try {
                chat.disable()
                _state.value = UiState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.toChatError()) }
            }
        }
    }

    fun revoke(grantId: String) {
        val api = chat.service() ?: return
        viewModelScope.launch {
            try {
                api.revoke(grantId)
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.toChatError()) }
            }
        }
    }

    /** Asks for a pairing code and then watches the connection list until a new chat shows up. */
    fun startPairing() {
        val api = chat.service() ?: return
        poll?.cancel()
        _state.update { it.copy(busy = true, error = null, newClient = null) }
        poll = viewModelScope.launch {
            try {
                baseline = api.status().connections.map { it.id }.toSet()
                val code = api.pairing()
                val at = clock()
                _state.update { it.copy(busy = false, pairing = code, pairingAt = at, newClient = null) }
                while (clock() - at < code.ttlSec * 1000L) {
                    delay(pollMs)
                    val status = api.status()
                    _state.update { it.copy(status = status) }
                    val fresh = status.connections.firstOrNull { it.id !in baseline }
                    if (fresh != null) {
                        _state.update { it.copy(pairing = null, newClient = fresh.clientName.ifBlank { "Chat" }) }
                        return@launch
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.toChatError()) }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    override fun onCleared() { poll?.cancel() }
}
