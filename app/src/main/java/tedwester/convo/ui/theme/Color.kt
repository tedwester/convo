package tedwester.convo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Accent = Color(0xFF5671F5)
val FabTeal = Color(0xFF78BFC8)
val FabTealOn = Color(0xFF111111)

val DarkBackground = Color(0xFF171615)
val DarkModal = Color(0xFF1E1D1B)
val DarkSurface = DarkModal
val DarkField = DarkModal
val DarkSurfaceHigh = Color(0xFF262524)
val DarkIconButton = Color(0xFF2A2A2A)
val DarkOnBackground = Color(0xFFE7EAF0)
val DarkOnSurface = Color(0xFF9AA3B1)
val DarkUserBubble = Color(0xFF4258CE)
val DarkUserBubbleOn = Color(0xFFFFFFFF)
val DarkAssistantBubble = Color(0xFF262524)
val DarkAssistantBubbleOn = Color(0xFFD0D4DB)
val DarkOutline = Color(0xFF353432)
val DarkFieldPlaceholder = Color(0xFF8A8A8A)
val DarkFieldOutline = Color.White.copy(alpha = 0.08f)

val LightBackground = Color(0xFFF5F6FA)
val LightSurface = Color(0xFFFFFFFF)
val LightModal = LightSurface
val LightField = Color(0xFFEEF0F5)
val LightSurfaceHigh = LightField
val LightIconButton = Color(0xFFE4E6EC)
val LightOnBackground = Color(0xFF1A1D23)
val LightOnSurface = Color(0xFF5A6472)
val LightUserBubble = Color(0xFF4359CE)
val LightUserBubbleOn = Color(0xFFFFFFFF)
val LightAssistantBubble = Color(0xFFEDEFF4)
val LightAssistantBubbleOn = Color(0xFF3A4049)
val LightOutline = Color(0xFFE2E5EB)
val LightFieldPlaceholder = Color(0xFF9AA3B1)
val LightFieldOutline = Color.Black.copy(alpha = 0.08f)

val ConvoDestructive = Color(0xFFE07A5F)

val DarkChatBox = DarkModal
val DarkChatBoxIcon = Color(0xFF8E99A8)
val LightChatBox = Color(0xFFE2E5EA)
val LightChatBoxIcon = Color(0xFF5A6472)

object ConvoModalTokens {
    const val AnimMs = 380
    const val KeyboardCloseLeadMs = 180
    val CornerRadius = 22.dp
    val HandleWidth = 36.dp
    val HandleHeight = 4.dp
    val ScrimAlpha = 0.45f
    val ContentHorizontalPadding = 18.dp
    val ActionHorizontalInset = 6.dp
}

object ConvoFieldTokens {
    val CornerRadius = 10.dp
    val SearchCornerRadius = 20.dp
    val SingleLineHeight = 44.dp
    val HorizontalPadding = 14.dp
    val MultilineVerticalPadding = 12.dp
    val OutlineWidth = 0.5.dp
}

object ConvoSearchHeaderTokens {
    const val ExpandMs = 125
    const val ContentStartMs = 55
    const val ContentFadeMs = 170
    const val BackDelayMs = 130
    const val BackSlideMs = 180
    const val BackSlideExitMs = 100
    val BackSlotWidth = 36.dp
    val BackGap = 4.dp
}

@Composable
fun convoModalSurface(): Color =
    if (isSystemInDarkTheme()) DarkModal else LightModal

@Composable
fun convoFieldFill(): Color =
    if (isSystemInDarkTheme()) DarkField else LightField

@Composable
fun convoFieldOutline(): Color =
    if (isSystemInDarkTheme()) DarkFieldOutline else LightFieldOutline

@Composable
fun convoFieldPlaceholder(): Color =
    if (isSystemInDarkTheme()) DarkFieldPlaceholder else LightFieldPlaceholder

@Composable
fun convoAssistantTextColor(): Color =
    if (isSystemInDarkTheme()) DarkAssistantBubbleOn else LightAssistantBubbleOn
