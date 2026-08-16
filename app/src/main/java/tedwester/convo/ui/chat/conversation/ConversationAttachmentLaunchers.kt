package tedwester.convo.ui.chat.conversation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tedwester.convo.features.chat.ChatState
import tedwester.convo.features.chat.data.AttachmentStore
import java.io.File

internal class ConversationAttachmentLaunchers(
    val launchGallery: () -> Unit,
    val launchCamera: () -> Unit,
    val launchFiles: () -> Unit,
)

@Composable
internal fun rememberConversationAttachmentLaunchers(
    chatState: ChatState,
    onAttachmentIngested: () -> Unit,
): ConversationAttachmentLaunchers {
    val context = LocalContext.current
    val attachScope = rememberCoroutineScope()
    val pendingCameraFile = androidx.compose.runtime.remember { mutableListOf<File?>() }

    fun ingestUri(uri: Uri) {
        attachScope.launch {
            val attachment = withContext(Dispatchers.IO) {
                AttachmentStore.ingest(context, uri)
            } ?: return@launch
            chatState.addAttachment(attachment)
            onAttachmentIngested()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) ingestUri(uri)
    }

    val filesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            ingestUri(uri)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val file = pendingCameraFile.removeFirstOrNull()
        if (success && file != null && file.exists()) {
            attachScope.launch {
                val attachment = withContext(Dispatchers.IO) {
                    AttachmentStore.ingestFile(
                        context = context,
                        file = file,
                        mimeType = "image/jpeg",
                        displayName = "photo.jpg",
                        deleteSource = true,
                    )
                } ?: return@launch
                chatState.addAttachment(attachment)
                onAttachmentIngested()
            }
        } else {
            file?.delete()
        }
    }

    fun launchCameraWithPermission() {
        val file = AttachmentStore.createCameraTempFile(context)
        pendingCameraFile.clear()
        pendingCameraFile.add(file)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        cameraLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) launchCameraWithPermission()
    }

    fun launchCamera() {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchCameraWithPermission()
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    return ConversationAttachmentLaunchers(
        launchGallery = {
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        launchCamera = ::launchCamera,
        launchFiles = { filesLauncher.launch(arrayOf("*/*")) },
    )
}
