package tedwester.convo.core.image

object AssistantImages {

    private val MarkdownImage =
        Regex("""!\[[^\]]*]\(\s*<?((?:[^()\s]|\([^()\s]*\))+)>?(?:\s+["'][^"']*["'])?\s*\)""")

    private val ImageExtension = Regex(
        """\.(png|jpe?g|webp|gif|bmp|avif|heic|heif|svg)(\?.*)?$""",
        RegexOption.IGNORE_CASE,
    )

    private const val MaxDataUriChars = 20_000_000

    fun normalizeImageUrl(raw: String?): String? {
        var url = raw?.trim().orEmpty()
        if (url.isBlank()) return null
        if (url.startsWith("<") && url.endsWith(">") && url.length > 2) {
            url = url.substring(1, url.length - 1).trim()
        }
        if (url.isBlank()) return null

        if (url.startsWith("data:", ignoreCase = true)) {
            val cleaned = url.replace(Regex("\\s+"), "")
            if (cleaned.length > MaxDataUriChars) return null
            val lower = cleaned.lowercase()
            if (!lower.startsWith("data:image/")) return null
            val comma = cleaned.indexOf(',')
            if (comma <= 11) return null
            if (comma >= cleaned.length - 1) return null
            return cleaned
        }

        val lower = url.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null
        var cleaned = url.replace(Regex("[\\r\\n\\t]+"), "").replace(" ", "%20")
        if (cleaned.startsWith("http://", ignoreCase = true)) {
            cleaned = "https://" + cleaned.substring("http://".length)
        }
        cleaned = cleaned.replace("(", "%28").replace(")", "%29")
            .trimEnd('.', ',', ';', ':', '!', '?')
        if (cleaned.isBlank() || cleaned.contains('"')) return null
        val parsed = runCatching { java.net.URI(cleaned) }.getOrNull() ?: return null
        if (parsed.host.isNullOrBlank()) return null
        return cleaned
    }

    fun isRenderableImageUrl(url: String?): Boolean = normalizeImageUrl(url) != null

    fun encodeImageMarkdown(rawUrl: String?): String? {
        val normalized = normalizeImageUrl(rawUrl) ?: return null
        return "![]($normalized)"
    }

    fun decodeDataUri(dataUri: String): Pair<ByteArray, String>? {
        val comma = dataUri.indexOf(',')
        if (comma <= 5) return null
        val meta = dataUri.substring(5, comma)
        if (!meta.startsWith("image/", ignoreCase = true)) return null
        val payload = dataUri.substring(comma + 1).replace(Regex("\\s+"), "")
        if (payload.isEmpty()) return null
        val bytes = if (meta.contains(";base64", ignoreCase = true)) {
            runCatching {
                android.util.Base64.decode(payload, android.util.Base64.DEFAULT)
            }.getOrNull()
        } else {
            runCatching {
                java.net.URLDecoder.decode(payload, "UTF-8").toByteArray()
            }.getOrNull()
        }?.takeIf { it.isNotEmpty() } ?: return null
        return bytes to meta.substringBefore(';').ifBlank { "image/png" }
    }

    fun extractMarkdownImageUrls(markdown: String): List<String> =
        MarkdownImage.findAll(markdown).mapNotNull { normalizeImageUrl(it.groupValues[2]) }.toList()

    fun stripDuplicateMarkdownImages(chunk: String, seenUrls: MutableSet<String>): String {
        if (!chunk.contains("![")) return chunk
        var result = chunk
        for (match in MarkdownImage.findAll(chunk).toList().asReversed()) {
            val normalized = normalizeImageUrl(match.groupValues[2])
            if (normalized == null) {
                // Unrenderable — drop the block so it never reserves a blank slot.
                result = result.removeRange(match.range)
                continue
            }
            if (!seenUrls.add(normalized)) {
                result = result.removeRange(match.range)
            } else {
                val fixed = "![" + match.groupValues[1] + "]($normalized)"
                if (match.value != fixed) {
                    result = result.replaceRange(match.range, fixed)
                }
            }
        }
        val collapsed = result.replace(Regex("\n{3,}"), "\n\n")
        if (collapsed.isBlank()) return ""
        // Drop trailing blank lines left by a removed image, keep leading.
        return collapsed.trimEnd()
    }

    fun sanitizeForStreaming(text: String): String {
        val bang = text.lastIndexOf("![")
        if (bang < 0) {
            return text
        }
        val tail = text.substring(bang)
        val complete = MarkdownImage.find(tail)
        if (complete != null && tail.substring(complete.range.last + 1).trim().isNotEmpty()) {
            val after = tail.substring(complete.range.last + 1)
            val reopen = after.lastIndexOf("![")
            if (reopen < 0) return text
            val tail2 = after.substring(reopen)
            if (MarkdownImage.find(tail2)?.let { tail2.substring(it.range.last + 1).isBlank() } == true) {
                return text
            }
            return text.substring(0, bang + complete.range.last + 1 + reopen).trimEnd()
        }
        if (complete != null) return text
        return text.substring(0, bang).trimEnd()
    }

    fun isBareImageUrl(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || t.contains(' ') || t.contains('\n')) return false
        if (t.startsWith("data:image/", ignoreCase = true)) return isRenderableImageUrl(t)
        if (!t.startsWith("http://", ignoreCase = true) && !t.startsWith("https://", ignoreCase = true)) {
            return false
        }
        return ImageExtension.containsMatchIn(t.substringBefore('?')) ||
            t.contains("image", ignoreCase = true) && t.length < 2048
    }

    sealed interface MarkdownSegment {
        data class Text(val text: String) : MarkdownSegment
        data class Image(val url: String, val alt: String = "") : MarkdownSegment
    }

    fun splitMarkdownImages(markdown: String): List<MarkdownSegment> {
        val out = ArrayList<MarkdownSegment>()
        var textRun = StringBuilder()
        fun flushText() {
            val text = textRun.toString()
            if (text.isNotBlank()) out.add(MarkdownSegment.Text(text))
            textRun = StringBuilder()
        }

        var inFence = false
        markdown.split('\n').forEachIndexed { index, line ->
            if (index > 0) textRun.append('\n')
            val trimmed = line.trimStart()
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                inFence = !inFence
                textRun.append(line)
                return@forEachIndexed
            }
            if (inFence) {
                textRun.append(line)
                return@forEachIndexed
            }
            val parts = line.split('`')
            parts.forEachIndexed { partIndex, part ->
                if (partIndex % 2 == 0) {
                    var pos = 0
                    for (match in MarkdownImage.findAll(part)) {
                        if (match.range.first > pos) {
                            textRun.append(part, pos, match.range.first)
                        }
                        val normalized = normalizeImageUrl(match.groupValues[2])
                        if (normalized != null) {
                            flushText()
                            out.add(MarkdownSegment.Image(normalized, match.groupValues[1].trim()))
                        } else {
                            textRun.append('`').append(match.value).append('`')
                        }
                        pos = match.range.last + 1
                    }
                    if (pos < part.length) textRun.append(part, pos, part.length)
                } else {
                    textRun.append(part)
                }
                if (partIndex < parts.lastIndex) textRun.append('`')
            }
        }
        flushText()
        return out
    }
}
