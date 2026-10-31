package tedwester.convo.features.chat.data

import android.content.Context
import android.content.SharedPreferences
import tedwester.convo.features.chat.model.QuickSettingIds

data class QuickSettingsConfig(
    val items: List<String> = DEFAULT_ITEMS,
) {
    companion object {

        val DEFAULT_ITEMS: List<String> = listOf(
            QuickSettingIds.BIOMETRIC_LOCK,
            QuickSettingIds.CREDITS,
            QuickSettingIds.NEW_CHAT,
            QuickSettingIds.KEEP_SEARCH_ON,
            QuickSettingIds.VOICE_MODE,
        )

        const val MAX_ITEMS = 5
    }
}

private val RetiredQuickSettingItems = setOf("web_search", "reasoning", "model")

class QuickSettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): QuickSettingsConfig {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return QuickSettingsConfig()
        val ids = raw.split(SEPARATOR)
            .filter { it.isNotBlank() && it !in RetiredQuickSettingItems }
        if (ids.isEmpty()) return QuickSettingsConfig()
        val items = ids.distinct().take(QuickSettingsConfig.MAX_ITEMS).toMutableList()
        // Voice mode joined the defaults late. Existing installs pick it up once
        // when there is room; the stamp keeps a deliberate removal removed.
        if (prefs.getInt(KEY_DEFAULTS_VERSION, 0) < DEFAULTS_WITH_VOICE_MODE) {
            if (QuickSettingIds.VOICE_MODE !in items &&
                items.size < QuickSettingsConfig.MAX_ITEMS
            ) {
                items.add(QuickSettingIds.VOICE_MODE)
            }
            prefs.edit().putInt(KEY_DEFAULTS_VERSION, DEFAULTS_WITH_VOICE_MODE).apply()
        }
        return QuickSettingsConfig(items = items)
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
        const val KEY_DEFAULTS_VERSION = "defaults_version"
        const val DEFAULTS_WITH_VOICE_MODE = 1
    }
}
