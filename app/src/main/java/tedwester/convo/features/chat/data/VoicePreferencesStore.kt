package tedwester.convo.features.chat.data

import android.content.Context
import android.content.SharedPreferences
import tedwester.convo.core.network.model.OpenRouterModel

enum class VoiceTtsMode {

    SpeakMyWords,

    Conversation,
}

data class VoicePreferences(
    val mode: VoiceTtsMode = VoiceTtsMode.SpeakMyWords,

    val transcriptionModelId: String = DEFAULT_TRANSCRIPTION_MODEL,

    val replyModelId: String = DEFAULT_REPLY_MODEL,

    val showVoiceRepliesAsTextFirst: Boolean = false,

    val autoPlayVoiceReplies: Boolean = false,
) {
    companion object {
        const val DEFAULT_TRANSCRIPTION_MODEL = "openai/whisper-large-v3"
        const val DEFAULT_REPLY_MODEL = "openai/gpt-4o-mini"

        const val CONVERSATION_REPLY_SYSTEM_MESSAGE =
            "Optimize for verbal conversation, keep your reply to about 100 words and stay concise. " +
                "Do not use markdown — your response will be spoken aloud in a voice conversation."

        fun buildConversationReplySystemMessage(
            voiceModel: OpenRouterModel,
            replyModelId: String,
            replyModel: OpenRouterModel?,
            voiceId: String?,
        ): String {
            val voiceSuffix = voiceModel.voiceDisplayLabel(voiceId)
                .takeIf { it != "Default" }
                ?.let { " (voice: $it)" }
                .orEmpty()
            if (voiceModel.usesIntegratedConversationReply) {
                return buildString {
                    append(CONVERSATION_REPLY_SYSTEM_MESSAGE)
                    append("\n\nContext: You are ")
                    append(voiceModel.name)
                    append(" (")
                    append(voiceModel.id)
                    append("). The user is in a live voice conversation in this app. ")
                    append("Your reply will be spoken aloud in your own voice")
                    append(voiceSuffix)
                    append(". You are both the reply model and the voice the user hears — ")
                    append("there is no separate text or voice model behind you.")
                }
            }
            val reply = replyModel ?: OpenRouterModel(id = replyModelId, name = replyModelId)
            return buildString {
                append(CONVERSATION_REPLY_SYSTEM_MESSAGE)
                append("\n\nContext: You are ")
                append(reply.name)
                append(" (")
                append(reply.id)
                append("), the text reply model in this app. Your response will be converted ")
                append("to speech by a separate voice model the user selected: ")
                append(voiceModel.name)
                append(" (")
                append(voiceModel.id)
                append(")")
                append(voiceSuffix)
                append(". You are not the voice the user hears — you only write the words ")
                append("that ")
                append(voiceModel.name)
                append(" will speak.")
            }
        }
    }

    fun hasVoicePath(voiceModel: OpenRouterModel? = null): Boolean {
        val hasStt = transcriptionModelId.isNotBlank() ||
            voiceModel?.transcribesAudioNatively == true
        if (!hasStt) return false
        if (mode != VoiceTtsMode.Conversation) return true
        return replyModelId.isNotBlank() || voiceModel?.usesIntegratedConversationReply == true
    }

    val hasVoicePath: Boolean
        get() = hasVoicePath(voiceModel = null)
}

class VoicePreferencesStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): VoicePreferences {
        val legacyTextOnly = prefs.getBoolean(KEY_TRANSCRIBE_VOICE_REPLIES, false)
        return VoicePreferences(
            mode = runCatching {
                VoiceTtsMode.valueOf(
                    prefs.getString(KEY_MODE, null) ?: VoiceTtsMode.SpeakMyWords.name,
                )
            }.getOrDefault(VoiceTtsMode.SpeakMyWords),
            transcriptionModelId = prefs.getString(
                KEY_STT_MODEL,
                VoicePreferences.DEFAULT_TRANSCRIPTION_MODEL,
            ) ?: VoicePreferences.DEFAULT_TRANSCRIPTION_MODEL,
            replyModelId = prefs.getString(
                KEY_REPLY_MODEL,
                VoicePreferences.DEFAULT_REPLY_MODEL,
            ) ?: VoicePreferences.DEFAULT_REPLY_MODEL,
            showVoiceRepliesAsTextFirst = prefs.getBoolean(
                KEY_SHOW_VOICE_REPLIES_AS_TEXT_FIRST,
                legacyTextOnly,
            ),
            autoPlayVoiceReplies = prefs.getBoolean(KEY_AUTO_PLAY_VOICE_REPLIES, false),
        )
    }

    fun save(value: VoicePreferences) {
        prefs.edit()
            .putString(KEY_MODE, value.mode.name)
            .putString(KEY_STT_MODEL, value.transcriptionModelId.trim())
            .putString(KEY_REPLY_MODEL, value.replyModelId.trim())
            .putBoolean(KEY_SHOW_VOICE_REPLIES_AS_TEXT_FIRST, value.showVoiceRepliesAsTextFirst)
            .putBoolean(KEY_AUTO_PLAY_VOICE_REPLIES, value.autoPlayVoiceReplies)
            .apply()
    }

    private companion object {
        const val PREFS = "convo_voice_prefs"
        const val KEY_MODE = "tts_mode"
        const val KEY_STT_MODEL = "stt_model"
        const val KEY_REPLY_MODEL = "reply_model"
        const val KEY_TRANSCRIBE_VOICE_REPLIES = "transcribe_voice_replies"
        const val KEY_SHOW_VOICE_REPLIES_AS_TEXT_FIRST = "show_voice_replies_as_text_first"
        const val KEY_AUTO_PLAY_VOICE_REPLIES = "auto_play_voice_replies"
    }
}
