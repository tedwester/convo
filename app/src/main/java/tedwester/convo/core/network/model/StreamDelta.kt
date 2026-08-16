package tedwester.convo.core.network.model

import tedwester.convo.features.chat.model.WebSearchStep

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
