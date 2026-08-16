package tedwester.convo.features.chat.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists the selected TTS voice id per OpenRouter model.
 *
 * A missing entry means "use the model default" ([OpenRouterModel.defaultVoiceId]).
 */
class TtsVoicePreferencesStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(modelId: String): String? {
        if (modelId.isBlank()) return null
        return prefs.getString(key(modelId), null)?.takeIf { it.isNotBlank() }
    }

    fun save(modelId: String, voiceId: String?) {
        if (modelId.isBlank()) return
        val editor = prefs.edit()
        val key = key(modelId)
        if (voiceId.isNullOrBlank()) {
            editor.remove(key)
        } else {
            editor.putString(key, voiceId)
        }
        editor.apply()
    }

    private fun key(modelId: String): String = "model_$modelId"

    private companion object {
        const val PREFS = "convo_tts_voice_prefs"
    }
}
