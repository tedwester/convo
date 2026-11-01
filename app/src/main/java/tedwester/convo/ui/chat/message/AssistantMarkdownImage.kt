package tedwester.convo.ui.chat.message

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import tedwester.convo.ui.icons.ConvoIcons

private val InlineImageShape = RoundedCornerShape(14.dp)

/**
 * Assistant reply image rendered as a native Coil card instead of going
 * through the markdown renderer's inline-image path. Sizes are real Compose
 * dp throughout; loading and failure show bounded, legible states rather
 * than an empty gap.
 */
@Composable
internal fun AssistantMarkdownImage(
    url: String,
    modifier: Modifier = Modifier,
) {
    var retryToken by remember(url) { mutableIntStateOf(0) }
    val sourceLabel = remember(url) { describeImageSource(url) }
    key(retryToken) {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            modifier = modifier
                .fillMaxWidth()
                .clip(InlineImageShape)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    shape = InlineImageShape,
                ),
            contentScale = ContentScale.Fit,
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(InlineImageShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = ConvoIcons.Images(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp),
                    )
                }
            },
            error = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(InlineImageShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { retryToken += 1 }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = ConvoIcons.Images(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        Text(
                            text = "Couldn't load image — tap to retry",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                        Text(
                            text = sourceLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        )
                    }
                }
            },
        )
    }
}

private fun describeImageSource(url: String): String {
    if (url.startsWith("data:", ignoreCase = true)) {
        val kb = url.length / 1024
        return if (kb >= 1024) {
            "embedded image (${kb / 1024} MB)"
        } else {
            "embedded image ($kb KB)"
        }
    }
    return url.substringAfter("://", url).substringBefore('/').takeIf { it.isNotBlank() } ?: "image"
}
