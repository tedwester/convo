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

interface OpenRouterApi {

    suspend fun fetchModels(
        apiKey: String,
        query: ModelListQuery = ModelListQuery(),
    ): List<OpenRouterModel>

    fun findCachedModel(id: String): OpenRouterModel?

    suspend fun chatCompletion(
        apiKey: String,
        model: String,
        messages: List<ChatMessageDto>,
        enableWebSearch: Boolean = false,
        reasoning: ReasoningRequest? = null,
        maxTokens: Int? = null,
    ): String

    fun chatCompletionStream(
        apiKey: String,
        model: String,
        messages: List<ChatMessageDto>,
        enableWebSearch: Boolean = false,
        reasoning: ReasoningRequest? = null,
        maxTokens: Int? = null,
    ): Flow<StreamDelta>

    fun updateRequestTimeoutMinutes(minutes: Int)

    suspend fun createSpeech(
        apiKey: String,
        request: SpeechRequest,
    ): SpeechResult

    suspend fun createImage(
        apiKey: String,
        request: ImageRequest,
    ): ImageResult

    suspend fun createVideo(
        apiKey: String,
        request: VideoRequest,
    ): VideoResult

    suspend fun transcribeAudio(
        apiKey: String,
        request: TranscriptionRequest,
    ): String

    suspend fun fetchKeyInfo(apiKey: String): OpenRouterKeyInfo
}
