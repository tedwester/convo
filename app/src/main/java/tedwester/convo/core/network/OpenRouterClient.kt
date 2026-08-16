package tedwester.convo.core.network

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import tedwester.convo.core.network.model.ChatMessageDto
import tedwester.convo.core.network.model.GeneratedImage
import tedwester.convo.core.network.model.ImageRequest
import tedwester.convo.core.network.model.ImageResult
import tedwester.convo.core.network.model.ModelListQuery
import tedwester.convo.core.network.model.ModelPricing
import tedwester.convo.core.network.model.ModelReasoningConfig
import tedwester.convo.core.network.model.OpenRouterKeyInfo
import tedwester.convo.core.network.model.OpenRouterModel
import tedwester.convo.core.network.model.ReasoningRequest
import tedwester.convo.core.network.model.SpeechRequest
import tedwester.convo.core.network.model.SpeechResult
import tedwester.convo.core.network.model.StreamDelta
import tedwester.convo.core.network.model.TranscriptionRequest
import tedwester.convo.core.network.model.GeneratedVideo
import tedwester.convo.core.network.model.VideoRequest
import tedwester.convo.core.network.model.VideoResult
import tedwester.convo.core.AppMetadata
import tedwester.convo.features.chat.model.ReasoningEffort
import java.util.concurrent.TimeUnit

class OpenRouterClient(
    initialReadTimeoutMinutes: Int = DEFAULT_READ_TIMEOUT_MINUTES,
) : OpenRouterApi {

    @Volatile
    private var readTimeoutMinutes: Int =
        initialReadTimeoutMinutes.coerceIn(MIN_READ_TIMEOUT_MINUTES, MAX_READ_TIMEOUT_MINUTES)

    @Volatile
    private var client: OkHttpClient = buildClient(readTimeoutMinutes)

    override fun updateRequestTimeoutMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(MIN_READ_TIMEOUT_MINUTES, MAX_READ_TIMEOUT_MINUTES)
        if (clamped == readTimeoutMinutes) return
        readTimeoutMinutes = clamped
        client = buildClient(clamped)
    }

    private val modelCache = linkedMapOf<String, CachedModels>()

    override suspend fun fetchModels(
        apiKey: String,
        query: ModelListQuery,
    ): List<OpenRouterModel> {
        val now = System.currentTimeMillis()
        val key = query.cacheKey
        val cached = modelCache[key]
        if (cached != null && now - cached.atMillis < MODEL_CACHE_TTL_MILLIS) {
            return cached.models
        }

        val fetched = fetchModelsFromNetwork(apiKey, query)
        modelCache[key] = CachedModels(fetched, now)
        while (modelCache.size > MODEL_CACHE_MAX_ENTRIES) {
            val oldest = modelCache.keys.firstOrNull() ?: break
            modelCache.remove(oldest)
        }
        return fetched
    }

    override fun findCachedModel(id: String): OpenRouterModel? {
        if (id.isBlank()) return null
        for (entry in modelCache.values) {
            entry.models.find { it.id == id }?.let { return it }
        }
        return null
    }

    private suspend fun fetchModelsFromNetwork(
        apiKey: String,
        query: ModelListQuery,
    ): List<OpenRouterModel> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(BASE_URL + "/api/v1/models" + query.toUrlSuffix())
                .header("Authorization", "Bearer $apiKey")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw OpenRouterApiException(
                    message = "Failed to load models (${response.code}).",
                    code = response.code,
                )
            }

            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: JSONArray()
            buildList {
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val pricingJson = item.optJSONObject("pricing")
                    val arch = item.optJSONObject("architecture")
                    val inputModalities = stringListFrom(arch?.optJSONArray("input_modalities"))
                    val outputModalities = stringListFrom(arch?.optJSONArray("output_modalities"))
                    val supportedParameters = stringListFrom(item.optJSONArray("supported_parameters"))
                    val reasoningConfig = item.optJSONObject("reasoning")?.let(::parseReasoningConfig)
                    val supportedVoices = parseSupportedVoices(item.opt("supported_voices"))
                    val model = OpenRouterModel(
                        id = item.optString("id"),
                        name = item.optString("name").ifBlank { item.optString("id") },
                        contextLength = if (item.has("context_length")) {
                            item.optInt("context_length")
                        } else {
                            null
                        },
                        pricing = pricingJson?.let {
                            ModelPricing(
                                prompt = it.optString("prompt").ifBlank { null },
                                completion = it.optString("completion").ifBlank { null },
                            )
                        },
                        description = item.optString("description").ifBlank { null },
                        inputModalities = inputModalities,
                        outputModalities = outputModalities,
                        supportedParameters = supportedParameters,
                        reasoningConfig = reasoningConfig,
                        supportedVoices = supportedVoices,
                        created = if (item.has("created") && !item.isNull("created")) {
                            item.optLong("created").takeIf { it > 0 }
                        } else {
                            null
                        },
                    )
                    if (model.isEmbeddingModel || model.isRerankModel) continue
                    add(model)
                }
            }
        }

    private data class CachedModels(
        val models: List<OpenRouterModel>,
        val atMillis: Long,
    )

    override suspend fun chatCompletion(
        apiKey: String,
        model: String,
        messages: List<ChatMessageDto>,
        enableWebSearch: Boolean,
        reasoning: ReasoningRequest?,
        maxTokens: Int?,
    ): String = withContext(Dispatchers.IO) {
        val request = buildChatCompletionRequest(
            apiKey = apiKey,
            model = model,
            messages = messages,
            stream = false,
            enableWebSearch = enableWebSearch,
            reasoning = reasoning,
            maxTokens = maxTokens,
        )
        val response = client.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw OpenRouterApiException(
                message = parseErrorMessage(body, response.code),
                code = response.code,
            )
        }

        val json = JSONObject(body)
        val choices = json.optJSONArray("choices") ?: JSONArray()
        if (choices.length() == 0) return@withContext ""
        val message = choices.getJSONObject(0).optJSONObject("message")
        extractContentText(message?.opt("content"))
            .let { text ->
                val images = extractContentText(message?.optJSONArray("images"))
                joinContent(text, images)
            }
    }

    override fun chatCompletionStream(
        apiKey: String,
        model: String,
        messages: List<ChatMessageDto>,
        enableWebSearch: Boolean,
        reasoning: ReasoningRequest?,
        maxTokens: Int?,
    ): Flow<StreamDelta> = callbackFlow {
        val request = buildChatCompletionRequest(
            apiKey = apiKey,
            model = model,
            messages = messages,
            stream = true,
            enableWebSearch = enableWebSearch,
            reasoning = reasoning,
            maxTokens = maxTokens,
        )
        val webSearchParser = if (enableWebSearch) WebSearchStreamParser() else null
        val call = client.newCall(request)
        val readerJob = launch(Dispatchers.IO) {
            try {
                val response = call.execute()
                val body = response.body
                if (!response.isSuccessful) {
                    val err = runCatching { body.string() }.getOrDefault("")
                    throw OpenRouterApiException(
                        message = parseErrorMessage(err, response.code),
                        code = response.code,
                    )
                }
                val source = body.source()
                while (isActive && !source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank() || !line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val parsed = parseStreamDelta(data)
                    val webSearchSteps = webSearchParser?.parseChunk(data)
                    if (parsed == null && webSearchSteps == null) continue
                    trySend(
                        StreamDelta(
                            content = parsed?.content,
                            reasoning = parsed?.reasoning,
                            webSearchSteps = webSearchSteps,
                        ),
                    )
                }
                close()
            } catch (e: Exception) {
                close(e)
            }
        }
        awaitClose {
            call.cancel()
            readerJob.cancel()
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun createSpeech(
        apiKey: String,
        request: SpeechRequest,
    ): SpeechResult = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("model", request.model)
            put("input", request.input)
            request.voice?.takeIf { it.isNotBlank() }?.let { put("voice", it) }
            request.responseFormat?.takeIf { it.isNotBlank() }?.let { put("response_format", it) }
            request.speed?.let { put("speed", it) }
        }
        val httpRequest = Request.Builder()
            .url(BASE_URL + "/api/v1/audio/speech")
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = client.newCall(httpRequest).execute()
        val bytes = response.body?.bytes() ?: ByteArray(0)
        if (!response.isSuccessful) {
            val text = runCatching { String(bytes) }.getOrDefault("")
            throw OpenRouterApiException(
                message = parseErrorMessage(text.ifBlank { "" }, response.code),
                code = response.code,
            )
        }
        if (bytes.isEmpty()) {
            throw OpenRouterApiException("TTS response was empty.", response.code)
        }
        SpeechResult(audioBytes = bytes, contentType = response.body?.contentType()?.toString())
    }

    override suspend fun createImage(
        apiKey: String,
        request: ImageRequest,
    ): ImageResult = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("model", request.model)
            put("prompt", request.prompt)
            put("n", request.n.coerceIn(1, 10))
            request.resolution?.takeIf { it.isNotBlank() }?.let { put("resolution", it) }
            request.aspectRatio?.takeIf { it.isNotBlank() }?.let { put("aspect_ratio", it) }
            request.outputFormat?.takeIf { it.isNotBlank() }?.let { put("output_format", it) }
        }
        val httpRequest = Request.Builder()
            .url(BASE_URL + "/api/v1/images")
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = client.newCall(httpRequest).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw OpenRouterApiException(
                message = parseErrorMessage(body, response.code),
                code = response.code,
            )
        }

        val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
        val images = buildList {
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val b64 = item.optString("b64_json").ifBlank { item.optString("b64") }
                if (b64.isBlank()) continue
                val decoded = runCatching { Base64.decode(b64, Base64.DEFAULT) }
                    .getOrNull() ?: continue
                val mediaType = item.optString("media_type").ifBlank { "image/png" }
                add(GeneratedImage(bytes = decoded, mediaType = mediaType))
            }
        }
        if (images.isEmpty()) {
            throw OpenRouterApiException("Image response contained no image data.", response.code)
        }
        ImageResult(images = images)
    }

    override suspend fun createVideo(
        apiKey: String,
        request: VideoRequest,
    ): VideoResult {
        val submitJson = withContext(Dispatchers.IO) {
            val payload = JSONObject().apply {
                put("model", request.model)
                request.prompt?.takeIf { it.isNotBlank() }?.let { put("prompt", it) }
                if (request.frameImages.isNotEmpty()) {
                    put(
                        "frame_images",
                        JSONArray().apply {
                            request.frameImages.forEach { frame ->
                                put(
                                    JSONObject().apply {
                                        put("type", "image_url")
                                        put("frame_type", frame.frameType)
                                        put(
                                            "image_url",
                                            JSONObject().apply { put("url", frame.dataUrl) },
                                        )
                                    },
                                )
                            }
                        },
                    )
                }
                if (request.inputReferences.isNotEmpty()) {
                    put(
                        "input_references",
                        JSONArray().apply {
                            request.inputReferences.forEach { dataUrl ->
                                put(
                                    JSONObject().apply {
                                        put("type", "image_url")
                                        put(
                                            "image_url",
                                            JSONObject().apply { put("url", dataUrl) },
                                        )
                                    },
                                )
                            }
                        },
                    )
                }
            }
            val httpRequest = Request.Builder()
                .url(BASE_URL + "/api/v1/videos")
                .header("Authorization", "Bearer $apiKey")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val response = client.newCall(httpRequest).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw OpenRouterApiException(
                    message = parseErrorMessage(body, response.code),
                    code = response.code,
                )
            }
            JSONObject(body)
        }

        var status = submitJson.optString("status")
        var jobJson = submitJson
        if (status !in VIDEO_TERMINAL_STATUSES) {
            val jobId = submitJson.optString("id")
            val pollingUrl = resolveOpenRouterUrl(
                submitJson.optString("polling_url").ifBlank {
                    if (jobId.isNotBlank()) "/api/v1/videos/$jobId" else ""
                },
            )
            if (pollingUrl.isBlank()) {
                throw OpenRouterApiException("Video job did not return a polling URL.", 0)
            }
            var waitMs = VIDEO_POLL_INITIAL_DELAY_MS
            val deadline = System.currentTimeMillis() + VIDEO_POLL_TIMEOUT_MS
            while (status !in VIDEO_TERMINAL_STATUSES) {
                if (System.currentTimeMillis() >= deadline) {
                    throw OpenRouterApiException("Video generation timed out.", 0)
                }
                delay(waitMs)
                waitMs = (waitMs * 3 / 2).coerceAtMost(VIDEO_POLL_MAX_DELAY_MS)
                jobJson = withContext(Dispatchers.IO) {
                    val pollRequest = Request.Builder()
                        .url(pollingUrl)
                        .header("Authorization", "Bearer $apiKey")
                        .get()
                        .build()
                    val response = client.newCall(pollRequest).execute()
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw OpenRouterApiException(
                            message = parseErrorMessage(body, response.code),
                            code = response.code,
                        )
                    }
                    JSONObject(body)
                }
                status = jobJson.optString("status")
            }
        }

        when (status) {
            "failed", "cancelled", "expired" -> {
                val detail = jobJson.optString("error").ifBlank { status }
                throw OpenRouterApiException("Video generation $detail.", 0)
            }
        }

        val urls = extractVideoUrls(jobJson)
        if (urls.isEmpty()) {
            throw OpenRouterApiException("Video job completed with no video URL.", 0)
        }
        val videos = urls.mapNotNull { url ->
            runCatching { downloadGeneratedVideo(apiKey, url) }.getOrNull()
        }
        if (videos.isEmpty()) {
            throw OpenRouterApiException("Could not download the generated video.", 0)
        }
        return VideoResult(videos = videos)
    }

    override suspend fun transcribeAudio(
        apiKey: String,
        request: TranscriptionRequest,
    ): String = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("model", request.model)
            put(
                "input_audio",
                JSONObject().apply {
                    put("data", request.audioBase64)
                    put("format", request.format)
                },
            )
            request.language?.takeIf { it.isNotBlank() }?.let { put("language", it) }
        }
        val httpRequest = Request.Builder()
            .url(BASE_URL + "/api/v1/audio/transcriptions")
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = client.newCall(httpRequest).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw OpenRouterApiException(
                message = parseErrorMessage(body, response.code),
                code = response.code,
            )
        }
        JSONObject(body).optString("text").ifBlank {
            throw OpenRouterApiException("Transcription returned no text.", response.code)
        }
    }

    private fun buildChatCompletionRequest(
        apiKey: String,
        model: String,
        messages: List<ChatMessageDto>,
        stream: Boolean,
        enableWebSearch: Boolean = false,
        reasoning: ReasoningRequest? = null,
        maxTokens: Int? = null,
    ): Request {
        val messagesJson = JSONArray().apply {
            messages.forEach { message ->
                put(messageToJson(message))
            }
        }
        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messagesJson)
            put("stream", stream)
            if (enableWebSearch) {
                put(
                    "tools",
                    JSONArray().apply {
                        put(
                            JSONObject().apply {
                                put("type", "openrouter:web_search")
                            },
                        )
                    },
                )
            }
            if (reasoning != null) {
                put("reasoning", reasoning.toJson())
            }
            if (maxTokens != null && maxTokens > 0) {
                put("max_tokens", maxTokens)
            }
        }
        return Request.Builder()
            .url(BASE_URL + "/api/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun messageToJson(message: ChatMessageDto): JSONObject =
        JSONObject().apply {
            put("role", message.role)
            val audio = message.audioBase64
            val format = message.audioFormat
            val images = message.imageDataUrls
            val multimodal = (!audio.isNullOrBlank() && !format.isNullOrBlank()) ||
                images.isNotEmpty()
            if (multimodal) {
                put(
                    "content",
                    JSONArray().apply {
                        if (message.content.isNotBlank()) {
                            put(
                                JSONObject().apply {
                                    put("type", "text")
                                    put("text", message.content)
                                },
                            )
                        }
                        images.forEach { dataUrl ->
                            put(
                                JSONObject().apply {
                                    put("type", "image_url")
                                    put(
                                        "image_url",
                                        JSONObject().apply {
                                            put("url", dataUrl)
                                        },
                                    )
                                },
                            )
                        }
                        if (!audio.isNullOrBlank() && !format.isNullOrBlank()) {
                            put(
                                JSONObject().apply {
                                    put("type", "input_audio")
                                    put(
                                        "input_audio",
                                        JSONObject().apply {
                                            put("data", audio)
                                            put("format", format)
                                        },
                                    )
                                },
                            )
                        }
                    },
                )
            } else {
                put("content", message.content)
            }
        }

    override suspend fun fetchKeyInfo(apiKey: String): OpenRouterKeyInfo =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(BASE_URL + "/api/v1/key")
                .header("Authorization", "Bearer $apiKey")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw OpenRouterApiException(
                    message = parseErrorMessage(body, response.code),
                    code = response.code,
                )
            }

            val data = JSONObject(body).optJSONObject("data")
                ?: throw OpenRouterApiException("Missing key data.", response.code)

            fun JSONObject.optDoubleOrNull(key: String): Double? {
                if (!has(key) || isNull(key)) return null
                return optDouble(key).takeUnless { it.isNaN() }
            }

            val (totalCredits, totalUsage) = fetchAccountCredits(apiKey)

            OpenRouterKeyInfo(
                label = data.optString("label").ifBlank { null },
                usage = data.optDouble("usage", 0.0).takeUnless { it.isNaN() } ?: 0.0,
                limit = data.optDoubleOrNull("limit"),
                limitRemaining = data.optDoubleOrNull("limit_remaining"),
                totalCredits = totalCredits,
                totalUsage = totalUsage,
            )
        }

    private fun fetchAccountCredits(apiKey: String): Pair<Double?, Double?> {
        val request = Request.Builder()
            .url(BASE_URL + "/api/v1/credits")
            .header("Authorization", "Bearer $apiKey")
            .build()

        return runCatching {
            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@runCatching null to null

            val data = JSONObject(body).optJSONObject("data") ?: return@runCatching null to null
            fun JSONObject.optDoubleOrNull(key: String): Double? {
                if (!has(key) || isNull(key)) return null
                return optDouble(key).takeUnless { it.isNaN() }
            }
            data.optDoubleOrNull("total_credits") to data.optDoubleOrNull("total_usage")
        }.getOrDefault(null to null)
    }

    private suspend fun downloadGeneratedVideo(apiKey: String, url: String): GeneratedVideo =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            val response = client.newCall(request).execute()
            val bytes = response.body?.bytes() ?: ByteArray(0)
            if (!response.isSuccessful) {
                val text = runCatching { String(bytes) }.getOrDefault("")
                throw OpenRouterApiException(
                    message = parseErrorMessage(text.ifBlank { "" }, response.code),
                    code = response.code,
                )
            }
            if (bytes.isEmpty()) {
                throw OpenRouterApiException("Downloaded video was empty.", response.code)
            }
            val mediaType = response.header("Content-Type")
                ?.substringBefore(';')
                ?.trim()
                ?.takeIf { it.startsWith("video/", ignoreCase = true) }
                ?: guessVideoMediaType(url)
            GeneratedVideo(bytes = bytes, mediaType = mediaType)
        }

    private companion object {
        const val BASE_URL = "https://openrouter.ai"
        val APP_REFERER = AppMetadata.GITHUB_REPO
        const val APP_TITLE = "Convo"
        const val MODEL_CACHE_TTL_MILLIS = 10 * 60 * 1000L // 10 minutes
        const val MODEL_CACHE_MAX_ENTRIES = 24
        const val VIDEO_POLL_INITIAL_DELAY_MS = 2_000L
        const val VIDEO_POLL_MAX_DELAY_MS = 8_000L
        const val VIDEO_POLL_TIMEOUT_MS = 12 * 60 * 1000L // 12 minutes
        val VIDEO_TERMINAL_STATUSES = setOf("completed", "failed", "cancelled", "expired")
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun resolveOpenRouterUrl(raw: String): String {
            val url = raw.trim()
            if (url.isBlank()) return ""
            return when {
                url.startsWith("http://", ignoreCase = true) ||
                    url.startsWith("https://", ignoreCase = true) -> url
                url.startsWith("/") -> BASE_URL + url
                else -> "$BASE_URL/$url"
            }
        }

        fun extractVideoUrls(json: JSONObject): List<String> {
            val urls = linkedSetOf<String>()
            fun add(value: String?) {
                value?.trim()?.takeIf { it.isNotBlank() }?.let(urls::add)
            }
            fun addAll(array: JSONArray?) {
                if (array == null) return
                for (i in 0 until array.length()) {
                    when (val item = array.opt(i)) {
                        is String -> add(item)
                        is JSONObject -> {
                            add(item.optString("url"))
                            add(item.optString("unsigned_url"))
                            add(item.optString("signed_url"))
                        }
                    }
                }
            }
            addAll(json.optJSONArray("unsigned_urls"))
            addAll(json.optJSONArray("signed_urls"))
            addAll(json.optJSONArray("urls"))
            add(json.optString("url"))
            json.optJSONArray("data")?.let { data ->
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    add(item.optString("url"))
                    addAll(item.optJSONArray("unsigned_urls"))
                }
            }
            return urls.toList()
        }

        fun guessVideoMediaType(url: String): String {
            val path = url.substringBefore('?').lowercase()
            return when {
                path.endsWith(".webm") -> "video/webm"
                path.endsWith(".mov") -> "video/quicktime"
                path.endsWith(".mkv") -> "video/x-matroska"
                else -> "video/mp4"
            }
        }

        const val DEFAULT_READ_TIMEOUT_MINUTES = 5
        const val MIN_READ_TIMEOUT_MINUTES = 1
        const val MAX_READ_TIMEOUT_MINUTES = 45

        fun buildClient(readTimeoutMinutes: Int): OkHttpClient {
            val clamped = readTimeoutMinutes.coerceIn(MIN_READ_TIMEOUT_MINUTES, MAX_READ_TIMEOUT_MINUTES)
            return OkHttpClient.Builder()
                .addInterceptor(AppAttributionInterceptor())
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(clamped.toLong(), TimeUnit.MINUTES)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()
        }
    }

    private class AppAttributionInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request().newBuilder()
                .header("HTTP-Referer", APP_REFERER)
                .header("X-OpenRouter-Title", APP_TITLE)
                .build()
            return chain.proceed(request)
        }
    }
}
