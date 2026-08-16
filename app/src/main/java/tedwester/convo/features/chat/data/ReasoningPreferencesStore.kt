package tedwester.convo.features.chat.data

import android.content.Context
import android.content.SharedPreferences
import tedwester.convo.features.chat.model.ReasoningEffort
import tedwester.convo.features.chat.model.ReasoningPreferences

/**
 * Persists [ReasoningPreferences] per OpenRouter model id.
 */
class ReasoningPreferencesStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(modelId: String): ReasoningPreferences {
        if (modelId.isBlank()) return ReasoningPreferences.Default
        val prefix = keyPrefix(modelId)
        if (!prefs.contains("${prefix}_enabled")) return ReasoningPreferences.Default
        return ReasoningPreferences(
            enabled = prefs.getBoolean("${prefix}_enabled", true),
            effort = ReasoningEffort.fromApiValue(
                prefs.getString("${prefix}_effort", ReasoningEffort.Medium.apiValue),
            ),
            streamThinking = prefs.getBoolean("${prefix}_stream", true),
        )
    }

    fun save(modelId: String, prefsValue: ReasoningPreferences) {
        if (modelId.isBlank()) return
        val prefix = keyPrefix(modelId)
        prefs.edit()
            .putBoolean("${prefix}_enabled", prefsValue.enabled)
            .putString("${prefix}_effort", prefsValue.effort.apiValue)
            .putBoolean("${prefix}_stream", prefsValue.streamThinking)
            .apply()
    }

    private fun keyPrefix(modelId: String): String = "model_$modelId"

    private companion object {
        const val PREFS = "convo_reasoning_prefs"
    }
}
