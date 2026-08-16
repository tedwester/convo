package tedwester.convo.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import tedwester.convo.ui.input.ConvoKeyboardOptions
import tedwester.convo.ui.icons.ConvoIcons
import tedwester.convo.ui.theme.ConvoFieldTokens

@Composable
fun ConvoSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    textAlpha: Float = 1f,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    ConvoTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        singleLine = true,
        leadingIconPainter = ConvoIcons.Search(),
        focusRequester = focusRequester,
        textAlpha = textAlpha,
        shape = RoundedCornerShape(ConvoFieldTokens.SearchCornerRadius),
        keyboardOptions = ConvoKeyboardOptions.Search,
        keyboardActions = keyboardActions,
        modifier = modifier.fillMaxWidth(),
    )
}
