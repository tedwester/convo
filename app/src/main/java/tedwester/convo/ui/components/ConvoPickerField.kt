package tedwester.convo.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import tedwester.convo.ui.icons.ConvoIcons

/**
 * Read-only field that opens a picker — same shell as [ConvoTextField].
 */
@Composable
fun ConvoPickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current

    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(
                alpha = if (enabled) 1f else 0.5f,
            ),
        )
        Spacer(modifier = Modifier.height(6.dp))
        ConvoTextField(
            value = value,
            onValueChange = {},
            enabled = enabled,
            onClick = if (enabled) {
                {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onClick()
                }
            } else {
                null
            },
            trailing = {
                Icon(
                    painter = ConvoIcons.ChevronRight(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = if (enabled) 0.65f else 0.35f,
                    ),
                    modifier = Modifier.size(18.dp),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
