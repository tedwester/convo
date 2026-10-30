package tedwester.convo.features.chat.model

internal fun ChatMessage.ensureVariantContinuations(variantCount: Int): List<List<ChatMessage>> {
    if (variantCount <= 0) return emptyList()
    val current = variantContinuations
    if (current.size >= variantCount) return current
    return current + List(variantCount - current.size) { emptyList() }
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
