package tedwester.convo.core.network.model

/**
 * Query parameters for OpenRouter `GET /api/v1/models`.
 *
 * Prefer these server-side filters/sorts over fetching everything and filtering
 * client-side — OpenRouter documents category, author, price, and sort as API
 * params (throughput / intelligence scores aren't on the full list payload).
 *
 * Note: OpenRouter's `output_modalities` parameter **defaults to `text`**, which
 * silently excludes TTS (speech/audio), image-generation, and video-generation
 * models from the response. We override it with `all` by default so the picker
 * always shows the full usable catalog; client-side filters then narrow it down.
 * Embedding and rerank models are dropped after fetch — they aren't chat-capable.
 */
data class ModelListQuery(
    /**
     * Comma-separated output modalities, or `all` to skip modality filtering.
     * Defaults to `all` so TTS / image / video models are included.
     */
    val outputModalities: String = "all",
    /** Use-case category, e.g. `programming`, `roleplay`, `science`. */
    val category: String? = null,
    /**
     * Server sort: `most-popular`, `newest`, `pricing-low-to-high`,
     * `throughput-high-to-low`, `latency-low-to-high`, `intelligence-high-to-low`, …
     */
    val sort: String? = null,
    /** Maximum prompt price in $/M tokens. Use `0` for free-only. */
    val maxPrice: Double? = null,
    /** Minimum prompt price in $/M tokens. Used to exclude free models when sorting by price. */
    val minPrice: Double? = null,
    /** Comma-separated author slugs, e.g. `openai,anthropic`. */
    val authors: String? = null,
    /** Comma-separated input modalities, e.g. `image`. */
    val inputModalities: String? = null,
) {
    val cacheKey: String
        get() = listOf(
            outputModalities,
            category.orEmpty(),
            sort.orEmpty(),
            maxPrice?.toString().orEmpty(),
            minPrice?.toString().orEmpty(),
            authors.orEmpty(),
            inputModalities.orEmpty(),
        ).joinToString("|")

    fun toUrlSuffix(): String {
        val parts = buildList {
            add("output_modalities=${outputModalities.encodeQuery()}")
            category?.let { add("category=${it.encodeQuery()}") }
            sort?.let { add("sort=${it.encodeQuery()}") }
            maxPrice?.let { add("max_price=$it") }
            minPrice?.let { add("min_price=$it") }
            authors?.let { add("model_authors=${it.encodeQuery()}") }
            inputModalities?.let { add("input_modalities=${it.encodeQuery()}") }
        }
        return "?" + parts.joinToString("&")
    }

    private fun String.encodeQuery(): String =
        java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
}
