package tedwester.convo.ui.chat.message

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownAnimations
import com.mikepenz.markdown.model.markdownPadding
import com.mikepenz.markdown.model.rememberMarkdownState
import tedwester.convo.ui.theme.AssistantSerifFamily
import tedwester.convo.ui.theme.convoAssistantTextColor

/** Text-only markdown. Images are rendered by [AssistantTurnText] via Coil. */
@Composable
internal fun AssistantMarkdown(
    content: String,
    modifier: Modifier = Modifier,
) {
    if (content.isBlank()) return

    val linkColor = MaterialTheme.colorScheme.primary
    val bodyColor = convoAssistantTextColor()
    val muted = MaterialTheme.colorScheme.onSurface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outline

    val serifBody = remember(bodyColor) {
        TextStyle(
            fontFamily = AssistantSerifFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 15.5.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.1.sp,
            color = bodyColor,
        )
    }
    val typography = markdownTypography(
        h1 = serifBody.copy(fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold),
        h2 = serifBody.copy(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
        h3 = serifBody.copy(fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
        h4 = serifBody.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        h5 = serifBody.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
        h6 = serifBody.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
        text = serifBody,
        paragraph = serifBody,
        quote = serifBody.copy(color = muted),
        ordered = serifBody,
        bullet = serifBody,
        list = serifBody,
        code = serifBody.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        ),
        inlineCode = serifBody.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
        ),
        table = serifBody.copy(fontSize = 14.sp, lineHeight = 21.sp),
        textLink = TextLinkStyles(
            style = serifBody.toSpanStyle().copy(
                color = linkColor,
                textDecoration = TextDecoration.Underline,
            ),
        ),
    )
    val colors = markdownColor(
        text = bodyColor,
        codeBackground = surfaceVariant.copy(alpha = 0.65f),
        inlineCodeBackground = surfaceVariant.copy(alpha = 0.55f),
        dividerColor = outline.copy(alpha = 0.45f),
        tableBackground = surfaceVariant.copy(alpha = 0.35f),
    )
    val padding = markdownPadding(
        block = 6.dp,
        list = 4.dp,
        listItemTop = 2.dp,
        listItemBottom = 2.dp,
        listIndent = 10.dp,
        codeBlock = PaddingValues(10.dp),
        blockQuote = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
    )

    val markdownState = rememberMarkdownState(
        content = content,
        retainState = true,
    )

    Markdown(
        markdownState = markdownState,
        colors = colors,
        typography = typography,
        padding = padding,
        animations = markdownAnimations(animateTextSize = { this }),
        imageTransformer = ConvoMarkdownImageTransformer,
        modifier = modifier.fillMaxWidth(),
    )
}
