package tedwester.convo.features.chat

import androidx.compose.runtime.Composable
import tedwester.convo.core.network.OpenRouterApi
import tedwester.convo.core.network.model.OpenRouterModel
import tedwester.convo.features.chat.state.ChatState as StateChatState

typealias ChatState = StateChatState

@Composable
fun rememberChatState(
    apiKey: String,
    api: OpenRouterApi,
    initialModel: OpenRouterModel? = null,
): ChatState = tedwester.convo.features.chat.state.rememberChatState(apiKey, api, initialModel)
