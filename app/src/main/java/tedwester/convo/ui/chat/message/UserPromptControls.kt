package tedwester.convo.ui.chat.message

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import tedwester.convo.ui.components.CopyButton
import tedwester.convo.ui.icons.ConvoIcons

@Composable
internal fun UserPromptControls(
    copyEnabled: Boolean,
    editEnabled: Boolean,
    resendEnabled: Boolean,
    isEditing: Boolean,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onResend: () -> Unit,
    modifier: Modifier = Modifier,
    controlsAlpha: Float = 1f,
) {
    val consumeClicks = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .height(36.dp)
            .graphicsLayer { alpha = controlsAlpha.coerceIn(0f, 1f) }
            .clickable(
                interactionSource = consumeClicks,
                indication = null,
                onClick = {},
            ),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CopyButton(
            enabled = copyEnabled,
            onCopy = onCopy,
        )
        MessageActionButton(
            painter = if (isEditing) ConvoIcons.X() else ConvoIcons.SquarePen(),
            contentDescription = if (isEditing) "Cancel edit" else "Edit",
            enabled = editEnabled,
            onClick = onEdit,
        )
        MessageActionButton(
            painter = ConvoIcons.Repeat(),
            contentDescription = "Resend",
            enabled = resendEnabled,
            onClick = onResend,
        )
    }
}
