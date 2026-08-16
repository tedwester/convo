package tedwester.convo.core.network

class OpenRouterApiException(
    message: String,
    val code: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
