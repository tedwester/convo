package tedwester.convo.core.network

/**
 * Represents a failure while talking to the OpenRouter API.
 *
 * @param message a human readable description of what went wrong.
 * @param code    an optional HTTP status code when the failure came from the network.
 * @param cause   the underlying exception, when present.
 */
class OpenRouterApiException(
    message: String,
    val code: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
