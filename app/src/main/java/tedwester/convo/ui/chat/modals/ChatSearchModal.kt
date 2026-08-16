package tedwester.convo.ui.chat.modals

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tedwester.convo.features.chat.model.Chat
import tedwester.convo.ui.components.ConvoIconButtonSize
import tedwester.convo.ui.components.ConvoIconGlyphSize
import tedwester.convo.ui.components.ConvoSearchField
import tedwester.convo.ui.icons.ConvoIcons
import tedwester.convo.ui.theme.ConvoFieldTokens
import tedwester.convo.ui.theme.ConvoSearchHeaderTokens
import java.util.concurrent.TimeUnit

private const val SearchAnimMs = 240
private val HeaderHorizontalPadding = 16.dp
private val HeaderVerticalPadding = 10.dp

@Composable
private fun selectedChatHighlight(): Color {
    val dark = isSystemInDarkTheme()
    return if (dark) {
        Color.White.copy(alpha = 0.06f)
    } else {
        Color.Black.copy(alpha = 0.045f)
    }
}

@Composable
fun ChatSearchModal(
    chats: List<Chat>,
    activeChatId: String?,
    onOpenChat: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var showBack by remember { mutableStateOf(false) }
    var showSearchContent by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val searchTextAlpha by animateFloatAsState(
        targetValue = if (showSearchContent) 1f else 0f,
        animationSpec = if (showSearchContent) {
            tween(ConvoSearchHeaderTokens.ContentFadeMs, easing = LinearOutSlowInEasing)
        } else {
            tween(80, easing = FastOutSlowInEasing)
        },
        label = "chatSearchTextAlpha",
    )

    LaunchedEffect(Unit) {
        visible = true
        searchOpen = true
        delay(ConvoSearchHeaderTokens.ContentStartMs.toLong())
        showSearchContent = true
        delay(
            (ConvoSearchHeaderTokens.BackDelayMs - ConvoSearchHeaderTokens.ContentStartMs)
                .toLong()
                .coerceAtLeast(0),
        )
        showBack = true
        runCatching { focusRequester.requestFocus() }
        keyboardController?.show()
    }

    fun finishClose() {
        if (closing) return
        closing = true
        showBack = false
        showSearchContent = false
        searchOpen = false
        visible = false
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        scope.launch {
            delay(SearchAnimMs.toLong())
            onDismiss()
        }
    }

    BackHandler(enabled = !closing) { finishClose() }

    val filtered = remember(chats, query) {
        val q = query.trim()
        if (q.isEmpty()) {
            chats
        } else {
            chats.filter { chat ->
                chat.title.contains(q, ignoreCase = true) ||
                    chat.preview.contains(q, ignoreCase = true)
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(SearchAnimMs)),
        exit = fadeOut(tween(SearchAnimMs)),
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            SearchHeader(
                query = query,
                onQueryChange = { query = it },
                onBack = { finishClose() },
                focusRequester = focusRequester,
                searchOpen = searchOpen,
                showBack = showBack,
                searchTextAlpha = searchTextAlpha,
            )

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (query.isBlank()) {
                            "No chats yet"
                        } else {
                            "No matching chats"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = HeaderHorizontalPadding,
                        end = HeaderHorizontalPadding,
                        top = 8.dp,
                        bottom = 16.dp,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    items(filtered, key = { it.id }) { chat ->
                        SearchResultRow(
                            chat = chat,
                            selected = chat.id == activeChatId,
                            onClick = { onOpenChat(chat.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    focusRequester: FocusRequester,
    searchOpen: Boolean,
    showBack: Boolean,
    searchTextAlpha: Float,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = HeaderHorizontalPadding,
                end = HeaderHorizontalPadding,
                top = HeaderVerticalPadding,
                bottom = HeaderVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fieldHeight = ConvoFieldTokens.SingleLineHeight
        Box(
            modifier = Modifier
                .width(ConvoSearchHeaderTokens.BackSlotWidth)
                .height(fieldHeight),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = showBack,
                enter = slideInHorizontally(
                    animationSpec = tween(
                        ConvoSearchHeaderTokens.BackSlideMs,
                        easing = FastOutSlowInEasing,
                    ),
                    initialOffsetX = { -it },
                ) + fadeIn(
                    tween(ConvoSearchHeaderTokens.BackSlideMs, easing = FastOutSlowInEasing),
                ),
                exit = slideOutHorizontally(
                    animationSpec = tween(
                        ConvoSearchHeaderTokens.BackSlideMs,
                        easing = FastOutSlowInEasing,
                    ),
                    targetOffsetX = { -it },
                ) + fadeOut(
                    tween(
                        ConvoSearchHeaderTokens.BackSlideMs,
                        easing = FastOutSlowInEasing,
                    ),
                ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onBack,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = ConvoIcons.ArrowLeft(),
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(ConvoIconGlyphSize),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(ConvoSearchHeaderTokens.BackGap))
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(fieldHeight),
            contentAlignment = Alignment.CenterEnd,
        ) {
            var expanded by remember { mutableStateOf(false) }
            LaunchedEffect(searchOpen) {
                expanded = searchOpen
            }
            val searchWidth by animateDpAsState(
                targetValue = if (expanded) maxWidth else ConvoIconButtonSize,
                animationSpec = tween(
                    ConvoSearchHeaderTokens.ExpandMs,
                    easing = FastOutSlowInEasing,
                ),
                label = "chatSearchWidth",
            )
            val searchFieldShape = RoundedCornerShape(ConvoFieldTokens.SearchCornerRadius)
            val showSearchField =
                searchOpen || expanded || searchWidth > ConvoIconButtonSize + 2.dp

            if (showSearchField) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(searchWidth)
                        .height(fieldHeight)
                        .clip(searchFieldShape),
                    contentAlignment = Alignment.Center,
                ) {
                    ConvoSearchField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = "Search chats",
                        focusRequester = focusRequester,
                        textAlpha = searchTextAlpha,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    chat: Chat,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) selectedChatHighlight() else Color.Transparent,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = chat.title,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = formatRelativeTime(chat.updatedAt),
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
    }
}

private fun formatRelativeTime(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    val delta = (System.currentTimeMillis() - epochMs).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    val hours = TimeUnit.MILLISECONDS.toHours(delta)
    val days = TimeUnit.MILLISECONDS.toDays(delta)
    return when {
        minutes < 1L -> "just now"
        minutes < 60L -> "${minutes}m ago"
        hours < 24L -> "${hours}h ago"
        days < 7L -> "${days}d ago"
        else -> {
            val weeks = days / 7L
            if (weeks < 5L) "${weeks}w ago" else "${days / 30L}mo ago"
        }
    }
}
