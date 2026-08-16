package tedwester.convo.core.network.model

import org.json.JSONObject
import tedwester.convo.features.chat.model.ReasoningEffort
import tedwester.convo.features.chat.model.ReasoningPreferences

data class ReasoningRequest(
    val effort: String,
    val exclude: Boolean,
) {
    fun toJson(): JSONObject =
        JSONObject().apply {
            put("effort", effort)
            put("exclude", exclude)
        }

    companion object {

        fun from(model: OpenRouterModel, prefs: ReasoningPreferences): ReasoningRequest? {
            if (!model.supportsReasoning) return null
            val enabled = prefs.enabled || model.requiresMandatoryReasoning
            if (!enabled) {
                return ReasoningRequest(effort = "none", exclude = true)
            }
            val levels = model.supportedEffortLevels()
            val effort = prefs.effort
                .takeIf { it in levels }
                ?: levels.firstOrNull { it == ReasoningEffort.Medium }
                ?: levels.firstOrNull()
                ?: ReasoningEffort.Medium
            return ReasoningRequest(
                effort = effort.apiValue,
                exclude = !prefs.streamThinking,
            )
        }
    }
}
