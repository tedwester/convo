package tedwester.convo.ui.chat.message

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tedwester.convo.features.chat.model.ChatAttachment
import tedwester.convo.ui.icons.ConvoIcons

private val CardShape = RoundedCornerShape(14.dp)

@Composable
internal fun AssistantVideoOutput(
    attachment: ChatAttachment,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var preview by remember(attachment.path) { mutableStateOf<VideoPreview?>(null) }
    LaunchedEffect(attachment.path) {
        preview = withContext(Dispatchers.IO) { loadVideoPreview(attachment.path) }
    }
    val ratio = preview?.aspectRatio ?: (16f / 9f)

    Box(
        modifier = modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .aspectRatio(ratio.coerceIn(9f / 16f, 16f / 9f))
            .clip(CardShape)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = CardShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val thumb = preview?.thumbnail
        if (thumb != null) {
            Image(
                bitmap = thumb,
                contentDescription = attachment.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = ConvoIcons.Play(),
                contentDescription = "Play video",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private data class VideoPreview(
    val thumbnail: ImageBitmap?,
    val aspectRatio: Float,
)

private fun loadVideoPreview(path: String): VideoPreview {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull()
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull()
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull() ?: 0
        val ratio = videoAspectRatio(width, height, rotation, frame)
        VideoPreview(
            thumbnail = frame?.asImageBitmap(),
            aspectRatio = ratio,
        )
    } catch (_: Exception) {
        VideoPreview(thumbnail = null, aspectRatio = 16f / 9f)
    } finally {
        runCatching { retriever.release() }
    }
}

private fun videoAspectRatio(
    width: Int?,
    height: Int?,
    rotation: Int,
    frame: Bitmap?,
): Float {
    val w = width ?: frame?.width
    val h = height ?: frame?.height
    if (w == null || h == null || w <= 0 || h <= 0) return 16f / 9f
    val swapped = rotation == 90 || rotation == 270
    return if (swapped) h.toFloat() / w else w.toFloat() / h
}
