package tedwester.convo.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

const val PasswordRevealAnimMs = 320

@Composable
fun rememberAnimatedPasswordReveal(
    visible: Boolean,
    animateReveal: Boolean = true,
): AnimatedPasswordReveal {
    val revealProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (animateReveal) {
            tween(PasswordRevealAnimMs, easing = FastOutSlowInEasing)
        } else {
            snap()
        },
        label = "passwordReveal",
    )

    val masked = if (animateReveal) revealProgress < 0.5f else !visible
    val visualTransformation = if (masked) {
        PasswordVisualTransformation()
    } else {
        VisualTransformation.None
    }

    return AnimatedPasswordReveal(
        visualTransformation = visualTransformation,
        textAlpha = if (animateReveal) revealTextAlpha(revealProgress) else 1f,
    )
}

data class AnimatedPasswordReveal(
    val visualTransformation: VisualTransformation,
    val textAlpha: Float,
)

private fun revealTextAlpha(revealProgress: Float): Float {
    if (revealProgress <= 0f || revealProgress >= 1f) return 1f
    return if (revealProgress < 0.5f) {
        1f - revealProgress * 2f
    } else {
        (revealProgress - 0.5f) * 2f
    }
}
