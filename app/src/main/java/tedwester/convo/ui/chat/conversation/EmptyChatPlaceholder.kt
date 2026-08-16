package tedwester.convo.ui.chat.conversation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val PlaceholderFadeMs = 280

@Composable
fun EmptyChatPlaceholder(
    modelName: String?,
    visible: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(PlaceholderFadeMs, easing = FastOutSlowInEasing),
        label = "emptyChatPlaceholderAlpha",
    )

    Column(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Ask Anything",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onBackground,
            ),
        )
        Text(
            text = modelName?.let { "Chatting with $it" } ?: "Select a model to get started",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
