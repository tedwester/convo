package tedwester.convo.features.chat.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import tedwester.convo.ConvoApp
import tedwester.convo.core.network.OpenRouterApi
import tedwester.convo.core.security.KeyStorage
import tedwester.convo.core.network.model.OpenRouterModel

@Composable
fun rememberChatState(
    apiKey: String,
    api: OpenRouterApi,
    initialModel: OpenRouterModel? = null,
): ChatState {
    val context = LocalContext.current
    val app = context.applicationContext as ConvoApp
    val scope = rememberCoroutineScope()
    val state = remember(apiKey) {
        ChatState(
            apiKey = apiKey,
            api = api,
            repository = app.repository,
            chatModelFilterStore = app.chatModelFilterStore,
            completions = app.completionController,
            reasoningStore = app.reasoningPreferencesStore,
            voiceStore = app.voicePreferencesStore,
            ttsVoiceStore = app.ttsVoicePreferencesStore,
            searchStore = app.searchPreferencesStore,
            apiPreferencesStore = app.apiPreferencesStore,
            keyStorage = KeyStorage(context.applicationContext),
            scope = scope,
            context = context.applicationContext,
            initialModel = initialModel,
        )
    }
    LaunchedEffect(state) {
        state.bootstrap()
        state.observeCompletions()
    }
    LaunchedEffect(state) {
        app.pendingOpenChatId.collect { pending ->
            if (pending == null) return@collect
            while (!state.isReady) {
                delay(50)
            }
            val id = app.consumePendingOpenChatId() ?: return@collect
            state.openChat(id)
        }
    }
    return state
}
