package tedwester.convo.core.network

import kotlinx.coroutines.flow.Flow
import tedwester.convo.core.network.model.ChatMessageDto
import tedwester.convo.core.network.model.ImageRequest
import tedwester.convo.core.network.model.ImageResult
import tedwester.convo.core.network.model.ModelListQuery
import tedwester.convo.core.network.model.OpenRouterKeyInfo
import tedwester.convo.core.network.model.OpenRouterModel
import tedwester.convo.core.network.model.ReasoningRequest
import tedwester.convo.core.network.model.SpeechRequest
import tedwester.convo.core.network.model.SpeechResult
import tedwester.convo.core.network.model.TranscriptionRequest
import tedwester.convo.core.network.model.StreamDelta
import tedwester.convo.core.network.model.VideoRequest
import tedwester.convo.core.network.model.VideoResult

/**
 * Thin abstraction over the OpenRouter HTTP API.
 *
 * The UI and higher layers depend on this interface rather than on OkHttp or
 * any concrete HTTP stack, keeping the networking implementation swappable.
 */
interface OpenRouterApi {

    /**
     * Fetch the list of models currently available on OpenRouter.
     *
     * @param apiKey the user's OpenRouter API key.
     * @param query optional server-side filters/sorts (`category`, `sort`, …).
     * @throws OpenRouterApiException if the request fails.
     */
    suspend fun fetchModels(
        apiKey: String,
        query: ModelListQuery = ModelListQuery(),
    ): List<OpenRouterModel>

    /**
     * Look up a model previously returned by [fetchModels] from the in-memory
     * cache, or null if it has not been fetched yet.
     */
    fun findCachedModel(id: String): OpenRouterModel?

    /**
     * Send a chat completion request and return the assistant's reply.
     *
     * @param apiKey   the user's OpenRouter API key.
     * @param model    the OpenRouter model id to use.
     * @param messages the conversation history (system, user, assistant).
     * @throws OpenRouterApiException if the request fails.
     */
    suspend fun chatCompletion(
        apiKey: String,
        model: String,
        messages: List<ChatMessageDto>,
        enableWebSearch: Boolean = false,
        reasoning: ReasoningRequest? = null,
        maxTokens: Int? = null,
    ): String

    /**
     * Stream a chat completion as incremental content / reasoning deltas
     * (SSE `stream: true`).
     *
     * @throws OpenRouterApiException if the request fails before/during the stream.
     */
    fun chatCompletionStream(
        apiKey: String,
        model: String,
        messages: List<ChatMessageDto>,
        enableWebSearch: Boolean = false,
        reasoning: ReasoningRequest? = null,
        maxTokens: Int? = null,
    ): Flow<StreamDelta>

    /**
     * Rebuild the HTTP client read timeout used for chat completions and streaming.
     * Call when the user changes the request timeout in settings.
     */
    fun updateRequestTimeoutMinutes(minutes: Int)

    /**
     * Synthesize speech from text via OpenRouter's dedicated TTS endpoint
     * (`POST /api/v1/audio/speech`). Use this for models whose
     * [OpenRouterModel.modelKind] is [ModelKind.Tts] — sending them to
     * [chatCompletion] would fail.
     *
     * @throws OpenRouterApiException if the request fails.
     */
    suspend fun createSpeech(
        apiKey: String,
        request: SpeechRequest,
    ): SpeechResult

    /**
     * Generate images from a prompt via OpenRouter's image endpoint
     * (`POST /api/v1/images`). Use this for models whose
     * [OpenRouterModel.modelKind] is [ModelKind.ImageGen].
     *
     * @throws OpenRouterApiException if the request fails.
     */
    suspend fun createImage(
        apiKey: String,
        request: ImageRequest,
    ): ImageResult

    /**
     * Generate video from a prompt (and optional reference images) via
     * OpenRouter's async video endpoint (`POST /api/v1/videos`). Submits a
     * job, polls until it completes, then downloads the clip(s). Use this
     * for models whose [OpenRouterModel.modelKind] is [ModelKind.VideoGen].
     *
     * @throws OpenRouterApiException if the request fails or the job errors.
     */
    suspend fun createVideo(
        apiKey: String,
        request: VideoRequest,
    ): VideoResult

    /**
     * Transcribe audio to text via OpenRouter's STT endpoint
     * (`POST /api/v1/audio/transcriptions`). Used to turn a recorded voice
     * message into text for the TTS flow (and for dedicated transcription models).
     *
     * @return the transcribed text.
     * @throws OpenRouterApiException if the request fails.
     */
    suspend fun transcribeAudio(
        apiKey: String,
        request: TranscriptionRequest,
    ): String

    /**
     * Fetch key usage (`GET /api/v1/key`) and account credits (`GET /api/v1/credits`).
     *
     * @throws OpenRouterApiException if the key request fails.
     */
    suspend fun fetchKeyInfo(apiKey: String): OpenRouterKeyInfo
}
