package tedwester.convo.features.chat.model

/**
 * A single web page cited from an OpenRouter [WebSearchStep].
 */
data class WebSearchCitation(
    val url: String,
    val title: String,
    val description: String = "",
    val publishedDate: String = "",
)

/**
 * One web search the model invoked during a turn (query + resulting sources).
 */
data class WebSearchStep(
    val id: String,
    val query: String = "",
    val citations: List<WebSearchCitation> = emptyList(),
    /** True while OpenRouter is still executing this search. */
    val isSearching: Boolean = false,
)

internal fun domainFromUrl(url: String): String =
    runCatching {
        android.net.Uri.parse(url).host?.removePrefix("www.").orEmpty()
    }.getOrDefault("")
        .ifBlank { url }

/** All unique citations across search steps (stable order). */
internal fun List<WebSearchStep>.allCitations(): List<WebSearchCitation> {
    val seen = mutableSetOf<String>()
    return buildList {
        for (step in this@allCitations) {
            for (citation in step.citations) {
                if (seen.add(citation.url)) add(citation)
            }
        }
    }
}

/** Favicon URL for a citation page (used in the search timeline). */
internal fun faviconUrlFor(url: String): String {
    val domain = domainFromUrl(url)
    if (domain.isBlank()) return url
    return "https://www.google.com/s2/favicons?domain=$domain&sz=64"
}
