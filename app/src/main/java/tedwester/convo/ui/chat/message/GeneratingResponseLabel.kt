package tedwester.convo.ui.chat.message

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tedwester.convo.features.chat.model.WebSearchStep

@Composable
internal fun GeneratingResponseLabel(
    modifier: Modifier = Modifier,
    text: String = "Generating response…",
) {
    ShimmerStatusLabel(
        text = text,
        modifier = modifier.fillMaxWidth(),
    )
}

internal fun isWebSearchInProgress(webSearchSteps: List<WebSearchStep>): Boolean =
    webSearchSteps.any { step ->
        step.isSearching || (step.query.isNotBlank() && step.citations.isEmpty())
    }

internal fun streamingStatusLabel(
    statusLabel: String?,
    webSearchSteps: List<WebSearchStep>,
): String {
    if (isWebSearchInProgress(webSearchSteps)) {
        return "Searching…"
    }
    return statusLabel?.takeIf { it.isNotBlank() } ?: "Generating response…"
}
