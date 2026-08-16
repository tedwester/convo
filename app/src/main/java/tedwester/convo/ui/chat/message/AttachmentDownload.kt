package tedwester.convo.ui.chat.message

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import androidx.core.content.FileProvider
import tedwester.convo.features.chat.model.ChatAttachment
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opens the system share sheet for a locally-stored audio attachment.
 * Returns `false` when the file is missing or can't be shared.
 */
internal fun shareAudioAttachment(context: Context, attachment: ChatAttachment): Boolean =
    shareAttachment(context, attachment, "Share voice message")

internal fun shareAttachment(
    context: Context,
    attachment: ChatAttachment,
    chooserTitle: String = "Share",
): Boolean {
    val src = File(attachment.path)
    if (!src.exists()) return false

    val mime = attachment.mimeType?.takeIf { it.isNotBlank() }
        ?: if (attachment.isVideo) "video/mp4" else "audio/mpeg"
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        src,
    )
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, chooserTitle))
    return true
}

internal suspend fun saveAudioAttachmentToDownloads(
    context: Context,
    attachment: ChatAttachment,
): Boolean = saveAttachmentToDownloads(context, attachment)

/**
 * Saves a locally-stored attachment to the user's Downloads folder
 * (under `Download/Convo/`) via [MediaStore]. Returns `true` on success.
 * Requires API 29+ (Q) for [MediaStore.Downloads] with a relative path.
 */
internal suspend fun saveAttachmentToDownloads(
    context: Context,
    attachment: ChatAttachment,
): Boolean = withContext(Dispatchers.IO) {
    val src = File(attachment.path)
    if (!src.exists()) return@withContext false

    val resolver = context.contentResolver
    val mime = attachment.mimeType?.takeIf { it.isNotBlank() }
        ?: if (attachment.isVideo) "video/mp4" else "audio/mpeg"
    val fallback = if (attachment.isVideo) {
        "video_${System.currentTimeMillis()}.mp4"
    } else {
        "voice_${System.currentTimeMillis()}.mp3"
    }
    val displayName = attachment.displayName.ifBlank { fallback }

    val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Convo")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }

    val uri = resolver.insert(collection, values) ?: return@withContext false
    try {
        resolver.openOutputStream(uri)?.use { out ->
            src.inputStream().use { input -> input.copyTo(out) }
        } ?: run {
            resolver.delete(uri, null, null)
            return@withContext false
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        false
    }
}
