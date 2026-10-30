package tedwester.convo.ui.chat.attachments

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import tedwester.convo.features.chat.model.ChatAttachment
import tedwester.convo.ui.chat.message.saveAttachmentToDownloads
import tedwester.convo.ui.icons.ConvoIcons

private const val ViewerMinScale = 1f
private const val ViewerMaxScale = 5f
private const val ViewerDoubleTapScale = 3f
private const val ViewerStepScale = 0.5f

@Composable
fun ImageViewerDialog(
    attachment: ChatAttachment,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val maxW = with(density) { config.screenWidthDp.dp.roundToPx().coerceAtLeast(1) }
    val maxH = with(density) { config.screenHeightDp.dp.roundToPx().coerceAtLeast(1) }
    val bitmap = remember(attachment.path, maxW, maxH) {
        runCatching {
            decodeSampledBitmap(attachment.path, maxW, maxH)?.asImageBitmap()
        }.getOrNull()
    }

    // Zoom state belongs to the image on screen: a new attachment starts over at 1x.
    var scale by remember(attachment.path) { mutableFloatStateOf(1f) }
    var offset by remember(attachment.path) { mutableStateOf(Offset.Zero) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun resetZoom() {
        scale = 1f
        offset = Offset.Zero
    }

    fun stepZoom(direction: Float) {
        scale = (scale + direction * ViewerStepScale).coerceIn(ViewerMinScale, ViewerMaxScale)
        if (scale <= 1f) offset = Offset.Zero
    }

    fun download() {
        scope.launch {
            val saved = saveAttachmentToDownloads(context, attachment)
            Toast.makeText(
                context,
                if (saved) "Saved to Downloads" else "Couldn’t save image",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            if (bitmap != null) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val halfW = with(density) { maxWidth.toPx() / 2f }
                    val halfH = with(density) { maxHeight.toPx() / 2f }
                    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                        scale = (scale * zoomChange).coerceIn(ViewerMinScale, ViewerMaxScale)
                        offset = if (scale <= 1f) {
                            Offset.Zero
                        } else {
                            // Keep the image from sliding entirely out of view.
                            val boundX = halfW * (scale - 1f)
                            val boundY = halfH * (scale - 1f)
                            Offset(
                                x = (offset.x + panChange.x).coerceIn(-boundX, boundX),
                                y = (offset.y + panChange.y).coerceIn(-boundY, boundY),
                            )
                        }
                    }
                    Image(
                        bitmap = bitmap,
                        contentDescription = attachment.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .transformable(transformState)
                            .pointerInput(attachment.path) {
                                detectTapGestures(
                                    onTap = { /* absorb — keep open when tapping the image */ },
                                    onDoubleTap = {
                                        if (scale > 1f) {
                                            resetZoom()
                                        } else {
                                            scale = ViewerDoubleTapScale
                                        }
                                    },
                                )
                            }
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ViewerRoundButton(
                    painter = ConvoIcons.Download(),
                    contentDescription = "Download image",
                    onClick = ::download,
                )
                ViewerRoundButton(
                    painter = ConvoIcons.Close(),
                    contentDescription = "Close",
                    onClick = onDismiss,
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
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
                    onClick = { stepZoom(-1f) },
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
                            onClick = ::resetZoom,
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                ViewerZoomStep(
                    label = "+",
                    contentDescription = "Zoom in",
                    enabled = scale < ViewerMaxScale,
                    onClick = { stepZoom(1f) },
                )
            }
        }
    }
}

@Composable
private fun ViewerRoundButton(
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
private fun ViewerZoomStep(
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
