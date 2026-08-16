package tedwester.convo.features.chat.model

data class WebSearchCitation(
    val url: String,
    val title: String,
    val description: String = "",
    val publishedDate: String = "",
)

data class WebSearchStep(
    val id: String,
    val query: String = "",
    val citations: List<WebSearchCitation> = emptyList(),

    val isSearching: Boolean = false,
)

internal fun domainFromUrl(url: String): String =
    runCatching {
        android.net.Uri.parse(url).host?.removePrefix("www.").orEmpty()
    }.getOrDefault("")
        .ifBlank { url }

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

internal fun faviconUrlFor(url: String): String {
    val domain = domainFromUrl(url)
    if (domain.isBlank()) return url
    return "https://www.google.com/s2/favicons?domain=$domain&sz=64"
}
