package tedwester.convo.ui.chat.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tedwester.convo.features.chat.model.ChatMessage
import tedwester.convo.ui.icons.ConvoIcons

/**
 * Variant stepper for the user's own messages. Same shared index space as assistant
 * replies — each edited resend is a variant with its own follow-ups — so it reuses the
 * pager buttons and the shared counter.
 */
@Composable
internal fun UserVariantStepper(
    message: ChatMessage,
    actionsEnabled: Boolean,
    onVariantSwipe: (delta: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VariantPagerButton(
            painter = ConvoIcons.ChevronLeft(),
            contentDescription = "Previous version",
            enabled = actionsEnabled && message.variantIndex > 0,
            onClick = { onVariantSwipe(-1) },
        )
        VariantCounter(
            index = message.pagerIndex,
            total = message.variantCount,
            color = muted,
        )
        VariantPagerButton(
            painter = ConvoIcons.ChevronRight(),
            contentDescription = "Next version",
            enabled = actionsEnabled && message.variantIndex < message.variantSlots - 1,
            onClick = { onVariantSwipe(1) },
        )
    }
}
