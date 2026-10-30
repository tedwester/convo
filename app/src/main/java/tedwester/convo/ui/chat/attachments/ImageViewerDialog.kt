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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import tedwester.convo.features.chat.model.ChatAttachment
import tedwester.convo.ui.chat.message.saveAttachmentToDownloads
import tedwester.convo.ui.icons.ConvoIcons

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
    val zoom = rememberViewerZoom(attachment.path)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
                        zoom.applyZoom(zoomChange)
                        // Keep the image from sliding entirely out of view.
                        zoom.applyPan(
                            panChange,
                            halfW * (zoom.scale - 1f),
                            halfH * (zoom.scale - 1f),
                        )
                    }
                    Image(
                        bitmap = bitmap,
                        contentDescription = attachment.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .transformable(transformState)
                            .pointerInput(attachment.path) {
                                // Single tap only: with no double-tap to wait for, this
                                // fires the moment the finger lifts, so exiting is
                                // instant. Zoom stays on pinch and the buttons below.
                                detectTapGestures(
                                    onTap = {
                                        // At rest a tap anywhere on the image is empty
                                        // space, so it closes. Zoomed in, taps are for
                                        // looking around and must not dismiss.
                                        if (!zoom.zoomedIn()) onDismiss()
                                    },
                                )
                            }
                            .viewerZoomGraphics(zoom),
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

            ViewerZoomBar(
                scale = zoom.scale,
                onZoomOut = { zoom.step(-1f) },
                onZoomIn = { zoom.step(1f) },
                onReset = { zoom.reset() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
