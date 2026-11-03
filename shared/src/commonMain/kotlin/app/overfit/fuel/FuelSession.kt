package app.overfit.fuel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the credit/invite screens talk to: the real backend when a URL is configured, or the local demo while the user
 * is looking at it. Demo state is per process and never persisted.
 */
class FuelSession(val access: FuelAccess, private val demoService: FuelService = DemoFuelService()) {
    private val _demo = MutableStateFlow(false)
    val demo: StateFlow<Boolean> = _demo.asStateFlow()

    fun startDemo() { _demo.value = true }
    fun endDemo() { _demo.value = false }

    /** Null = no backend configured and no demo running ("Fuel startet bald"). */
    fun current(): FuelService? = if (_demo.value) demoService else access.service()
}
