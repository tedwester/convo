package tedwester.convo.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tedwester.convo.ui.components.ConvoIconButton
import tedwester.convo.ui.components.ConvoIconButtonGap
import tedwester.convo.ui.icons.ConvoIcons

@Composable
fun HelpScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = MaterialTheme.colorScheme.background

    BackHandler { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        HelpHeader(onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            HelpSection(
                title = "Getting started",
                body = """
                    Convo is a chat client for OpenRouter. You need an OpenRouter API key to use it — create one at openrouter.ai/keys and paste it during onboarding or in Settings.

                    Your key is stored encrypted on this device. Convo sends your messages and attachments to OpenRouter, which routes them to the AI model you choose. Usage is billed through your OpenRouter account.
                """.trimIndent(),
            )

            HelpSection(
                title = "Chats and conversations",
                body = """
                    Tap the account icon in the conversation header to open your chat list. Swipe from the left edge of a conversation to open it as well.

                    Start a new chat from the list header or from a quick-settings shortcut. Each chat keeps its own message history, title, system message, and selected model.

                    While a reply is generating you can leave the chat, open the list, or lock the app — generation continues in the background. When it finishes, you'll see a checkmark on the chat in the list and may get a notification.

                    Tap Stop in the composer to cancel an in-progress reply. Tap your own message to open a prompt bar with edit and copy. Assistant replies show copy, share, and regenerate controls.
                """.trimIndent(),
            )

            HelpSection(
                title = "Composer and sending messages",
                body = """
                    Type in the input bar at the bottom and tap send. The send button appears when there is text or attachments.

                    Attach photos from your gallery or camera, or attach files. Images are sent to vision-capable models as inline content. Other files are referenced by name in your prompt.

                    The search toggle enables web search for supported chat models. When enabled, the model can look up current information before answering. You can choose in Settings whether search stays on after each message.

                    The brain toggle controls extended reasoning on models that support it. Reasoning effort and whether reasoning is shown in the reply can be adjusted per model in the reasoning settings sheet.

                    The model chip opens the model picker. The system-message button opens instructions that apply to the whole chat (for example, tone, format, or role-play rules).
                """.trimIndent(),
            )

            HelpSection(
                title = "Model picker",
                body = """
                    Open the model selector from the composer to browse OpenRouter models. Use filter badges to narrow by capabilities — chat, vision, image generation, video, speech, transcription, and more.

                    Model prices and context lengths are shown where available. Your last selected model is remembered per device.

                    Different model types behave differently:
                    • Chat models stream text (and optional reasoning) replies.
                    • Image models generate images from your prompt.
                    • Video models submit async jobs and return clips when ready.
                    • TTS / voice models synthesize speech instead of returning plain text.
                    • Transcription models convert recorded audio to text.
                """.trimIndent(),
            )

            HelpSection(
                title = "Voice input and voice replies",
                body = """
                    Hold the microphone button to record a voice note, or enable the transcription button in Settings to dictate into the text field.

                    When a voice-capable (TTS) model is selected, Convo can speak responses. In Settings → Voice on voice models, choose:

                    • Speak my words — reads back what you typed or said, in the model's voice.
                    • Conversation — a separate reply model generates a text answer first, then the voice model speaks it.

                    Pick transcription and reply models for conversation mode. You can show voice replies as text first (with a button to switch to audio), and optionally auto-play new voice replies.

                    Per-model voice selection is available from the voice picker when a TTS model is active.
                """.trimIndent(),
            )

            HelpSection(
                title = "Assistant replies",
                body = """
                    Text replies stream in as they are generated. Models with reasoning may show a thinking section you can expand; duration is displayed when available.

                    Web search runs show a timeline of search steps and sources when the model uses search.

                    Use the repeat control on an assistant message to regenerate a response. Multiple variants are kept — swipe horizontally or use the variant pager to compare them.

                    Image and video replies appear inline with viewers. Audio replies show a playback row; you can download clips or switch between audio and transcript views.

                    Markdown formatting, code blocks, and inline images in replies are rendered in the message bubble.
                """.trimIndent(),
            )

            HelpSection(
                title = "Projects",
                body = """
                    From the chat list, open Projects to organize chats into folders. Create a project, name it, and assign chats from the list or project view.

                    Projects help group related conversations — for example, work, research, or creative writing — without mixing them in the main list.
                """.trimIndent(),
            )

            HelpSection(
                title = "Chat list tools",
                body = """
                    Search chats by title from the list header. Rename or delete chats from their overflow menu.

                    Pin important chats so they stay at the top. Archive chats you want out of the main list without deleting them.

                    The quick-settings dock (configurable in Settings) gives one-tap access to shortcuts like new chat, lock app, credits flash, web search persistence, and voice settings.
                """.trimIndent(),
            )

            HelpSection(
                title = "Settings — API key and credits",
                body = """
                    Open Settings from the cog on the chat list. View or replace your OpenRouter API key; saving verifies it against the models API.

                    The Credits section shows remaining balance and usage for your key. Open openrouter.ai/logs for detailed request history on the OpenRouter site.
                """.trimIndent(),
            )

            HelpSection(
                title = "Settings — AI requests",
                body = """
                    Request timeout — how long Convo waits for an AI response before giving up (1–45 minutes, default 5). Longer timeouts help slow models or deep reasoning jobs; shorter ones fail faster if something stalls.

                    Max tokens — caps how long the model's reply can be (default 12,000). OpenRouter bills by tokens; lowering this can reduce cost on verbose models. The value is sent as max_tokens on chat completion requests.
                """.trimIndent(),
            )

            HelpSection(
                title = "Settings — security and composer",
                body = """
                    Biometric lock requires fingerprint, face unlock, or device PIN when reopening Convo after you leave the app. Set up device authentication in system settings first.

                    Composer options control whether the dictation/transcription shortcut appears in the input bar, and whether scroll-to-top / scroll-to-bottom buttons appear while reading a chat.

                    Web search persistence controls whether the search toggle in the composer resets after you send a message.
                """.trimIndent(),
            )

            HelpSection(
                title = "Settings — quick settings dock",
                body = """
                    Add, remove, and reorder shortcuts that appear on the chat list. Drag handles reorder items; tap + to add from the catalogue of available settings.

                    Each shortcut mirrors a full setting — for example, toggling keep-search-on from the dock updates the same preference as in Settings.
                """.trimIndent(),
            )

            HelpSection(
                title = "App lock and notifications",
                body = """
                    With biometric lock enabled, leaving Convo locks it until you authenticate. You can also lock immediately from a quick-settings shortcut.

                    Notifications alert you when a reply finishes while you are in another chat or outside the app. Tapping a notification opens that chat. Reply-in-notification may require the notification permission on Android 13+.
                """.trimIndent(),
            )

            HelpSection(
                title = "Composer hints",
                body = """
                    The first time you use certain composer features, small hint popups explain what they do. These appear once per feature and can be dismissed. They are separate from this help guide.
                """.trimIndent(),
            )

            HelpSection(
                title = "Tips and troubleshooting",
                body = """
                    If replies fail, check your API key, OpenRouter credits, and that the chosen model supports what you asked (vision for images, etc.).

                    Empty or cut-off replies may mean max tokens is too low for the task — raise it in Settings if you need longer answers.

                    Timeout errors mean the model did not finish within your request timeout — try a longer limit or a faster model.

                    For billing questions, use openrouter.ai and your key's usage logs. Convo does not store conversation data in the cloud; chats are saved locally on your device.
                """.trimIndent(),
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun HelpHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = SettingsHeaderHorizontalPadding,
                end = SettingsHeaderHorizontalPadding,
                top = SettingsHeaderVerticalPadding,
                bottom = SettingsHeaderVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConvoIconButton(
            painter = ConvoIcons.ArrowLeft(),
            contentDescription = "Back to settings",
            onClick = onBack,
        )
        Text(
            text = "Help",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = (-0.2).sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = SettingsTitleStartGap, end = ConvoIconButtonGap),
        )
    }
}

@Composable
private fun HelpSection(
    title: String,
    body: String,
) {
    if (title != "Getting started") {
        SettingsSectionDivider()
    }
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.2).sp,
        ),
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(8.dp))
}
