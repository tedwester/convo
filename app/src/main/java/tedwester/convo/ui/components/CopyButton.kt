package tedwester.convo.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tedwester.convo.ui.icons.ConvoIcons

private const val CopyIconAnimMs = 220
private const val CopySuccessHoldMs = 2_000L

private enum class CopyIconState {
    Copy,
    Copied,
}

/**
 * Copy action with a brief success state: smoothly morphs into circle-check,
 * holds for a moment, then returns to the copy icon.
 */
@Composable
fun CopyButton(
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var iconState by remember { mutableStateOf(CopyIconState.Copy) }
    var copyPulse by remember { mutableIntStateOf(0) }

    LaunchedEffect(copyPulse) {
        if (copyPulse == 0) return@LaunchedEffect
        iconState = CopyIconState.Copied
        delay(CopySuccessHoldMs)
        iconState = CopyIconState.Copy
    }

    val tint = MaterialTheme.colorScheme.onSurface.copy(
        alpha = if (enabled) 0.75f else 0.3f,
    )

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) {
                onCopy()
                copyPulse++
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = iconState,
            transitionSpec = {
                when {
                    initialState == CopyIconState.Copy && targetState == CopyIconState.Copied -> {
                        (
                            fadeIn(tween(CopyIconAnimMs, easing = FastOutSlowInEasing)) +
                                scaleIn(
                                    initialScale = 0.65f,
                                    animationSpec = spring(
                                        dampingRatio = 0.72f,
                                        stiffness = 420f,
                                    ),
                                )
                            ) togetherWith (
                            fadeOut(tween(CopyIconAnimMs / 2, easing = FastOutSlowInEasing)) +
                                scaleOut(
                                    targetScale = 0.75f,
                                    animationSpec = tween(CopyIconAnimMs / 2),
                                )
                            )
                    }
                    initialState == CopyIconState.Copied && targetState == CopyIconState.Copy -> {
                        (
                            fadeIn(tween(CopyIconAnimMs, easing = FastOutSlowInEasing)) +
                                scaleIn(
                                    initialScale = 0.75f,
                                    animationSpec = tween(CopyIconAnimMs, easing = FastOutSlowInEasing),
                                )
                            ) togetherWith (
                            fadeOut(tween(CopyIconAnimMs / 2, easing = FastOutSlowInEasing)) +
                                scaleOut(
                                    targetScale = 0.65f,
                                    animationSpec = tween(CopyIconAnimMs / 2),
                                )
                            )
                    }
                    else -> fadeIn(tween(160)) togetherWith fadeOut(tween(120))
                }.using(SizeTransform(clip = false) { _, _ -> snap() })
            },
            label = "copyButtonIcon",
        ) { state ->
            val (painter, description) = when (state) {
                CopyIconState.Copy -> ConvoIcons.Copy() to "Copy"
                CopyIconState.Copied -> ConvoIcons.CircleCheck() to "Copied"
            }
            Icon(
                painter = painter,
                contentDescription = description,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
