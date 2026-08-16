package tedwester.convo

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tedwester.convo.core.image.DataUriFetcher
import tedwester.convo.core.network.OpenRouterApi
import tedwester.convo.core.network.OpenRouterClient
import tedwester.convo.features.chat.data.ChatCompletionController
import tedwester.convo.features.chat.data.ChatNotifications
import tedwester.convo.features.chat.data.ChatRepository
import tedwester.convo.features.chat.data.ApiPreferencesStore
import tedwester.convo.features.chat.data.ComposerHintsStore
import tedwester.convo.features.chat.data.ComposerPreferencesStore
import tedwester.convo.features.chat.data.ChatModelFilterStore
import tedwester.convo.features.chat.data.QuickSettingsStore
import tedwester.convo.features.chat.data.ReasoningPreferencesStore
import tedwester.convo.features.chat.data.SearchPreferencesStore
import tedwester.convo.features.chat.data.TtsVoicePreferencesStore
import tedwester.convo.features.chat.data.VoicePreferencesStore

class ConvoApp : Application(), ImageLoaderFactory {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var repository: ChatRepository
        private set

    lateinit var api: OpenRouterApi
        private set

    lateinit var completionController: ChatCompletionController
        private set

    lateinit var reasoningPreferencesStore: ReasoningPreferencesStore
        private set

    lateinit var voicePreferencesStore: VoicePreferencesStore
        private set

    lateinit var ttsVoicePreferencesStore: TtsVoicePreferencesStore
        private set

    lateinit var chatModelFilterStore: ChatModelFilterStore
        private set

    lateinit var searchPreferencesStore: SearchPreferencesStore
        private set

    lateinit var quickSettingsStore: QuickSettingsStore
        private set

    lateinit var composerHintsStore: ComposerHintsStore
        private set

    lateinit var composerPreferencesStore: ComposerPreferencesStore
        private set

    lateinit var apiPreferencesStore: ApiPreferencesStore
        private set

    private val _pendingOpenChatId = MutableStateFlow<String?>(null)
    val pendingOpenChatId: StateFlow<String?> = _pendingOpenChatId.asStateFlow()

    fun offerOpenChat(chatId: String) {
        _pendingOpenChatId.value = chatId
    }

    fun consumePendingOpenChatId(): String? {
        val id = _pendingOpenChatId.value ?: return null
        _pendingOpenChatId.value = null
        return id
    }

    override fun onCreate() {
        super.onCreate()
        ChatNotifications.ensureChannels(this)
        repository = ChatRepository(this)
        reasoningPreferencesStore = ReasoningPreferencesStore(this)
        voicePreferencesStore = VoicePreferencesStore(this)
        ttsVoicePreferencesStore = TtsVoicePreferencesStore(this)
        chatModelFilterStore = ChatModelFilterStore(this)
        searchPreferencesStore = SearchPreferencesStore(this)
        quickSettingsStore = QuickSettingsStore(this)
        composerHintsStore = ComposerHintsStore(this)
        composerPreferencesStore = ComposerPreferencesStore(this)
        apiPreferencesStore = ApiPreferencesStore(this)
        val apiPrefs = apiPreferencesStore.load()
        api = OpenRouterClient(initialReadTimeoutMinutes = apiPrefs.requestTimeoutMinutes)
        completionController = ChatCompletionController(
            app = this,
            api = api,
            repository = repository,
            scope = applicationScope,
        )
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(DataUriFetcher.Factory()) }
            .build()
}
