package tedwester.convo.ui.chat.message

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tedwester.convo.ui.theme.InterFontFamily

/**
 * The n/m variant counter shared by assistant replies and user messages. One composable so
 * both read identically and animate the same way when the index changes.
 */
@Composable
internal fun VariantCounter(
    index: Int,
    total: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = index to total,
        transitionSpec = {
            (fadeIn(tween(PagerAnimMs, easing = FastOutSlowInEasing)) togetherWith
                fadeOut(tween(PagerAnimMs / 2, easing = FastOutSlowInEasing)))
                .using(SizeTransform(clip = false) { _, _ -> snap() })
        },
        contentAlignment = Alignment.Center,
        label = "variant-counter",
        modifier = modifier,
    ) { (current, count) ->
        Text(
            text = "$current/$count",
            style = TextStyle(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                letterSpacing = 0.02.sp,
                color = color,
            ),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
