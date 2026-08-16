package tedwester.convo.core.security

/**
 * A summary of the user's selected model, persisted so it survives restarts.
 *
 * Only the id and friendly name are stored; the full model descriptor is
 * re-fetched from OpenRouter when needed.
 */
data class StoredModel(
    val id: String,
    val name: String,
)
