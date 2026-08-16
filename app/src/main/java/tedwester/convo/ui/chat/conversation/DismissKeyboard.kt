package tedwester.convo.ui.chat.conversation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import tedwester.convo.ui.input.rememberDismissKeyboard

@Composable
fun rememberDismissKeyboard(): () -> Unit = tedwester.convo.ui.input.rememberDismissKeyboard()

@Composable
fun DismissKeyboardOnLifecycle() {
    val dismissKeyboard = rememberDismissKeyboard()
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeGeneration by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner, dismissKeyboard) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                -> dismissKeyboard()
                Lifecycle.Event.ON_RESUME -> resumeGeneration++
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(resumeGeneration) {
        if (resumeGeneration == 0) return@LaunchedEffect
        dismissKeyboard()
        delay(50)
        dismissKeyboard()
    }
}
