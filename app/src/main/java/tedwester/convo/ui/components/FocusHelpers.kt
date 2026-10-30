package tedwester.convo.ui.components

import androidx.compose.ui.focus.FocusRequester

fun FocusRequester.requestFocusSafely(): Boolean =
    runCatching { requestFocus() }.isSuccess
