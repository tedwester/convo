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

internal val PromptBarHeight = 36.dp

@Composable
internal fun UserPromptControls(
    copyEnabled: Boolean,
    editEnabled: Boolean,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    controlsAlpha: Float = 1f,
) {
    val consumeClicks = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .height(PromptBarHeight)
            .graphicsLayer { alpha = controlsAlpha.coerceIn(0f, 1f) }
            .clickable(
                interactionSource = consumeClicks,
                indication = null,
                onClick = {},
            ),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MessageActionButton(
            painter = ConvoIcons.Pencil(),
            contentDescription = "Edit",
            enabled = editEnabled,
            onClick = onEdit,
        )
        CopyButton(
            enabled = copyEnabled,
            onCopy = onCopy,
        )
    }
}
