package tedwester.convo.core.image

/**
 * Single home for every rule about assistant reply images.
 *
 * OpenRouter chat models can return images two ways:
 *  1. Natively — `message.content[]` parts with `type: "image_url"`, or a
 *     top-level `message.images[]` / `delta.images[]` array. These are
 *     normalized to `![](url)` markdown by [encodeImageMarkdown] at the
 *     network boundary ([tedwester.convo.core.network] parsing).
 *  2. Authored markdown — the model writes `![alt](url)` inline in its text,
 *     which flows through untouched.
 *
 * Everything downstream (streaming dedup, render sanitizing, Coil loading)
 * reads from here so the rules stay in one place.
 */
object AssistantImages {

    private val MarkdownImage = Regex("""!\[[^\]]*]\(\s*<?([^)\s]+)>?(?:\s+["'][^"']*["'])?\s*\)""")

    private val ImageExtension = Regex(
        """\.(png|jpe?g|webp|gif|bmp|avif|heic|heif|svg)(\?.*)?$""",
        RegexOption.IGNORE_CASE,
    )

    private const val MaxDataUriChars = 20_000_000

    /**
     * Clean a raw image URL for safe embedding in `![](…)` markdown and for
     * handing to Coil. Returns null when the URL can never render.
     *
     * - trims, strips `<…>` wrapping
     * - `data:image/…` URIs: all whitespace removed (servers fold long
     *   base64 across lines), requires `,payload`
     * - `http(s)` URLs: whitespace removed, `(`/`)`/space percent-encoded so
     *   the markdown destination never terminates early (signed URLs)
     */
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
        val cleaned = url.replace(Regex("\\s+"), "")
            .replace("(", "%28")
            .replace(")", "%29")
            .replace(" ", "%20")
        if (cleaned.isBlank() || cleaned.contains('"')) return null
        val parsed = runCatching { java.net.URI(cleaned) }.getOrNull() ?: return null
        if (parsed.host.isNullOrBlank()) return null
        return cleaned
    }

    fun isRenderableImageUrl(url: String?): Boolean = normalizeImageUrl(url) != null

    /** Wrap a URL as `![](url)`, or null when it can never render. */
    fun encodeImageMarkdown(rawUrl: String?): String? {
        val normalized = normalizeImageUrl(rawUrl) ?: return null
        return "![]($normalized)"
    }

    /** All normalized image URLs inside `![](…)` blocks. */
    fun extractMarkdownImageUrls(markdown: String): List<String> =
        MarkdownImage.findAll(markdown).mapNotNull { normalizeImageUrl(it.groupValues[1]) }.toList()

    /**
     * Strip `![](…)` blocks whose URL was already emitted (OpenRouter repeats
     * the cumulative `images[]` array on every SSE event). Mutates [seenUrls]
     * with each newly kept URL and returns the chunk with only fresh images.
     */
    fun stripDuplicateMarkdownImages(chunk: String, seenUrls: MutableSet<String>): String {
        if (!chunk.contains("![")) return chunk
        var result = chunk
        for (match in MarkdownImage.findAll(chunk).toList().asReversed()) {
            val normalized = normalizeImageUrl(match.groupValues[1])
            if (normalized == null) {
                // Unrenderable — drop the block so it never reserves a blank slot.
                result = result.removeRange(match.range)
                continue
            }
            if (!seenUrls.add(normalized)) {
                result = result.removeRange(match.range)
            } else {
                // Keep first sighting, but normalize the destination so Coil sees
                // the same URL that dedup tracks (whitespace/encoding fixed).
                val fixed = "![]($normalized)"
                if (match.value != fixed) {
                    result = result.replaceRange(match.range, fixed)
                }
            }
        }
        // Collapse gaps left behind, preserving leading text spacing
        // (" world\n\n![](dup)" -> " world", not "world" — the leading space
        // continues the previous streaming fragment).
        val collapsed = result.replace(Regex("\n{3,}"), "\n\n")
        if (collapsed.isBlank()) return ""
        // Drop trailing blank lines left by a removed image, keep leading.
        return collapsed.trimEnd()
    }

    /**
     * Hide a trailing half-written `![…](…` image while streaming so Coil is
     * never fired with a partial URL and no blank placeholder is reserved.
     * Complete images (ending in `)`) are kept. Plain `!` exclamations are kept.
     */
    fun sanitizeForStreaming(text: String): String {
        val bang = text.lastIndexOf("![")
        if (bang < 0) {
            // Lone trailing "!" is never an image start worth hiding — but a lone
            // "!" + "[" split across chunks ("…![") is covered above.
            return text
        }
        val tail = text.substring(bang)
        // Complete image that runs to (or past) the end — keep everything.
        // A complete image followed by more text is also fine; only a dangling
        // start at the very end is stripped.
        val complete = MarkdownImage.find(tail)
        if (complete != null && tail.substring(complete.range.last + 1).trim().isNotEmpty()) {
            // Complete image with text after it — nothing dangling at the end
            // unless there is *another* opener after the match.
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
        // No complete image from the last opener to the end — the opener is a
        // partial image still streaming in. Drop it (keep text before it).
        // Guard: "![alt]" without parens yet is also partial.
        return text.substring(0, bang).trimEnd()
    }

    /**
     * Join non-blank parts with blank lines, dropping exact duplicate blocks
     * (same text paragraph or same `![](url)` twice in one response).
     */
    fun joinContent(vararg parts: String): String {
        val seen = LinkedHashSet<String>()
        val out = ArrayList<String>(parts.size)
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.isBlank()) continue
            // Split multi-block parts so a duplicated image paragraph dedups
            // even when bundled with text: "text\n\n![](a)\n\n![](a)" -> once.
            val blocks = trimmed.split(Regex("\n{2,}"))
            for (block in blocks) {
                val b = block.trim()
                if (b.isBlank()) continue
                val key = if (b.startsWith("![")) {
                    normalizeImageUrl(MarkdownImage.find(b)?.groupValues?.getOrNull(1) ?: b) ?: b
                } else {
                    b
                }
                if (seen.add(key)) out.add(b)
            }
        }
        return out.joinToString("\n\n")
    }

    /** True when a bare string looks like an image URL worth auto-wrapping. */
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
}
