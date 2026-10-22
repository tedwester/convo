package tedwester.convo.ui.chat.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tedwester.convo.ui.chat.modals.ModelSelectorChip
import tedwester.convo.ui.components.ConvoIconButton
import tedwester.convo.ui.icons.ConvoIcons

@Composable
fun ConversationTopBar(
    modelName: String?,
    onOpenMenu: () -> Unit,
    onOpenModelSelector: () -> Unit,
    onNewChat: () -> Unit,
    showModelHint: Boolean = false,
    onDismissModelHint: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConvoIconButton(
            painter = ConvoIcons.CircleUser(),
            contentDescription = "Menu",
            onClick = onOpenMenu,
            modifier = Modifier.padding(start = 4.dp),
        )
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            ModelSelectorHintAnchor(
                show = showModelHint,
                onDismiss = onDismissModelHint,
            ) {
                ModelSelectorChip(
                    modelName = modelName,
                    onClick = onOpenModelSelector,
                    highlighted = showModelHint,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        ConvoIconButton(
            painter = ConvoIcons.SquarePen(),
            contentDescription = "New Chat",
            onClick = onNewChat,
            modifier = Modifier.padding(end = 4.dp),
        )
    }
}
