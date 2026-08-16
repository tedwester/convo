package tedwester.convo.features.chat.data

import android.content.Context
import android.content.SharedPreferences

data class ComposerPreferences(
    /** When true, the mic dictation button appears in the chat composer. */
    val showDictationButton: Boolean = true,
    /** When true, a floating button appears to jump to the latest messages. */
    val showScrollToBottomButton: Boolean = true,
    /** When true, a floating button appears to jump to the start of the chat. */
    val showScrollToTopButton: Boolean = true,
)

class ComposerPreferencesStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): ComposerPreferences = ComposerPreferences(
        showDictationButton = prefs.getBoolean(KEY_SHOW_DICTATION, true),
        showScrollToBottomButton = prefs.getBoolean(KEY_SHOW_SCROLL_TO_BOTTOM, true),
        showScrollToTopButton = prefs.getBoolean(KEY_SHOW_SCROLL_TO_TOP, true),
    )

    fun save(value: ComposerPreferences) {
        prefs.edit()
            .putBoolean(KEY_SHOW_DICTATION, value.showDictationButton)
            .putBoolean(KEY_SHOW_SCROLL_TO_BOTTOM, value.showScrollToBottomButton)
            .putBoolean(KEY_SHOW_SCROLL_TO_TOP, value.showScrollToTopButton)
            .apply()
    }

    private companion object {
        const val PREFS = "convo_composer_prefs"
        const val KEY_SHOW_DICTATION = "show_dictation_button"
        const val KEY_SHOW_SCROLL_TO_BOTTOM = "show_scroll_to_bottom_button"
        const val KEY_SHOW_SCROLL_TO_TOP = "show_scroll_to_top_button"
    }
}
