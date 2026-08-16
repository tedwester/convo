package tedwester.convo.core.network

import org.json.JSONArray
import org.json.JSONObject
import tedwester.convo.core.network.model.ModelReasoningConfig
import tedwester.convo.core.network.model.StreamDelta
import tedwester.convo.features.chat.model.ReasoningEffort

/**
 * Shared JSON parsing helpers for [OpenRouterClient].
 * Kept separate so the client file stays focused on HTTP orchestration.
 */
internal fun extractContentText(raw: Any?): String = when (raw) {
    null, JSONObject.NULL -> ""
    is String -> raw
    is JSONArray -> buildString {
        for (i in 0 until raw.length()) {
            append(extractContentPart(raw.opt(i)))
        }
    }
    is JSONObject -> extractContentPart(raw)
    else -> raw.toString()
}

internal fun extractContentPart(part: Any?): String = when (part) {
    null, JSONObject.NULL -> ""
    is String -> part
    is JSONArray -> extractContentText(part)
    is JSONObject -> {
        val type = part.optString("type")
        when {
            type.equals("text", ignoreCase = true) ||
                type.equals("output_text", ignoreCase = true) ->
                part.optString("text")

            type.equals("image_url", ignoreCase = true) -> {
                val url = part.optJSONObject("image_url")?.optString("url")
                    ?: part.optString("url")
                if (url.isBlank()) "" else "![]($url)"
            }

            part.has("text") && !part.isNull("text") -> part.optString("text")
            part.has("content") -> extractContentText(part.opt("content"))
            else -> ""
        }
    }
    else -> part.toString()
}

internal fun joinContent(vararg parts: String): String =
    parts.filter { it.isNotBlank() }.joinToString("\n\n")

internal fun stringListFrom(array: JSONArray?): List<String> {
    if (array == null) return emptyList()
    return buildList {
        for (i in 0 until array.length()) {
            array.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
        }
    }
}

/**
 * OpenRouter reports `supported_voices` as either an array of strings or an
 * array of objects (with `name`/`voice_name`/`id`). Normalize to voice ids.
 */
internal fun parseSupportedVoices(raw: Any?): List<String> {
    val array = raw as? JSONArray ?: return emptyList()
    return buildList {
        for (i in 0 until array.length()) {
            when (val entry = array.opt(i)) {
                is String -> entry.takeIf { it.isNotBlank() }?.let { add(it) }
                is JSONObject -> {
                    val name = entry.optString("name").ifBlank {
                        entry.optString("voice_name").ifBlank { entry.optString("id") }
                    }
                    name.takeIf { it.isNotBlank() }?.let { add(it) }
                }
                else -> Unit
            }
        }
    }
}

internal fun parseReasoningDelta(delta: JSONObject): String? {
    if (delta.has("reasoning") && !delta.isNull("reasoning")) {
        val asString = delta.optString("reasoning")
        if (asString.isNotEmpty()) return asString
    }
    if (delta.has("reasoning_content") && !delta.isNull("reasoning_content")) {
        val asString = delta.optString("reasoning_content")
        if (asString.isNotEmpty()) return asString
    }
    val details = delta.optJSONArray("reasoning_details") ?: return null
    if (details.length() == 0) return null
    val builder = StringBuilder()
    for (i in 0 until details.length()) {
        val item = details.optJSONObject(i) ?: continue
        val type = item.optString("type")
        if (type.isNotBlank() &&
            !type.equals("reasoning.text", ignoreCase = true) &&
            !type.equals("text", ignoreCase = true)
        ) {
            continue
        }
        val text = when {
            item.has("text") && !item.isNull("text") -> item.optString("text")
            item.has("summary") && !item.isNull("summary") -> item.optString("summary")
            else -> ""
        }
        if (text.isNotEmpty()) builder.append(text)
    }
    return builder.toString().takeIf { it.isNotEmpty() }
}

internal fun parseStreamDelta(data: String): StreamDelta? {
    return runCatching {
        val json = JSONObject(data)
        val choices = json.optJSONArray("choices") ?: return null
        if (choices.length() == 0) return null
        val delta = choices.getJSONObject(0).optJSONObject("delta") ?: return null
        val content = joinContent(
            extractContentText(delta.opt("content")),
            extractContentText(delta.optJSONArray("images")),
        ).takeIf { it.isNotEmpty() }
        val reasoning = parseReasoningDelta(delta)
        if (content == null && reasoning == null) return null
        StreamDelta(content = content, reasoning = reasoning)
    }.getOrNull()
}

internal fun parseErrorMessage(body: String, code: Int): String {
    val fallback = "Request failed ($code)."
    if (body.isBlank()) return fallback
    return runCatching {
        val json = JSONObject(body)
        val error = json.optJSONObject("error")
        val msg = error?.optString("message")?.ifBlank { null }
            ?: json.optString("message").ifBlank { null }
        msg ?: fallback
    }.getOrDefault(fallback)
}

internal fun parseReasoningConfig(json: JSONObject): ModelReasoningConfig {
    val supportedEfforts: List<ReasoningEffort>? = when {
        !json.has("supported_efforts") || json.isNull("supported_efforts") -> null
        else -> {
            val array = json.optJSONArray("supported_efforts") ?: JSONArray()
            buildList {
                for (i in 0 until array.length()) {
                    val apiValue = array.optString(i)
                    if (apiValue.isBlank() || apiValue.equals("none", ignoreCase = true)) continue
                    add(ReasoningEffort.fromApiValue(apiValue))
                }
            }
        }
    }
    val defaultEffort = json.optString("default_effort")
        .takeIf { it.isNotBlank() && !it.equals("none", ignoreCase = true) }
        ?.let { ReasoningEffort.fromApiValue(it) }
    return ModelReasoningConfig(
        supportedEfforts = supportedEfforts,
        defaultEffort = defaultEffort,
        defaultEnabled = if (json.has("default_enabled")) {
            json.optBoolean("default_enabled")
        } else {
            null
        },
        mandatory = json.optBoolean("mandatory", false),
        supportsMaxTokens = json.optBoolean("supports_max_tokens", false),
    )
}
