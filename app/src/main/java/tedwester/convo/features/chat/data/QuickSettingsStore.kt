package tedwester.convo.features.chat.data

import android.content.Context
import android.content.SharedPreferences
import tedwester.convo.features.chat.model.QuickSettingIds

/**
 * Persisted, ordered list of quick-setting IDs shown in the chat-search dock.
 *
 * The order of [items] is the left-to-right order of the dock buttons.
 */
data class QuickSettingsConfig(
    val items: List<String> = DEFAULT_ITEMS,
) {
    companion object {
        /** Defaults: lock and credits on the left, new chat in the center, keep-search on the right. */
        val DEFAULT_ITEMS: List<String> = listOf(
            QuickSettingIds.BIOMETRIC_LOCK,
            QuickSettingIds.CREDITS,
            QuickSettingIds.NEW_CHAT,
            QuickSettingIds.KEEP_SEARCH_ON,
        )

        /** Maximum number of quick-setting slots the dock can hold. */
        const val MAX_ITEMS = 5
    }
}

private val RetiredQuickSettingItems = setOf("web_search", "reasoning", "model")

/**
 * Persists [QuickSettingsConfig] via SharedPreferences, mirroring the other
 * small preference stores (`VoicePreferencesStore`, `SearchPreferencesStore`).
 */
class QuickSettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): QuickSettingsConfig {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return QuickSettingsConfig()
        val ids = raw.split(SEPARATOR)
            .filter { it.isNotBlank() && it !in RetiredQuickSettingItems }
        if (ids.isEmpty()) return QuickSettingsConfig()
        return QuickSettingsConfig(items = ids.distinct().take(QuickSettingsConfig.MAX_ITEMS))
    }

    fun save(value: QuickSettingsConfig) {
        val sanitized = value.items
            .filter { it.isNotBlank() && it !in RetiredQuickSettingItems }
            .distinct()
            .take(QuickSettingsConfig.MAX_ITEMS)
        prefs.edit()
            .putString(KEY_ITEMS, sanitized.joinToString(SEPARATOR))
            .apply()
    }

    private companion object {
        const val PREFS = "convo_quick_settings_prefs"
        const val KEY_ITEMS = "items"
        const val SEPARATOR = ","
    }
}
