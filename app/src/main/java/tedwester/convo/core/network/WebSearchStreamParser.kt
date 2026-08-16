package tedwester.convo.core.network

import org.json.JSONArray
import org.json.JSONObject
import tedwester.convo.features.chat.model.WebSearchCitation
import tedwester.convo.features.chat.model.WebSearchStep
import tedwester.convo.features.chat.model.domainFromUrl

internal class WebSearchStreamParser {
    private val toolCallBuffers = mutableMapOf<Int, StringBuilder>()
    private val toolCallIds = mutableMapOf<Int, String>()
    private val toolCallNames = mutableMapOf<Int, String>()
    private val steps = mutableListOf<WebSearchStep>()
    private val seenCitationUrls = mutableSetOf<String>()
    private var stepCounter = 0

    fun parseChunk(rawJson: String): List<WebSearchStep>? {
        val json = runCatching { JSONObject(rawJson) }.getOrNull() ?: return null
        var changed = false

        json.optJSONArray("annotations")?.let {
            changed = mergeAnnotations(it) || changed
        }

        val choices = json.optJSONArray("choices") ?: return snapshotIfChanged(changed)
        if (choices.length() == 0) return snapshotIfChanged(changed)

        val choice = choices.getJSONObject(0)
        choice.optJSONObject("message")?.optJSONArray("annotations")?.let {
            changed = mergeAnnotations(it) || changed
        }
        choice.optJSONObject("message")?.optJSONArray("tool_calls")?.let { toolCalls ->
            changed = ingestToolCalls(toolCalls) || changed
        }

        val delta = choice.optJSONObject("delta")
        delta?.optJSONArray("annotations")?.let {
            changed = mergeAnnotations(it) || changed
        }

        delta?.optJSONArray("tool_calls")?.let { toolCalls ->
            changed = ingestToolCalls(toolCalls) || changed
        }
        changed = syncInProgressQueriesFromBuffers() || changed

        when (choice.optString("finish_reason")) {
            "tool_calls" -> changed = finalizeToolCalls() || changed
        }

        return snapshotIfChanged(changed)
    }

    private fun ingestToolCalls(toolCalls: JSONArray): Boolean {
        var changed = false
        for (i in 0 until toolCalls.length()) {
            val toolCall = toolCalls.getJSONObject(i)
            val index = if (toolCall.has("index")) toolCall.optInt("index") else i
            toolCall.optString("id").takeIf { it.isNotBlank() }?.let { toolCallIds[index] = it }
            val type = toolCall.optString("type")
            if (isWebSearchTool(type)) {
                toolCallNames[index] = type
            }
            toolCall.optJSONObject("function")?.let { fn ->
                fn.optString("name").takeIf { it.isNotBlank() }?.let { toolCallNames[index] = it }
            }
            val name = toolCallNames[index].orEmpty()
            if (name.isNotBlank() && !isWebSearchTool(name) && !isWebSearchTool(type)) {
                continue
            }
            val chunk = readToolCallArguments(toolCall)
            if (chunk.isNotBlank()) {
                toolCallBuffers.getOrPut(index) { StringBuilder() }.append(chunk)
                changed = true
            }
        }
        return changed
    }

    private fun readToolCallArguments(toolCall: JSONObject): String {
        toolCall.optJSONObject("function")
            ?.optString("arguments")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        toolCall.optString("arguments")
            .takeIf { it.isNotBlank() }
            ?.let { return it }
        toolCall.optJSONObject("arguments")
            ?.takeIf { it.length() > 0 }
            ?.let { return it.toString() }
        return ""
    }

    fun currentSteps(): List<WebSearchStep> = steps.toList()

    private fun snapshotIfChanged(changed: Boolean): List<WebSearchStep>? =
        if (changed) steps.toList() else null

    private fun finalizeToolCalls(): Boolean {
        if (toolCallBuffers.isEmpty()) return false
        var changed = false
        for ((index, buffer) in toolCallBuffers) {
            val name = toolCallNames[index].orEmpty()
            if (!isWebSearchTool(name)) continue
            val query = extractQueryFromArguments(buffer.toString())
            val id = toolCallIds[index] ?: nextStepId()
            changed = upsertSearchStep(id = id, query = query, isSearching = true) || changed
        }
        toolCallBuffers.clear()
        toolCallIds.clear()
        toolCallNames.clear()
        return changed
    }

    private fun syncInProgressQueriesFromBuffers(): Boolean {
        var changed = false
        for ((index, buffer) in toolCallBuffers) {
            val name = toolCallNames[index].orEmpty()
            if (name.isNotBlank() && !isWebSearchTool(name)) continue
            val query = extractQueryFromArguments(buffer.toString(), partial = true)
            if (query.isBlank()) continue
            val id = toolCallIds[index] ?: "pending_$index"
            if (upsertSearchStep(id = id, query = query, isSearching = true)) {
                changed = true
            }
        }
        return changed
    }

    private fun upsertSearchStep(id: String, query: String, isSearching: Boolean): Boolean {
        val existingIndex = steps.indexOfFirst { it.id == id }
        if (existingIndex >= 0) {
            val existing = steps[existingIndex]
            val mergedQuery = query.ifBlank { existing.query }
            if (existing.query == mergedQuery && existing.isSearching == isSearching) return false
            steps[existingIndex] = existing.copy(query = mergedQuery, isSearching = isSearching)
            return true
        }
        val orphanIndex = steps.indexOfLast { step ->
            step.query.isBlank() && step.citations.isNotEmpty()
        }
        if (orphanIndex >= 0 && query.isNotBlank()) {
            val orphan = steps[orphanIndex]
            steps[orphanIndex] = orphan.copy(id = id, query = query, isSearching = isSearching)
            return true
        }
        completeActiveSearches()
        steps.add(
            WebSearchStep(
                id = id,
                query = query,
                isSearching = isSearching,
            ),
        )
        return true
    }

    private fun mergeAnnotations(array: JSONArray): Boolean {
        var changed = false
        for (i in 0 until array.length()) {
            val annotation = array.optJSONObject(i) ?: continue
            val annotationType = annotation.optString("type")
            if (annotationType.contains("web_search", ignoreCase = true)) {
                val query = annotation.optString("query")
                    .ifBlank { annotation.optJSONObject("web_search")?.optString("query").orEmpty() }
                    .ifBlank { annotation.optJSONObject("webSearch")?.optString("query").orEmpty() }
                if (query.isNotBlank()) {
                    val id = annotation.optString("id").ifBlank { nextStepId() }
                    changed = upsertSearchStep(id = id, query = query, isSearching = true) || changed
                }
                continue
            }
            if (!annotationType.equals("url_citation", ignoreCase = true)) continue
            val payload = annotation.optJSONObject("url_citation")
                ?: annotation.optJSONObject("urlCitation")
                ?: continue
            val url = payload.optString("url").ifBlank { annotation.optString("url") }
            if (url.isBlank() || url in seenCitationUrls) continue
            seenCitationUrls.add(url)
            val title = payload.optString("title")
                .ifBlank { annotation.optString("title") }
                .ifBlank { domainFromUrl(url) }
            val description = payload.optString("content")
                .ifBlank { payload.optString("description") }
                .ifBlank { payload.optString("snippet") }
                .ifBlank { annotation.optString("content") }
            val publishedDate = payload.optString("published_date")
                .ifBlank { payload.optString("publishedDate") }
                .ifBlank { payload.optString("date") }
            val citation = WebSearchCitation(
                url = url,
                title = title,
                description = description,
                publishedDate = publishedDate,
            )
            changed = appendCitation(citation) || changed
        }
        return changed
    }

    private fun appendCitation(citation: WebSearchCitation): Boolean {
        if (steps.isEmpty()) {
            steps.add(
                WebSearchStep(
                    id = nextStepId(),
                    query = "",
                    citations = listOf(citation),
                    isSearching = true,
                ),
            )
            return true
        }
        val targetIndex = steps.indexOfLast { it.isSearching }.takeIf { it >= 0 }
            ?: steps.lastIndex
        val target = steps[targetIndex]
        if (target.citations.any { it.url == citation.url }) return false
        steps[targetIndex] = target.copy(
            citations = target.citations + citation,
            isSearching = false,
        )
        return true
    }

    private fun completeActiveSearches() {
        for (i in steps.indices) {
            if (steps[i].isSearching) {
                steps[i] = steps[i].copy(isSearching = false)
            }
        }
    }

    private fun nextStepId(): String {
        stepCounter += 1
        return "search_$stepCounter"
    }

    private fun isWebSearchTool(name: String): Boolean {
        if (name.isBlank()) return true
        val normalized = name.lowercase()
        return normalized.contains("web_search") ||
            normalized.contains("websearch") ||
            normalized.contains("web-search") ||
            normalized == "search"
    }

    private fun extractQueryFromArguments(args: String, partial: Boolean = false): String {
        if (args.isBlank()) return ""
        runCatching {
            val json = JSONObject(args)
            for (key in listOf("query", "search_query", "q", "input", "search", "text", "keywords")) {
                val value = json.optString(key)
                if (value.isNotBlank()) return value
            }
            json.optJSONArray("search_terms")?.let { terms ->
                val joined = buildList {
                    for (i in 0 until terms.length()) {
                        terms.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                }.joinToString(" ")
                if (joined.isNotBlank()) return joined
            }
        }
        Regex(""""(?:query|search_query|q|input|search|text)"\s*:\s*"((?:\\.|[^"\\])*)""")
            .find(args)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { unescapeJsonString(it) }
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        if (!partial && !args.startsWith("{") && !args.startsWith("[")) {
            return args.trim()
        }
        return ""
    }

    private fun unescapeJsonString(raw: String): String =
        raw.replace("\\\\", "\\")
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
}
