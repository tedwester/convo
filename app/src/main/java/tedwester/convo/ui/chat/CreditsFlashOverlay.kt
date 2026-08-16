package tedwester.convo.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import tedwester.convo.core.network.model.OpenRouterKeyInfo
import tedwester.convo.ui.settings.formatUsd

private const val FadeMs = 240
private const val VisibleMs = 2_400L

sealed interface CreditsFlashContent {
    data class Credits(
        val label: String,
        val amount: String,
    ) : CreditsFlashContent

    data class Message(
        val text: String,
    ) : CreditsFlashContent
}

internal fun creditsFlashContentFrom(info: OpenRouterKeyInfo): CreditsFlashContent {
    val remaining = info.creditsRemaining
    return when {
        remaining != null -> CreditsFlashContent.Credits(
            label = when {
                info.accountCreditsRemaining != null -> "Credits left"
                info.limitRemaining != null -> "Key credits left"
                else -> "Credits left"
            },
            amount = formatUsd(remaining),
        )
        info.limit == null -> CreditsFlashContent.Message("No credit limit set on this key.")
        else -> CreditsFlashContent.Message("Credits unavailable for this key.")
    }
}

@Composable
internal fun CreditsFlashOverlay(
    content: CreditsFlashContent?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var displayed by remember { mutableStateOf<CreditsFlashContent?>(null) }
    val alpha = remember { Animatable(0f) }
    var dismissSignal by remember { mutableIntStateOf(0) }

    LaunchedEffect(content) {
        val flash = content ?: return@LaunchedEffect
        displayed = flash
        val dismissAtStart = dismissSignal
        alpha.snapTo(0f)
        alpha.animateTo(1f, animationSpec = tween(FadeMs))
        try {
            withTimeout(VisibleMs) {
                snapshotFlow { dismissSignal }.first { it != dismissAtStart }
            }
        } catch (_: TimeoutCancellationException) {
        }
        alpha.animateTo(0f, animationSpec = tween(FadeMs))
        displayed = null
        onDismiss()
    }

    val card = displayed ?: return

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(displayed) {
                detectTapGestures { dismissSignal++ }
            },
        contentAlignment = Alignment.Center,
    ) {
        CreditsFlashCard(
            content = card,
            modifier = Modifier.graphicsLayer { this.alpha = alpha.value },
        )
    }
}

@Composable
private fun CreditsFlashCard(
    content: CreditsFlashContent,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val fill = if (dark) {
        Color(0xFF1E1D1B)
    } else {
        Color.White
    }
    val ring = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .size(240.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, ring, shape)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (content) {
            is CreditsFlashContent.Credits -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = content.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = content.amount,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 34.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            is CreditsFlashContent.Message -> {
                Text(
                    text = content.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
