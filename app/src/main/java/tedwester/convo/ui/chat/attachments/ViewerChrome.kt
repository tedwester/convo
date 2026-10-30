package tedwester.convo.ui.chat.attachments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal const val ViewerMinScale = 1f
internal const val ViewerMaxScale = 5f
internal const val ViewerStepScale = 0.5f

internal class ViewerZoom {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    fun zoomedIn(): Boolean = scale > 1f

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    fun zoomTo(value: Float) {
        scale = value.coerceIn(ViewerMinScale, ViewerMaxScale)
        if (scale <= 1f) offset = Offset.Zero
    }

    fun step(direction: Float) {
        zoomTo(scale + direction * ViewerStepScale)
    }

    fun applyZoom(zoomChange: Float) {
        zoomTo(scale * zoomChange)
    }

    fun applyPan(panChange: Offset, boundX: Float, boundY: Float) {
        if (scale <= 1f) {
            offset = Offset.Zero
            return
        }
        offset = Offset(
            x = (offset.x + panChange.x).coerceIn(-boundX, boundX),
            y = (offset.y + panChange.y).coerceIn(-boundY, boundY),
        )
    }
}

@Composable
internal fun rememberViewerZoom(key: Any?): ViewerZoom = remember(key) { ViewerZoom() }

internal fun Modifier.viewerZoomGraphics(zoom: ViewerZoom): Modifier = graphicsLayer {
    scaleX = zoom.scale
    scaleY = zoom.scale
    translationX = zoom.offset.x
    translationY = zoom.offset.y
}

@Composable
internal fun ViewerRoundButton(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
internal fun ViewerZoomStep(
    label: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
            ),
            color = Color.White.copy(alpha = if (enabled) 0.9f else 0.3f),
        )
    }
}

@Composable
internal fun ViewerZoomBar(
    scale: Float,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 20.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ViewerZoomStep(
            label = "−",
            contentDescription = "Zoom out",
            enabled = scale > 1f,
            onClick = onZoomOut,
        )
        Text(
            text = "${(scale * 100).toInt()}%",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            ),
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onReset,
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        ViewerZoomStep(
            label = "+",
            contentDescription = "Zoom in",
            enabled = scale < ViewerMaxScale,
            onClick = onZoomIn,
        )
    }
}
