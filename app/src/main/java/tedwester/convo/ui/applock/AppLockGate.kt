package tedwester.convo.ui.applock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import tedwester.convo.core.security.AppLockManager

private const val LockRevealExitMs = 260

private const val LockRevealEnterMs = 380
private const val LockRevealEnterDelayMs = 90

@Composable
fun AppLockGate(
    locked: Boolean,
    appLockManager: AppLockManager,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val enterOffsetPx = with(density) { 40.dp.toPx() }

    val chatAlpha by animateFloatAsState(
        targetValue = if (locked) 0f else 1f,
        animationSpec = if (locked) {
            snap()
        } else {
            tween(
                durationMillis = LockRevealEnterMs,
                delayMillis = LockRevealEnterDelayMs,
                easing = FastOutSlowInEasing,
            )
        },
        label = "appLockChatAlpha",
    )
    val chatTranslationY by animateFloatAsState(
        targetValue = if (locked) enterOffsetPx else 0f,
        animationSpec = if (locked) {
            snap()
        } else {
            tween(
                durationMillis = LockRevealEnterMs,
                delayMillis = LockRevealEnterDelayMs,
                easing = FastOutSlowInEasing,
            )
        },
        label = "appLockChatTranslation",
    )

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = chatAlpha
                    translationY = chatTranslationY
                },
        ) {
            content()
        }

        AnimatedVisibility(
            visible = locked,
            enter = EnterTransition.None,
            exit = fadeOut(
                tween(LockRevealExitMs, easing = FastOutSlowInEasing),
            ) + slideOutVertically(
                tween(LockRevealExitMs, easing = FastOutSlowInEasing),
            ) { fullHeight -> -fullHeight / 52 },
            modifier = Modifier.fillMaxSize(),
        ) {
            AppLockScreen(
                appLockManager = appLockManager,
                onUnlocked = onUnlocked,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
