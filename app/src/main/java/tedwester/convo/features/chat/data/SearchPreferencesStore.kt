package tedwester.convo.features.chat.data

import android.content.Context
import android.content.SharedPreferences

data class SearchPreferences(
    /** When true, the composer search toggle stays on after sending a message. */
    val persistAfterPrompt: Boolean = true,
)

class SearchPreferencesStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): SearchPreferences = SearchPreferences(
        persistAfterPrompt = prefs.getBoolean(KEY_PERSIST, true),
    )

    fun save(value: SearchPreferences) {
        prefs.edit()
            .putBoolean(KEY_PERSIST, value.persistAfterPrompt)
            .apply()
    }

    private companion object {
        const val PREFS = "convo_search_prefs"
        const val KEY_PERSIST = "persist_after_prompt"
    }
}
