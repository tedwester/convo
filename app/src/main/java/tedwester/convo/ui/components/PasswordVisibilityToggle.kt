package tedwester.convo.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val EyeSlashAnimMs = PasswordRevealAnimMs

private val EyeOutlinePathData =
    "M2.062,12.348a1,1 0,0 1,0 -0.696 10.75,10.75 0,0 1,19.876 0 1,1 0,0 1,0 0.696 10.75,10.75 0,0 1,-19.876 0"

/**
 * Eye toggle for password fields — Lucide-style eye with an animated slash line.
 * Matches muted field icon styling ([ConvoTextField] leading icons).
 *
 * @param visible `true` when the secret text is shown (slash drawn).
 */
@Composable
fun PasswordVisibilityToggle(
    visible: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconSize: Dp = 18.dp,
    contentColor: Color? = null,
) {
    val description = if (visible) "Hide key" else "Show key"
    val iconColor = contentColor ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    val tint = if (enabled) iconColor else iconColor.copy(alpha = 0.38f)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(iconSize)
            .semantics { contentDescription = description }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onToggle,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibilityEyeIcon(
            visible = visible,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun AnimatedVisibilityEyeIcon(
    visible: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val slashProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(EyeSlashAnimMs, easing = FastOutSlowInEasing),
        label = "eyeSlash",
    )
    val eyeAlpha by animateFloatAsState(
        targetValue = if (visible) 0.42f else 1f,
        animationSpec = tween(EyeSlashAnimMs, easing = FastOutSlowInEasing),
        label = "eyeAlpha",
    )

    val eyeOutline = rememberEyeOutlinePath()

    Canvas(modifier = modifier) {
        val scale = size.minDimension / 24f
        val stroke = 0.75f * scale
        val strokeStyle = Stroke(
            width = stroke,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        val eyeColor = tint.copy(alpha = tint.alpha * eyeAlpha)

        withTransform({
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
        }) {
            drawPath(eyeOutline, eyeColor, style = strokeStyle)
            drawCircle(
                color = eyeColor,
                radius = 3f,
                center = Offset(12f, 12f),
                style = strokeStyle,
            )
        }

        if (slashProgress > 0f) {
            val start = Offset(2f * scale, 2f * scale)
            val end = Offset(22f * scale, 22f * scale)
            val slashEnd = Offset(
                x = start.x + (end.x - start.x) * slashProgress,
                y = start.y + (end.y - start.y) * slashProgress,
            )
            drawLine(
                color = tint,
                start = start,
                end = slashEnd,
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun rememberEyeOutlinePath(): Path =
    remember {
        PathParser().parsePathString(EyeOutlinePathData).toPath()
    }
