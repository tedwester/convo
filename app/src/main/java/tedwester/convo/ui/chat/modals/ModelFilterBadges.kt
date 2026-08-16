package tedwester.convo.ui.chat.modals

import tedwester.convo.core.network.model.ModelListQuery
import tedwester.convo.core.network.model.OpenRouterModel

enum class ModelInputFilter(val label: String, val apiValue: String) {
    Vision("Vision", "image"),
    Audio("Audio", "audio"),
    File("File", "file"),
}

enum class ModelOutputFilter(val label: String, val apiValues: List<String>) {
    Chat("Chat", listOf("text")),
    Voice("Voice", listOf("speech", "audio")),
    Image("Image", listOf("image")),
    Video("Video", listOf("video")),
    Transcribe("Transcribe", listOf("transcription")),
}

enum class ModelSortBadge(val label: String, val apiValue: String) {
    Popular("Popular", "most-popular"),
    Cheapest("Cheapest", "pricing-low-to-high"),
    Fastest("Fastest", "throughput-high-to-low"),
    LowestLatency("Snappy", "latency-low-to-high"),
    Smartest("Smartest", "intelligence-high-to-low"),
    Newest("Newest", "newest"),
    ;
}

data class ModelFilterState(
    val inputFilters: Set<ModelInputFilter> = emptySet(),
    val outputFilters: Set<ModelOutputFilter> = emptySet(),
    val freeOnly: Boolean = false,
    val reasoningOnly: Boolean = false,
    val sorts: Set<ModelSortBadge> = emptySet(),
    val authors: Set<ModelAuthorBadge> = emptySet(),
    val category: ModelCategoryBadge? = null,
) {
    val hasActiveFilters: Boolean
        get() = inputFilters.isNotEmpty() || outputFilters.isNotEmpty() || freeOnly ||
            reasoningOnly || sorts.isNotEmpty() || authors.isNotEmpty() || category != null

    val primarySort: ModelSortBadge?
        get() = sorts.minByOrNull { it.ordinal }

    fun toQuery(): ModelListQuery = ModelListQuery(
        outputModalities = outputFilters.toOutputModalitiesParam(),
        category = category?.apiValue,
        sort = primarySort?.apiValue,
        maxPrice = if (freeOnly) 0.0 else null,
        minPrice = if (!freeOnly && ModelSortBadge.Cheapest in sorts) CHEAPEST_MIN_PRICE else null,
        authors = authors.toAuthorParam(),
        inputModalities = inputFilters.toInputModalitiesParam(),
    )

    fun matchesCapabilities(model: OpenRouterModel): Boolean {
        if (inputFilters.isNotEmpty()) {
            inputFilters.forEach { filter ->
                if (!model.matchesInputFilter(filter)) return false
            }
        }
        if (outputFilters.isNotEmpty()) {
            if (!outputFilters.any { model.matchesOutputFilter(it) }) return false
        }
        return true
    }

    fun matchesPriceTier(model: OpenRouterModel): Boolean = when {
        freeOnly -> true
        ModelSortBadge.Cheapest in sorts -> !model.isFree
        else -> true
    }

    fun applySorts(models: List<OpenRouterModel>): List<OpenRouterModel> {
        if (sorts.size <= 1) return models
        val primary = primarySort ?: return models
        if (primary.comparator == null) return models
        val ordered = sorts.sortedByDescending { it.ordinal }
        var result = models
        ordered.forEach { sort ->
            val cmp = sort.comparator ?: return@forEach
            result = result.sortedWith(cmp)
        }
        return result
    }

    fun toggleInput(filter: ModelInputFilter): ModelFilterState = copy(
        inputFilters = inputFilters.toggle(filter),
    )

    fun toggleOutput(filter: ModelOutputFilter): ModelFilterState = copy(
        outputFilters = outputFilters.toggle(filter),
    )

    fun toggleSort(sort: ModelSortBadge): ModelFilterState = copy(
        sorts = sorts.toggle(sort),
    )

    fun toggleAuthor(author: ModelAuthorBadge): ModelFilterState = copy(
        authors = authors.toggle(author),
    )
}

private fun OpenRouterModel.matchesInputFilter(filter: ModelInputFilter): Boolean = when (filter) {
    ModelInputFilter.Vision -> hasImageInput
    ModelInputFilter.Audio -> hasAudioInput
    ModelInputFilter.File -> hasFileInput
}

private fun OpenRouterModel.matchesOutputFilter(filter: ModelOutputFilter): Boolean = when (filter) {
    ModelOutputFilter.Chat -> hasTextOutput
    ModelOutputFilter.Voice -> supportsSpeechOutput
    ModelOutputFilter.Image -> supportsImageOutput
    ModelOutputFilter.Video -> supportsVideoOutput
    ModelOutputFilter.Transcribe -> hasTranscriptionOutput
}

private fun Set<ModelInputFilter>.toInputModalitiesParam(): String? =
    if (isEmpty()) null else joinToString(",") { it.apiValue }

private fun Set<ModelOutputFilter>.toOutputModalitiesParam(): String {
    if (isEmpty()) return "all"
    return flatMap { it.apiValues }.distinct().joinToString(",")
}

private fun Set<ModelAuthorBadge>.toAuthorParam(): String? =
    if (isEmpty()) null else joinToString(",") { it.slug }

private fun <T> Set<T>.toggle(value: T): Set<T> =
    if (contains(value)) this - value else this + value

private const val CHEAPEST_MIN_PRICE = 1e-9

private val OpenRouterModel.averagePricePerMillion: Double?
    get() {
        val prompt = pricing?.prompt?.toDoubleOrNull()?.times(1_000_000.0)
        val completion = pricing?.completion?.toDoubleOrNull()?.times(1_000_000.0)
        return when {
            prompt != null && completion != null -> (prompt + completion) / 2.0
            prompt != null -> prompt
            completion != null -> completion
            else -> null
        }
    }

private fun <T : Comparable<T>> nullsLastComparator(
    selector: (OpenRouterModel) -> T?,
    ascending: Boolean,
): Comparator<OpenRouterModel> = Comparator { a, b ->
    val va = selector(a)
    val vb = selector(b)
    if (va == null && vb == null) 0
    else if (va == null) 1
    else if (vb == null) -1
    else {
        val cmp = va.compareTo(vb)
        if (ascending) cmp else -cmp
    }
}

private val ModelSortBadge.comparator: Comparator<OpenRouterModel>?
    get() = when (this) {
        ModelSortBadge.Cheapest -> nullsLastComparator(
            { it.averagePricePerMillion },
            ascending = true,
        )
        ModelSortBadge.Newest -> nullsLastComparator(
            { it.created },
            ascending = false,
        )
        ModelSortBadge.Fastest,
        ModelSortBadge.LowestLatency,
        ModelSortBadge.Popular,
        ModelSortBadge.Smartest -> null
    }

enum class ModelAuthorBadge(val label: String, val slug: String) {
    OpenAI("OpenAI", "openai"),
    Anthropic("Anthropic", "anthropic"),
    Google("Google", "google"),
    Meta("Meta", "meta-llama"),
    DeepSeek("DeepSeek", "deepseek"),
    Qwen("Qwen", "qwen"),
    Mistral("Mistral", "mistralai"),
    XAI("xAI", "x-ai"),
    Cohere("Cohere", "cohere"),
    NVIDIA("NVIDIA", "nvidia"),
}

enum class ModelCategoryBadge(val label: String, val apiValue: String) {
    Programming("Coding", "programming"),
    Roleplay("Roleplay", "roleplay"),
    Marketing("Marketing", "marketing"),
    MarketingSeo("SEO", "marketing/seo"),
    Technology("Tech", "technology"),
    Science("Science", "science"),
    Translation("Translate", "translation"),
    Legal("Legal", "legal"),
    Finance("Finance", "finance"),
    Health("Health", "health"),
    Trivia("Trivia", "trivia"),
    Academia("Academia", "academia"),
}
