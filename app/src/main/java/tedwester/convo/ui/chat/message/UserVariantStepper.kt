package tedwester.convo.ui.chat.message

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tedwester.convo.features.chat.model.ChatMessage

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
    VariantStepperRow(
        message = message,
        actionsEnabled = actionsEnabled,
        onVariantChange = onVariantSwipe,
        prevDescription = "Previous version",
        nextDescription = "Next version",
        modifier = modifier,
    )
}
