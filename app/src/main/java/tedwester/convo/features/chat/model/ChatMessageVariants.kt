package tedwester.convo.features.chat.model

internal fun ChatMessage.ensureVariantContinuations(variantCount: Int): List<List<ChatMessage>> {
    if (variantCount <= 0) return emptyList()
    val current = variantContinuations
    if (current.size >= variantCount) return current
    return current + List(variantCount - current.size) { emptyList() }
}

internal fun <T> List<T>.alignedWithVariantCount(
    variantCount: Int,
    fallback: (index: Int) -> T,
): List<T> {
    if (size == variantCount) return this
    return List(variantCount) { i -> getOrNull(i) ?: fallback(i) }
}

internal fun List<String>.appendAlignedReasoning(
    priorSize: Int,
    append: Boolean,
    newReasoning: String,
): List<String> =
    if (append && priorSize > 0) {
        alignedWithVariantCount(priorSize) { "" } + newReasoning
    } else {
        listOf(newReasoning)
    }

internal fun List<Long?>.appendAlignedDurations(
    priorSize: Int,
    append: Boolean,
    newDuration: Long?,
): List<Long?> =
    if (append && priorSize > 0) {
        alignedWithVariantCount(priorSize) { getOrNull(it) } + newDuration
    } else {
        listOf(newDuration)
    }

internal fun List<List<WebSearchStep>>.appendAlignedSearch(
    priorSize: Int,
    append: Boolean,
    newSteps: List<WebSearchStep>,
): List<List<WebSearchStep>> =
    if (append && priorSize > 0) {
        alignedWithVariantCount(priorSize) { emptyList() } + listOf(newSteps)
    } else {
        listOf(newSteps)
    }

internal fun ChatMessage.withActiveVariantFields(index: Int): ChatMessage {
    val saved = savedVariants()
    val clamped = clampVariantIndex(index)
    val reasoningForVariant = reasoningVariants.getOrNull(clamped)
        ?: reasoning.takeIf { clamped == variantIndex }
        ?: ""
    val thoughtDurationForVariant = thoughtDurationVariants.getOrNull(clamped)
        ?: thoughtDurationMs.takeIf { clamped == variantIndex }
    val webSearchForVariant = webSearchStepVariants.getOrNull(clamped)
        ?: webSearchSteps.takeIf { clamped == variantIndex }
        ?: emptyList()
    val attachmentsForVariant = savedAttachmentVariants().getOrNull(clamped)
        ?: attachments.takeIf { clamped == variantIndex }
        ?: emptyList()
    return copy(
        content = saved.getOrNull(clamped) ?: content,
        attachments = attachmentsForVariant,
        reasoning = reasoningForVariant,
        thoughtDurationMs = thoughtDurationForVariant,
        webSearchSteps = webSearchForVariant,
        variantIndex = clamped,
        variants = variants.ifEmpty { saved },
        attachmentVariants = attachmentVariants.ifEmpty { savedAttachmentVariants() },
    )
}
