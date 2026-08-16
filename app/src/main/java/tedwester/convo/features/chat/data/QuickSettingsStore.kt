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
