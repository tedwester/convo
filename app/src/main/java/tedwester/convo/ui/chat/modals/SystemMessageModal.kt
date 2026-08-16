package tedwester.convo.ui.chat.modals

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tedwester.convo.ui.components.ConvoBottomSheet
import tedwester.convo.ui.components.ConvoButton
import tedwester.convo.ui.components.ConvoTextField
import tedwester.convo.ui.components.rememberConvoSheetController
import tedwester.convo.ui.theme.ConvoModalTokens

@Composable
fun SystemMessageModal(
    initialMessage: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val sheet = rememberConvoSheetController()
    var draft by remember { mutableStateOf(initialMessage) }
    var saving by remember { mutableStateOf(false) }

    val trimmed = draft.trim()
    val canSave = !saving

    fun submit() {
        if (!canSave || sheet.closing) return
        saving = true
        scope.launch {
            delay(280)
            onSave(trimmed)
            sheet.dismiss(scope, onDismiss)
        }
    }

    ConvoBottomSheet(
        controller = sheet,
        onDismissRequest = onDismiss,
        useDialog = true,
        applyImePadding = true,
        contentScrollable = true,
        contentHorizontalPadding = 20.dp,
        contentVerticalPadding = 10.dp,
        consumeSheetClicks = false,
        dismissEnabled = !saving,
        title = "System message",
        titleBottomSpacing = 8.dp,
        modifier = modifier,
    ) {
        Text(
            text = "Instructions sent with every request in this chat. Leave blank to use the model’s default behavior.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            modifier = Modifier.padding(end = 4.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        ConvoTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = "You are a helpful assistant…",
            singleLine = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
        )

        Spacer(modifier = Modifier.height(18.dp))

        ConvoButton(
            text = "Save",
            onClick = { submit() },
            enabled = canSave,
            loading = saving,
            containerColor = Color.White,
            contentColor = Color.Black,
            textStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ConvoModalTokens.ActionHorizontalInset),
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}
