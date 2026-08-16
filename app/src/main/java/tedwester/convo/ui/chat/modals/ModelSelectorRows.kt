package tedwester.convo.ui.chat.modals

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tedwester.convo.core.network.model.ModelKind
import tedwester.convo.core.network.model.OpenRouterModel
import java.util.Locale
@Composable
internal fun ModelRow(
    model: OpenRouterModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
                } else {
                    Color.Transparent
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = model.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = modelMetaLine(model),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

internal fun modelMetaLine(model: OpenRouterModel): String {
    val bits = buildList {
        add(model.authorLabel)
        when (model.modelKind) {
            ModelKind.Tts -> add("Voice")
            ModelKind.ImageGen -> add("Image")
            ModelKind.VideoGen -> add("Video")
            ModelKind.Embedding -> add("Embeddings")
            ModelKind.Rerank -> add("Rerank")
            ModelKind.Transcription -> add("Transcription")
            ModelKind.Chat -> Unit
        }
        when {
            model.isFree -> add("Free")
            model.promptPricePerMillion != null -> {
                add(formatPricePerMillion(model.promptPricePerMillion!!))
            }
        }
        model.contextLength?.let { tokens ->
            if (tokens >= 1_000_000) add("${tokens / 1_000_000}M ctx")
            else if (tokens >= 1_000) add("${tokens / 1_000}k ctx")
        }
        if (model.hasImageInput) add("Vision")
    }
    return bits.joinToString(" · ")
}

internal fun formatPricePerMillion(price: Double): String {
    return when {
        price < 0.01 -> String.format(Locale.US, "$%.4f/M", price)
        price < 1.0 -> String.format(Locale.US, "$%.3f/M", price)
        else -> String.format(Locale.US, "$%.2f/M", price)
    }
}

@Composable
internal fun ModelRowSkeleton() {
    val pulse = rememberModelSkeletonPulse()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SkeletonLine(
                widthFraction = 0.62f,
                height = 14.dp,
                alpha = pulse,
            )
            SkeletonLine(
                widthFraction = 0.42f,
                height = 11.dp,
                alpha = pulse * 0.85f,
            )
        }
    }
}

@Composable
internal fun SkeletonLine(
    widthFraction: Float,
    height: Dp,
    alpha: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = alpha)),
    )
}

@Composable
internal fun rememberModelSkeletonPulse(): Float {
    val transition = rememberInfiniteTransition(label = "model-skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "model-skeleton-alpha",
    )
    return alpha
}

