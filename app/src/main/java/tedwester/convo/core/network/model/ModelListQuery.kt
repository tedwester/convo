package tedwester.convo.core.network.model

data class ModelListQuery(
    val outputModalities: String = "all",
    val category: String? = null,
    val sort: String? = null,
    val maxPrice: Double? = null,
    val minPrice: Double? = null,
    val authors: String? = null,
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
