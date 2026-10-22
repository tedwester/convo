package tedwester.convo.ui.chat

/**
 * Drives the haptics that accompany a pull gesture.
 *
 * A light haptic is fired as the pull passes each quarter, and a firmer one when it reaches
 * the end. The state is resettable so each gesture starts fresh.
 */
class PullHaptics {
    private var tickLevel = 0
    private var armed = false

    /**
     * Reports a new progress value and fires the matching haptics.
     *
     * @return true the first time the pull reaches full, so the caller can fire its action.
     */
    fun onProgress(
        progress: Float,
        tick: () -> Unit,
        confirm: () -> Unit,
    ): Boolean {
        val clamped = progress.coerceIn(0f, 1f)
        while (tickLevel < 3 && clamped >= (tickLevel + 1) * 0.25f) {
            tickLevel += 1
            tick()
        }
        if (!armed && clamped >= 1f) {
            armed = true
            confirm()
            return true
        }
        return false
    }

    fun reset() {
        tickLevel = 0
        armed = false
    }
}
