package tedwester.convo.features.chat.data

import android.content.Context
import android.content.SharedPreferences

data class ApiPreferences(
    /** How long to wait for an AI response before terminating the request (1–45 minutes). */
    val requestTimeoutMinutes: Int = DEFAULT_REQUEST_TIMEOUT_MINUTES,
    /** Upper bound on completion tokens sent to OpenRouter. Higher values may increase cost. */
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
) {
    companion object {
        const val DEFAULT_REQUEST_TIMEOUT_MINUTES = 5
        const val DEFAULT_MAX_TOKENS = 12_000
        const val MIN_REQUEST_TIMEOUT_MINUTES = 1
        const val MAX_REQUEST_TIMEOUT_MINUTES = 45
    }
}

class ApiPreferencesStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): ApiPreferences = ApiPreferences(
        requestTimeoutMinutes = prefs.getInt(
            KEY_REQUEST_TIMEOUT_MINUTES,
            ApiPreferences.DEFAULT_REQUEST_TIMEOUT_MINUTES,
        ).coerceIn(
            ApiPreferences.MIN_REQUEST_TIMEOUT_MINUTES,
            ApiPreferences.MAX_REQUEST_TIMEOUT_MINUTES,
        ),
        maxTokens = prefs.getInt(KEY_MAX_TOKENS, ApiPreferences.DEFAULT_MAX_TOKENS)
            .coerceAtLeast(1),
    )

    fun save(value: ApiPreferences) {
        prefs.edit()
            .putInt(
                KEY_REQUEST_TIMEOUT_MINUTES,
                value.requestTimeoutMinutes.coerceIn(
                    ApiPreferences.MIN_REQUEST_TIMEOUT_MINUTES,
                    ApiPreferences.MAX_REQUEST_TIMEOUT_MINUTES,
                ),
            )
            .putInt(KEY_MAX_TOKENS, value.maxTokens.coerceAtLeast(1))
            .apply()
    }

    private companion object {
        const val PREFS = "convo_api_prefs"
        const val KEY_REQUEST_TIMEOUT_MINUTES = "request_timeout_minutes"
        const val KEY_MAX_TOKENS = "max_tokens"
    }
}
