package tedwester.convo.ui.components

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.focus.FocusManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tedwester.convo.ui.icons.ConvoIcons
import tedwester.convo.ui.theme.ConvoModalTokens
import tedwester.convo.ui.theme.ConvoTheme
import tedwester.convo.ui.theme.convoModalSurface

class ConvoSheetController {
    var visible by mutableStateOf(false)
        private set
    var closing by mutableStateOf(false)
        private set

    internal var dialogView: View? = null
    internal var prepareClose: (suspend () -> Boolean)? = null

    fun open() {
        if (!closing) visible = true
    }

    suspend fun animateClose(): Boolean {
        if (closing) return false
        closing = true
        val keyboardWasOpen = prepareClose?.invoke() == true
        if (keyboardWasOpen) {
            delay(ConvoModalTokens.KeyboardCloseLeadMs.toLong())
        }
        visible = false
        return true
    }

    fun dismiss(scope: CoroutineScope, onFinished: () -> Unit) {
        if (closing) return
        scope.launch {
            if (!animateClose()) return@launch
            delay(ConvoModalTokens.AnimMs.toLong())
            onFinished()
        }
    }
}

private fun dismissKeyboardBeforeSheetExit(
    focusManager: FocusManager,
    keyboardController: SoftwareKeyboardController?,
    views: List<View>,
): Boolean {
    val closeViews = views.distinct()
    val keyboardWasOpen = closeViews.any { view ->
        view.hasFocus() ||
            ViewCompat.getRootWindowInsets(view)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
    }

    focusManager.clearFocus(force = true)
    keyboardController?.hide()

    val inputMethodManager = closeViews.firstOrNull()
        ?.context
        ?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    closeViews.forEach { view ->
        view.clearFocus()
        inputMethodManager?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    return keyboardWasOpen
}

@Composable
fun rememberConvoSheetController(): ConvoSheetController =
    remember { ConvoSheetController() }

@Composable
fun ConvoBottomSheet(
    controller: ConvoSheetController,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    useDialog: Boolean = false,
    sheetHeightFraction: Float? = null,
    applyImePadding: Boolean = false,
    contentScrollable: Boolean = false,
    contentHorizontalPadding: Dp = ConvoModalTokens.ContentHorizontalPadding,
    contentVerticalPadding: Dp = 10.dp,
    showHandle: Boolean = true,
    consumeSheetClicks: Boolean = true,
    dismissEnabled: Boolean = true,
    title: String? = null,
    titleBottomSpacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val hostView = LocalView.current
    val navigationBarBottomPx = with(density) {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().roundToPx()
    }

    SideEffect {
        controller.prepareClose = {
            dismissKeyboardBeforeSheetExit(
                focusManager = focusManager,
                keyboardController = keyboardController,
                views = listOfNotNull(controller.dialogView, hostView),
            )
        }
    }

    fun requestDismiss() {
        if (!dismissEnabled) return
        controller.dismiss(scope, onDismissRequest)
    }

    BackHandler(enabled = dismissEnabled && !controller.closing) { requestDismiss() }

    LaunchedEffect(controller) {
        if (controller.visible || controller.closing) return@LaunchedEffect
        withFrameNanos { }
        controller.open()
    }

    val body: @Composable () -> Unit = {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onBackground,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = controller.visible,
                    enter = fadeIn(tween(ConvoModalTokens.AnimMs)),
                    exit = fadeOut(tween(ConvoModalTokens.AnimMs)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = ConvoModalTokens.ScrimAlpha))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                },
                            ),
                    )
                }

                AnimatedVisibility(
                    visible = controller.visible,
                    enter = slideInVertically(
                        animationSpec = tween(ConvoModalTokens.AnimMs),
                        initialOffsetY = { it },
                    ),
                    exit = slideOutVertically(
                        animationSpec = tween(ConvoModalTokens.AnimMs),
                        targetOffsetY = { sheetHeight ->
                            sheetHeight + navigationBarBottomPx
                        },
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .graphicsLayer { clip = false },
                ) {
                    val sheetShape = RoundedCornerShape(
                        topStart = ConvoModalTokens.CornerRadius,
                        topEnd = ConvoModalTokens.CornerRadius,
                    )
                    Box(
                        modifier = modifier
                            .fillMaxWidth()
                            .then(
                                if (sheetHeightFraction != null) {
                                    Modifier.fillMaxHeight(sheetHeightFraction)
                                } else {
                                    Modifier
                                },
                            )
                            .clip(sheetShape)
                            .background(convoModalSurface())
                            .then(
                                if (consumeSheetClicks) {
                                    Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {},
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .then(if (applyImePadding) Modifier.imePadding() else Modifier),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (sheetHeightFraction != null) {
                                        Modifier.fillMaxHeight()
                                    } else {
                                        Modifier
                                    },
                                )
                                .then(
                                    if (contentScrollable) {
                                        Modifier.verticalScroll(rememberScrollState())
                                    } else {
                                        Modifier
                                    },
                                )
                                .navigationBarsPadding()
                                .padding(
                                    horizontal = contentHorizontalPadding,
                                    vertical = contentVerticalPadding,
                                ),
                        ) {
                            if (showHandle) {
                                ConvoSheetHandle(onClick = { requestDismiss() })
                            }
                            if (title != null) {
                                ConvoSheetHeader(
                                    title = title,
                                    onClose = { requestDismiss() },
                                )
                                if (titleBottomSpacing > 0.dp) {
                                    Spacer(modifier = Modifier.height(titleBottomSpacing))
                                }
                            }
                            content()
                        }
                    }
                }
            }
        }
    }

    if (useDialog) {
        Dialog(
            onDismissRequest = { requestDismiss() },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
            ),
        ) {
            ConvoTheme {
                val dialogView = LocalView.current
                SideEffect {
                    controller.dialogView = dialogView
                    (dialogView.parent as? DialogWindowProvider)?.window?.apply {
                        setDimAmount(0f)
                        setWindowAnimations(0)
                    }
                }
                body()
            }
        }
    } else {
        body()
    }
}

@Composable
fun ConvoSheetHandle(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(top = 6.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(
                    width = ConvoModalTokens.HandleWidth,
                    height = ConvoModalTokens.HandleHeight,
                )
                .clip(RoundedCornerShape(50))
                .background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                ),
        )
    }
}

@Composable
fun ConvoSheetHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    closeEnabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        )
        ConvoIconButton(
            painter = ConvoIcons.Close(),
            contentDescription = "Close",
            onClick = onClose,
            enabled = closeEnabled,
        )
    }
}
