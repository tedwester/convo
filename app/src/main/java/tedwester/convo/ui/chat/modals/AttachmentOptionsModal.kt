package tedwester.convo.ui.chat.modals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tedwester.convo.ui.components.ConvoBottomSheet
import tedwester.convo.ui.components.rememberConvoSheetController
import tedwester.convo.ui.icons.ConvoIcons
import tedwester.convo.ui.theme.ConvoModalTokens

@Composable
fun AttachmentOptionsModal(
    onImageClick: () -> Unit,
    onCameraClick: () -> Unit,
    onFileClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    allowImages: Boolean = true,
    allowFiles: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val sheet = rememberConvoSheetController()

    fun pickAndDismiss(action: () -> Unit) {
        if (sheet.closing) return
        scope.launch {
            if (!sheet.animateClose()) return@launch
            action()
            delay(ConvoModalTokens.AnimMs.toLong())
            onDismiss()
        }
    }

    ConvoBottomSheet(
        controller = sheet,
        onDismissRequest = onDismiss,
        useDialog = true,
        contentHorizontalPadding = 20.dp,
        contentVerticalPadding = 10.dp,
        consumeSheetClicks = false,
        title = "Options",
        titleBottomSpacing = 20.dp,
        modifier = modifier,
    ) {
        val showImageTiles = allowImages
        val showFileTile = allowFiles
        val visibleSlots = (if (showImageTiles) 2 else 0) + (if (showFileTile) 1 else 0)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val slot = Modifier.weight(1f)
            if (showImageTiles) {
                AttachmentOptionTile(
                    label = "Image",
                    icon = ConvoIcons.Images(),
                    onClick = { pickAndDismiss(onImageClick) },
                    modifier = slot,
                )
                AttachmentOptionTile(
                    label = "Camera",
                    icon = ConvoIcons.Camera(),
                    onClick = { pickAndDismiss(onCameraClick) },
                    modifier = slot,
                )
            }
            if (showFileTile) {
                AttachmentOptionTile(
                    label = "File",
                    icon = ConvoIcons.File(),
                    onClick = { pickAndDismiss(onFileClick) },
                    modifier = slot,
                )
            }
            repeat((3 - visibleSlots).coerceAtLeast(0)) {
                Spacer(modifier = slot)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AttachmentOptionTile(
    label: String,
    icon: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tileShape = RoundedCornerShape(ConvoModalTokens.CornerRadius)
    val tileColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(tileShape)
                .background(tileColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        ) {
            Icon(
                painter = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
        )
    }
}
