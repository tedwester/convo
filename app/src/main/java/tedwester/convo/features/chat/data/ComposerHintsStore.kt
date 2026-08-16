package tedwester.convo.features.chat.data

import android.content.Context
import android.content.SharedPreferences

/**
 * One-shot first-run callouts on the composer. Shown after the API key is
 * saved on the entrance screen.
 */
class ComposerHintsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasSeen(): Boolean = prefs.getBoolean(KEY_SEEN, false)

    fun markSeen() {
        prefs.edit().putBoolean(KEY_SEEN, true).apply()
    }

    fun hasSeenModelHint(): Boolean = prefs.getBoolean(KEY_MODEL_HINT, false)

    fun markModelHintSeen() {
        prefs.edit().putBoolean(KEY_MODEL_HINT, true).apply()
    }

    private companion object {
        const val PREFS = "convo_composer_hints"
        const val KEY_SEEN = "seen_v2"
        const val KEY_MODEL_HINT = "model_hint_seen"
    }
}
