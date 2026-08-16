package tedwester.convo.core.network.model

import tedwester.convo.features.chat.model.WebSearchStep

/**
 * Incremental chunk from an OpenRouter SSE chat completion stream.
 *
 * Either [content], [reasoning], [webSearchSteps], or a combination may be
 * set for a given chunk.
 */
data class StreamDelta(
    val content: String? = null,
    val reasoning: String? = null,
    val webSearchSteps: List<WebSearchStep>? = null,
) {
    val isEmpty: Boolean
        get() = content.isNullOrEmpty() &&
            reasoning.isNullOrEmpty() &&
            webSearchSteps == null
}
