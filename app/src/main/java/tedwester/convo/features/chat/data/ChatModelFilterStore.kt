package tedwester.convo.features.chat.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import tedwester.convo.ui.chat.modals.ModelAuthorBadge
import tedwester.convo.ui.chat.modals.ModelCategoryBadge
import tedwester.convo.ui.chat.modals.ModelFilterState
import tedwester.convo.ui.chat.modals.ModelInputFilter
import tedwester.convo.ui.chat.modals.ModelOutputFilter
import tedwester.convo.ui.chat.modals.ModelSelectorUiState
import tedwester.convo.ui.chat.modals.ModelSortBadge

class ChatModelFilterStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(chatId: String): ModelFilterState {
        val raw = prefs.getString(chatId, null) ?: return ModelFilterState()
        return runCatching { decode(raw) }.getOrDefault(ModelFilterState())
    }

    fun save(chatId: String, state: ModelFilterState) {
        prefs.edit()
            .putString(chatId, encode(state))
            .apply()
    }

    fun delete(chatId: String) {
        prefs.edit().remove(chatId).remove(uiKey(chatId)).apply()
    }

    fun loadUiState(chatId: String): ModelSelectorUiState {
        val raw = prefs.getString(uiKey(chatId), null) ?: return ModelSelectorUiState()
        return runCatching {
            val obj = JSONObject(raw)
            ModelSelectorUiState(
                filtersExpanded = obj.optBoolean(KEY_EXPANDED, true),
                query = obj.optString(KEY_QUERY),
            )
        }.getOrDefault(ModelSelectorUiState())
    }

    fun saveUiState(chatId: String, state: ModelSelectorUiState) {
        val raw = JSONObject().apply {
            put(KEY_EXPANDED, state.filtersExpanded)
            put(KEY_QUERY, state.query)
        }.toString()
        prefs.edit().putString(uiKey(chatId), raw).apply()
    }

    private fun uiKey(chatId: String): String = "$chatId.ui"

    private fun encode(state: ModelFilterState): String =
        JSONObject().apply {
            put(KEY_INPUT_FILTERS, state.inputFilters.joinToString(",") { it.name })
            put(KEY_OUTPUT_FILTERS, state.outputFilters.joinToString(",") { it.name })
            put(KEY_FREE, state.freeOnly)
            put(KEY_REASONING, state.reasoningOnly)
            put(KEY_SORTS, state.sorts.joinToString(",") { it.name })
            put(KEY_AUTHORS, state.authors.joinToString(",") { it.name })
            put(KEY_CATEGORY, state.category?.name)
        }.toString()

    private fun decode(raw: String): ModelFilterState {
        val obj = JSONObject(raw)
        return ModelFilterState(
            inputFilters = parseEnumSet<ModelInputFilter>(obj.optString(KEY_INPUT_FILTERS)),
            outputFilters = parseEnumSet<ModelOutputFilter>(obj.optString(KEY_OUTPUT_FILTERS)),
            freeOnly = obj.optBoolean(KEY_FREE, false),
            reasoningOnly = obj.optBoolean(KEY_REASONING, false),
            sorts = parseEnumSet<ModelSortBadge>(obj.optString(KEY_SORTS)),
            authors = parseEnumSet<ModelAuthorBadge>(obj.optString(KEY_AUTHORS)),
            category = obj.optString(KEY_CATEGORY)
                .takeIf { it.isNotBlank() }
                ?.let { enumOrNull<ModelCategoryBadge>(it) },
        )
    }

    private inline fun <reified T : Enum<T>> parseEnumSet(raw: String?): Set<T> {
        if (raw.isNullOrBlank()) return emptySet()
        return raw.split(',')
            .mapNotNull { token -> runCatching { enumValueOf<T>(token.trim()) }.getOrNull() }
            .toSet()
    }

    private inline fun <reified T : Enum<T>> enumOrNull(raw: String): T? =
        runCatching { enumValueOf<T>(raw) }.getOrNull()

    private companion object {
        const val PREFS = "convo_chat_model_filter_prefs"
        const val KEY_INPUT_FILTERS = "input_filters"
        const val KEY_OUTPUT_FILTERS = "output_filters"
        const val KEY_SORTS = "sorts"
        const val KEY_AUTHORS = "authors"
        const val KEY_FREE = "free"
        const val KEY_REASONING = "reasoning"
        const val KEY_CATEGORY = "category"
        const val KEY_EXPANDED = "expanded"
        const val KEY_QUERY = "query"
    }
}
