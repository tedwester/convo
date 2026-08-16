package tedwester.convo.core.network

/**
 * Lightweight format checks for OpenRouter API keys before hitting the network.
 * Server verification via [OpenRouterApi.fetchModels] remains the source of truth.
 */
object OpenRouterApiKeyValidation {

    private const val PREFIX = "sk-or-v1-"
    private const val MIN_LENGTH = 24

    fun formatError(key: String): String? {
        val trimmed = key.trim()
        return when {
            trimmed.isBlank() -> "Please enter an API key."
            !trimmed.startsWith(PREFIX) -> "OpenRouter keys start with sk-or-v1-."
            trimmed.length < MIN_LENGTH -> "That key looks too short."
            trimmed.any { it.isWhitespace() } -> "Remove spaces from the key."
            else -> null
        }
    }

    fun isPlausibleFormat(key: String): Boolean = formatError(key) == null
}
