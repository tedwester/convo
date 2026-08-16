package tedwester.convo.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tedwester.convo.features.chat.data.ChatRunStatus
import tedwester.convo.features.chat.model.Chat
import tedwester.convo.ui.components.ConvoIconButton
import tedwester.convo.ui.components.ConvoIconButtonGap
import tedwester.convo.ui.components.ConvoOverflowMenu
import tedwester.convo.ui.components.ConvoPopupMenuDivider
import tedwester.convo.ui.components.ConvoPopupMenuItem
import tedwester.convo.ui.components.SpinningLoader
import tedwester.convo.ui.icons.ConvoIcons
import java.util.concurrent.TimeUnit

@Composable
internal fun selectedChatHighlight(): Color {
    val dark = isSystemInDarkTheme()
    return if (dark) {
        Color.White.copy(alpha = 0.06f)
    } else {
        Color.Black.copy(alpha = 0.045f)
    }
}

@Composable
internal fun ChatListHeader(
    title: String,
    onOpenSettings: () -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                start = ChatHeaderHorizontalPadding,
                end = ChatHeaderHorizontalPadding,
                top = ChatHeaderVerticalPadding,
                bottom = ChatHeaderVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConvoIconButton(
            painter = ConvoIcons.Cog(),
            contentDescription = "Settings",
            onClick = onOpenSettings,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = (-0.2).sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = ChatTitleStartGap, end = ConvoIconButtonGap),
        )
        ConvoIconButton(
            painter = ConvoIcons.Search(),
            contentDescription = "Search chats",
            onClick = onSearch,
        )
        Spacer(modifier = Modifier.size(ConvoIconButtonGap))
        ConvoIconButton(
            painter = ConvoIcons.ArrowRightToLine(),
            contentDescription = "Back to chat",
            onClick = onBack,
        )
    }
}

@Composable
internal fun ProjectsNavRow(onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = ConvoIcons.Folder(),
            contentDescription = null,
            tint = muted,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = "Projects",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
internal fun CollapsibleSectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(ChatSectionAnimMs, easing = FastOutSlowInEasing),
        label = "sectionChevron",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = ConvoIcons.ChevronDown(),
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            modifier = Modifier
                .size(18.dp)
                .rotate(chevronRotation),
        )
    }
}

@Composable
internal fun EmptySectionHint(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        modifier = modifier.padding(horizontal = 14.dp, vertical = 14.dp),
    )
}

private val ChatRowDeleteAnimSpec = tween<Float>(ChatSectionAnimMs, easing = FastOutSlowInEasing)

@Composable
private fun AnimatedChatTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = title,
        transitionSpec = {
            (
                fadeIn(tween(ChatSectionAnimMs, easing = FastOutSlowInEasing)) +
                    slideInVertically(
                        animationSpec = tween(ChatSectionAnimMs, easing = FastOutSlowInEasing),
                        initialOffsetY = { it / 3 },
                    )
                ) togetherWith (
                fadeOut(tween(ChatSectionAnimMs / 2, easing = FastOutSlowInEasing)) +
                    slideOutVertically(
                        animationSpec = tween(ChatSectionAnimMs / 2, easing = FastOutSlowInEasing),
                        targetOffsetY = { -it / 3 },
                    )
                )
        },
        label = "chatTitle",
        modifier = modifier,
    ) { animatedTitle ->
        Text(
            text = animatedTitle,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun AnimatedChatListRow(
    chat: Chat,
    selected: Boolean,
    runStatus: ChatRunStatus?,
    projectName: String?,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onAddToProject: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember(chat.id) { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    AnimatedVisibility(
        visible = visible,
        enter = EnterTransition.None,
        exit = fadeOut(ChatRowDeleteAnimSpec),
        modifier = modifier,
    ) {
        ChatListRow(
            chat = chat,
            selected = selected,
            runStatus = runStatus,
            projectName = projectName,
            onClick = onClick,
            onPin = onPin,
            onAddToProject = onAddToProject,
            onRename = onRename,
            onArchive = onArchive,
            onDelete = {
                visible = false
                scope.launch {
                    delay(ChatSectionAnimMs.toLong())
                    onDelete()
                }
            },
        )
    }
}

@Composable
internal fun ChatListRow(
    chat: Chat,
    selected: Boolean,
    runStatus: ChatRunStatus?,
    projectName: String?,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onAddToProject: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val mutedIcon = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)

    Row(
        modifier = modifier
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
            .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (chat.pinned) {
                    Icon(
                        painter = ConvoIcons.Pin(),
                        contentDescription = "Pinned",
                        tint = mutedIcon,
                        modifier = Modifier.size(13.dp),
                    )
                }
                AnimatedChatTitle(
                    title = chat.title,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = formatRelativeTime(chat.updatedAt),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                    ),
                    color = mutedIcon,
                )
                if (projectName != null) {
                    ProjectBadge(name = projectName)
                }
            }
        }

        when (runStatus) {
            ChatRunStatus.Running -> {
                SpinningLoader(
                    tint = mutedIcon,
                    modifier = Modifier
                        .padding(start = 8.dp, end = 4.dp)
                        .size(16.dp),
                )
            }
            ChatRunStatus.CompletedUnread -> {
                Icon(
                    painter = ConvoIcons.Check(),
                    contentDescription = "Response Completed",
                    tint = mutedIcon,
                    modifier = Modifier
                        .padding(start = 8.dp, end = 4.dp)
                        .size(16.dp),
                )
            }
            null -> Unit
        }

        ConvoOverflowMenu(
            expanded = menuOpen,
            onExpandedChange = { menuOpen = it },
            contentDescription = "Chat options",
            tint = mutedIcon,
        ) {
            ConvoPopupMenuItem(
                label = if (chat.pinned) "Unpin" else "Pin",
                icon = ConvoIcons.Pin(),
                onClick = onPin,
            )
            ConvoPopupMenuItem(
                label = "Add to project",
                icon = ConvoIcons.FolderPlus(),
                onClick = onAddToProject,
            )
            ConvoPopupMenuItem(
                label = "Rename",
                icon = ConvoIcons.SquarePen(),
                onClick = onRename,
            )
            ConvoPopupMenuItem(
                label = if (chat.archived) "Unarchive" else "Archive",
                icon = ConvoIcons.Archive(),
                onClick = onArchive,
            )
            ConvoPopupMenuDivider()
            ConvoPopupMenuItem(
                label = "Delete",
                icon = ConvoIcons.Trash2(),
                destructive = true,
                onClick = onDelete,
            )
        }
    }
}

@Composable
internal fun ProjectBadge(name: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.055f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = ConvoIcons.Folder(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Normal,
                fontSize = 10.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun formatRelativeTime(epochMs: Long): String {
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
