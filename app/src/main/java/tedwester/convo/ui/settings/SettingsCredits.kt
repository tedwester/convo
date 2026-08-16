package tedwester.convo.ui.settings

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tedwester.convo.core.network.model.OpenRouterKeyInfo
import java.util.Locale

@Composable
internal fun CreditsCardSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(4) {
            CreditRowSkeleton()
        }
    }
}

@Composable
internal fun CreditRowSkeleton() {
    val pulse = rememberSkeletonPulse()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBar(
            widthFraction = 0.34f,
            height = 14.dp,
            alpha = pulse,
        )
        SkeletonBar(
            widthFraction = 0.22f,
            height = 14.dp,
            alpha = pulse,
        )
    }
}

@Composable
internal fun SkeletonBar(
    widthFraction: Float,
    height: Dp,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = alpha)),
    )
}

@Composable
internal fun rememberSkeletonPulse(): Float {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton-alpha",
    )
    return alpha
}

@Composable
internal fun CreditsCard(info: OpenRouterKeyInfo) {
    val remaining = info.creditsRemaining
    val remainingLabel = when {
        info.accountCreditsRemaining != null -> "Credits left"
        info.limitRemaining != null -> "Key credits left"
        else -> "Credits left"
    }
    val remainingValue = when {
        remaining != null -> formatUsd(remaining)
        info.limit == null -> "No key limit set"
        else -> "Unavailable"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!info.label.isNullOrBlank()) {
            CreditRow(label = "Label", value = info.label)
        }
        CreditRow(label = remainingLabel, value = remainingValue)
        if (info.totalUsage != null) {
            CreditRow(label = "Account usage", value = formatUsd(info.totalUsage))
        }
        CreditRow(label = "Key usage", value = formatUsd(info.usage))
        if (info.limit != null) {
            CreditRow(label = "Key limit", value = formatUsd(info.limit))
        }
        if (remaining == null && info.limit == null) {
            Text(
                text = "Account balance isn't available for this key. " +
                    "Set a spending limit on the key in OpenRouter, or check your balance at openrouter.ai.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
internal fun CreditRow(label: String, value: String) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

internal fun formatUsd(amount: Double): String =
    String.format(Locale.US, "$%.4f", amount)
