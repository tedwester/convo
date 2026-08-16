package tedwester.convo.ui.chat.modals

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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
fun RenameChatModal(
    initialTitle: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheet = rememberConvoSheetController()
    val focusRequester = remember { FocusRequester() }
    var draft by remember { mutableStateOf(initialTitle) }
    var saving by remember { mutableStateOf(false) }

    val trimmed = draft.trim()
    val canSave = trimmed.isNotEmpty() && !saving

    LaunchedEffect(focusRequester) {
        runCatching { focusRequester.requestFocus() }
    }

    fun submit() {
        if (!canSave || sheet.closing) return
        saving = true
        scope.launch {
            if (!sheet.animateClose()) return@launch
            onRename(trimmed)
            delay(ConvoModalTokens.AnimMs.toLong())
            onDismiss()
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
        title = "Rename chat",
        titleBottomSpacing = 16.dp,
    ) {
        ConvoTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = "Chat name",
            focusRequester = focusRequester,
            keyboardActions = KeyboardActions(
                onDone = { submit() },
            ),
            modifier = Modifier.fillMaxWidth(),
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
