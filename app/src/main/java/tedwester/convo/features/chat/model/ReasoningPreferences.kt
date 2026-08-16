package tedwester.convo.features.chat.model

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

data class ReasoningPreferences(
    val enabled: Boolean = true,
    val effort: ReasoningEffort = ReasoningEffort.Medium,

    val streamThinking: Boolean = true,
) {
    companion object {
        val Default = ReasoningPreferences()
    }
}
