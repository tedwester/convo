package tedwester.convo.ui.chat

/**
 * Drives the haptics that accompany a pull gesture.
 *
 * A light haptic is fired as the pull passes each quarter, and a firmer one when it reaches
 * the end. Backing off and pulling again replays them: only a threshold crossing counts, so
 * the gesture never demands one continuous motion.
 */
class PullHaptics {
    private var tickLevel = 0
    private var confirmed = false

    /**
     * Reports a new progress value and fires the matching haptics.
     *
     * @return true the first time the pull reaches full since backing off, so the caller can
     *   fire its action.
     */
    fun onProgress(
        progress: Float,
        tick: () -> Unit,
        confirm: () -> Unit,
    ): Boolean {
        val clamped = progress.coerceIn(0f, 1f)
        // Backing off drops the tick level again (with a little hysteresis so hovering on a
        // boundary doesn't chatter), so pulling back through replays the ticks.
        while (tickLevel > 0 && clamped < tickLevel * TickQuarter - TickHysteresis) {
            tickLevel -= 1
        }
        while (tickLevel < 3 && clamped >= (tickLevel + 1) * TickQuarter) {
            tickLevel += 1
            tick()
        }
        if (clamped < ConfirmRearm) confirmed = false
        if (!confirmed && clamped >= 1f) {
            confirmed = true
            confirm()
            return true
        }
        return false
    }

    fun reset() {
        tickLevel = 0
        confirmed = false
    }

    private companion object {
        const val TickQuarter = 0.25f
        const val TickHysteresis = 0.08f

        /** Falling back below this re-arms the full-pull haptic. */
        const val ConfirmRearm = 0.85f
    }
}
