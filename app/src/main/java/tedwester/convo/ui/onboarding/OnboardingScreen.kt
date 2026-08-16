package tedwester.convo.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tedwester.convo.core.network.OpenRouterApi
import tedwester.convo.core.network.OpenRouterApiKeyValidation
import tedwester.convo.ui.components.ConvoButton
import tedwester.convo.ui.components.ConvoIconButton
import tedwester.convo.ui.components.ConvoTextField
import tedwester.convo.ui.components.PasswordVisibilityToggle
import tedwester.convo.ui.components.rememberAnimatedPasswordReveal
import tedwester.convo.ui.input.ConvoKeyboardOptions
import tedwester.convo.ui.input.rememberDismissKeyboard
import tedwester.convo.ui.icons.ConvoIcons

@Composable
fun OnboardingScreen(
    api: OpenRouterApi,
    onComplete: (apiKey: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val dismissKeyboard = rememberDismissKeyboard()
    val hasClipboardText = rememberClipboardHasText()

    var apiKey by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var keyVisible by remember { mutableStateOf(false) }
    val passwordReveal = rememberAnimatedPasswordReveal(
        visible = keyVisible,
        animateReveal = apiKey.isNotBlank(),
    )

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val trimmedKey = apiKey.trim()
    val keyFormatValid = OpenRouterApiKeyValidation.isPlausibleFormat(trimmedKey)

    fun submit() {
        val formatError = OpenRouterApiKeyValidation.formatError(trimmedKey)
        if (formatError != null) {
            error = formatError
            return
        }
        error = null
        dismissKeyboard()
        isLoading = true
        scope.launch {
            try {
                api.fetchModels(apiKey = trimmedKey)
                onComplete(trimmedKey)
            } catch (e: Exception) {
                error = e.message ?: "Could not verify the key. Please try again."
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(380)) +
                slideInVertically(
                    animationSpec = tween(420),
                    initialOffsetY = { it / 16 },
                ),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(top = 56.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.Top,
            ) {
                BrandMark()

                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "Convo",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Normal,
                        letterSpacing = (-0.4).sp,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Connect your OpenRouter key to start chatting. It’s stored only on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(36.dp))
                Text(
                    text = "API key",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(10.dp))
                ConvoTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        if (error != null) error = null
                    },
                    enabled = !isLoading,
                    placeholder = "sk-or-v1-…",
                    visualTransformation = passwordReveal.visualTransformation,
                    textAlpha = passwordReveal.textAlpha,
                    keyboardOptions = ConvoKeyboardOptions.Password,
                    keyboardActions = KeyboardActions(
                        onDone = { if (keyFormatValid && !isLoading) submit() },
                    ),
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (hasClipboardText) {
                                ConvoIconButton(
                                    painter = ConvoIcons.ClipboardPaste(),
                                    contentDescription = "Paste from clipboard",
                                    onClick = {
                                        clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }
                                            ?.let { pasted ->
                                                apiKey = pasted
                                                if (error != null) error = null
                                            }
                                    },
                                    enabled = !isLoading,
                                    size = 36.dp,
                                    iconSize = 18.dp,
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            PasswordVisibilityToggle(
                                visible = keyVisible,
                                onToggle = { keyVisible = !keyVisible },
                                enabled = !isLoading,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (error != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                ConvoButton(
                    text = "Continue",
                    onClick = ::submit,
                    enabled = keyFormatValid && !isLoading,
                    loading = isLoading,
                    icon = null,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Get a key at openrouter.ai/keys",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable { uriHandler.openUri("https://openrouter.ai/keys") }
                        .padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun rememberClipboardHasText(): Boolean {
    val context = LocalContext.current
    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    var hasText by remember { mutableStateOf(false) }

    DisposableEffect(clipboardManager) {
        fun refresh() {
            val clip = clipboardManager.primaryClip
            hasText = clip != null &&
                clip.itemCount > 0 &&
                clip.getItemAt(0).coerceToText(context).toString().trim().isNotEmpty()
        }
        refresh()
        val listener = ClipboardManager.OnPrimaryClipChangedListener { refresh() }
        clipboardManager.addPrimaryClipChangedListener(listener)
        onDispose { clipboardManager.removePrimaryClipChangedListener(listener) }
    }
    return hasText
}

@Composable
private fun BrandMark() {
    val dark = isSystemInDarkTheme()
    val fill = if (dark) Color.White.copy(alpha = 0.025f) else Color.Black.copy(alpha = 0.03f)
    val ring = if (dark) Color.White.copy(alpha = 0.055f) else Color.Black.copy(alpha = 0.055f)

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(fill)
            .border(width = Dp.Hairline, color = ring, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = ConvoIcons.Key(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(28.dp),
        )
    }
}
