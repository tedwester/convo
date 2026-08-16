package tedwester.convo.ui.chat.attachments

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import tedwester.convo.features.chat.model.ChatAttachment
import java.io.File

internal fun decodeSampledBitmap(
    path: String,
    reqWidth: Int,
    reqHeight: Int,
): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds, reqWidth, reqHeight)
    }
    return BitmapFactory.decodeFile(path, options)
}

private fun calculateInSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int,
): Int {
    val (height, width) = options.outHeight to options.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        var halfH = height / 2
        var halfW = width / 2
        while (halfH / inSampleSize >= reqHeight && halfW / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize.coerceAtLeast(1)
}

@Composable
fun AttachmentPreviewStrip(
    attachments: List<ChatAttachment>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var viewing by remember { mutableStateOf<ChatAttachment?>(null) }
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        attachments.forEach { attachment ->
            if (attachment.isImage) {
                AttachmentImageThumb(
                    attachment = attachment,
                    onRemove = { onRemove(attachment.id) },
                    onClick = { viewing = attachment },
                )
            } else {
                AttachmentFileBadge(
                    attachment = attachment,
                    onRemove = { onRemove(attachment.id) },
                )
            }
        }
    }
    viewing?.let { attachment ->
        ImageViewerDialog(
            attachment = attachment,
            onDismiss = { viewing = null },
        )
    }
}

@Composable
fun AttachmentImageThumb(
    attachment: ChatAttachment,
    onRemove: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    Box(
        modifier = modifier.size(size),
    ) {
        val imageModifier = Modifier
            .matchParentSize()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
        SubcomposeAsyncImage(
            model = File(attachment.path),
            contentDescription = attachment.displayName,
            contentScale = ContentScale.Crop,
            modifier = imageModifier,
            loading = {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
            error = {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
        )
        if (onRemove != null) {
            RemoveChip(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

@Composable
fun AttachmentFileBadge(
    attachment: ChatAttachment,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .height(48.dp)
            .widthIn(max = 200.dp)
            .clip(shape)
            .background(if (dark) Color(0xFF2A2A2A) else Color(0xFFF0F1F4))
            .border(
                width = 1.dp,
                color = if (dark) Color(0xFF3A3A3A) else Color(0xFFE2E5EB),
                shape = shape,
            )
            .padding(start = 10.dp, end = if (onRemove != null) 6.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.InsertDriveFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = attachment.displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val ext = attachment.displayName
                .substringAfterLast('.', missingDelimiterValue = "")
                .uppercase()
                .takeIf { it.isNotBlank() && it.length <= 5 }
                ?: attachment.mimeType?.substringAfter('/')?.uppercase()?.take(6)
                ?: "FILE"
            Text(
                text = ext,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (onRemove != null) {
            RemoveChip(onClick = onRemove)
        }
    }
}

@Composable
private fun RemoveChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(2.dp)
            .size(20.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Remove attachment",
            tint = Color.White,
            modifier = Modifier.size(12.dp),
        )
    }
}
