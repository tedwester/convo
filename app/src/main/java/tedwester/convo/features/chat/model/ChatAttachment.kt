package tedwester.convo.features.chat.model

data class ChatAttachment(
    val id: String,

    val path: String,
    val mimeType: String?,
    val displayName: String,
) {
    val isImage: Boolean
        get() {
            if (mimeType?.startsWith("image/", ignoreCase = true) == true) return true
            val ext = displayName.substringAfterLast('.', "")
                .ifBlank { path.substringAfterLast('.', "") }
                .lowercase()
            return ext in IMAGE_EXTENSIONS
        }

    val isVideo: Boolean
        get() {
            if (mimeType?.startsWith("video/", ignoreCase = true) == true) return true
            val ext = displayName.substringAfterLast('.', "")
                .ifBlank { path.substringAfterLast('.', "") }
                .lowercase()
            return ext in VIDEO_EXTENSIONS
        }

    private companion object {
        val IMAGE_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif",
        )
        val VIDEO_EXTENSIONS = setOf(
            "mp4", "webm", "mov", "m4v", "mkv",
        )
    }
}
