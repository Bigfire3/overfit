package app.overfit.fuel

import kotlin.math.pow

enum class RevealPhase { Black, Counting, Done }

/**
 * Timeline of the invite reveal: screen goes black and the heartbeat pulses play ([blackMs]), then the number counts up from
 * 0,00 to [targetMicro] over [countMs] (ease-out, so the last cents arrive slowly), then a strong pulse. Pure logic, no clock.
 */
class RevealPlan(val targetMicro: Long, val blackMs: Long = 1_100, val countMs: Long = 2_400) {
    val totalMs: Long get() = blackMs + countMs

    fun phaseAt(elapsedMs: Long): RevealPhase = when {
        elapsedMs < blackMs -> RevealPhase.Black
        elapsedMs < totalMs -> RevealPhase.Counting
        else -> RevealPhase.Done
    }

    /** Amount shown at [elapsedMs]: 0 during the black phase, exactly [targetMicro] when done; never decreasing. */
    fun valueAt(elapsedMs: Long): Long {
        if (elapsedMs < blackMs) return 0
        if (elapsedMs >= totalMs) return targetMicro
        val p = (elapsedMs - blackMs).toDouble() / countMs
        val eased = 1.0 - (1.0 - p).pow(3)
        return (targetMicro * eased).toLong().coerceIn(0, targetMicro)
    }
}
