package tedwester.convo.ui.chat.attachments

import android.widget.Toast
import android.widget.VideoView
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import tedwester.convo.features.chat.model.ChatAttachment
import tedwester.convo.ui.chat.message.saveAttachmentToDownloads
import tedwester.convo.ui.icons.ConvoIcons

@Composable
fun VideoViewerDialog(
    attachment: ChatAttachment,
    onDismiss: () -> Unit,
) {
    var playing by remember(attachment.path) { mutableStateOf(true) }
    var videoView by remember(attachment.path) { mutableStateOf<VideoView?>(null) }
    val zoom = rememberViewerZoom(attachment.path)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // The video renders on its own surface, which doesn't fade with the dialog —
    // it would pop out while everything else dissolves. Stopping and hiding it
    // first leaves only the backdrop to fade.
    fun dismissCleanly() {
        videoView?.apply {
            stopPlayback()
            visibility = android.view.View.GONE
        }
        onDismiss()
    }

    fun download() {
        scope.launch {
            val saved = saveAttachmentToDownloads(context, attachment)
            Toast.makeText(
                context,
                if (saved) "Saved to Downloads" else "Couldn’t save video",
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
                    onClick = ::dismissCleanly,
                ),
        ) {
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
                    zoom.applyPan(
                        panChange,
                        halfW * (zoom.scale - 1f),
                        halfH * (zoom.scale - 1f),
                    )
                }
                AndroidView(
                    factory = { factoryContext ->
                        VideoView(factoryContext).apply {
                            videoView = this
                            setVideoPath(attachment.path)
                            setOnPreparedListener { player ->
                                player.isLooping = true
                                if (playing) start()
                            }
                            setOnCompletionListener {
                                playing = false
                            }
                            setOnErrorListener { _, _, _ -> true }
                        }
                    },
                    update = { view ->
                        if (playing) {
                            if (!view.isPlaying) view.start()
                        } else if (view.isPlaying) {
                            view.pause()
                        }
                    },
                    onRelease = {
                        videoView = null
                        it.stopPlayback()
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(transformState)
                        .pointerInput(attachment.path) {
                            // Single tap only, so play/pause answers the moment the
                            // finger lifts instead of waiting out a double-tap.
                            // Zoom stays on pinch and the buttons below.
                            detectTapGestures(
                                onTap = { playing = !playing },
                            )
                        }
                        .viewerZoomGraphics(zoom),
                )
            }

            if (!playing) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { playing = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = ConvoIcons.Play(),
                        contentDescription = "Play video",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
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
                    contentDescription = "Download video",
                    onClick = ::download,
                )
                ViewerRoundButton(
                    painter = ConvoIcons.Close(),
                    contentDescription = "Close",
                    onClick = ::dismissCleanly,
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
