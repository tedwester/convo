package tedwester.convo.features.chat.model

/**
 * Reasoning effort levels supported by OpenRouter's unified `reasoning.effort`
 * parameter.
 */
enum class ReasoningEffort(val apiValue: String, val label: String) {
    Minimal("minimal", "Minimal"),
    Low("low", "Low"),
    Medium("medium", "Medium"),
    High("high", "High"),
    XHigh("xhigh", "X-High"),
    Max("max", "Max"),
    ;

    companion object {
        fun fromApiValue(value: String?): ReasoningEffort =
            entries.firstOrNull { it.apiValue.equals(value, ignoreCase = true) } ?: Medium
    }
}

/**
 * Per-model user preferences for reasoning behaviour.
 */
data class ReasoningPreferences(
    val enabled: Boolean = true,
    val effort: ReasoningEffort = ReasoningEffort.Medium,
    /** When true, reasoning tokens are returned in the stream (`exclude: false`). */
    val streamThinking: Boolean = true,
) {
    companion object {
        val Default = ReasoningPreferences()
    }
}
