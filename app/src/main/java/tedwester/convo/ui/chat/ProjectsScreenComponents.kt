package tedwester.convo.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tedwester.convo.features.chat.model.Project
import tedwester.convo.ui.components.ConvoBottomSheet
import tedwester.convo.ui.components.ConvoButton
import tedwester.convo.ui.components.ConvoIconButton
import tedwester.convo.ui.components.ConvoIconButtonSize
import tedwester.convo.ui.components.ConvoIconGlyphSize
import tedwester.convo.ui.components.ConvoOverflowMenu
import tedwester.convo.ui.components.ConvoPopupMenuItem
import tedwester.convo.ui.components.ConvoSearchField
import tedwester.convo.ui.components.ConvoTextField
import tedwester.convo.ui.components.rememberConvoSheetController
import tedwester.convo.ui.icons.ConvoIcons
import tedwester.convo.ui.theme.ConvoFieldTokens
import tedwester.convo.ui.theme.ConvoModalTokens
import tedwester.convo.ui.theme.ConvoSearchHeaderTokens
@Composable
internal fun ProjectsIdleHeader(
    onBack: () -> Unit,
    onSearch: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConvoIconButton(
            painter = ConvoIcons.ArrowLeft(),
            contentDescription = "Back to chats",
            onClick = onBack,
        )
        Text(
            text = "Projects",
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
                .padding(start = ChatTitleStartGap),
        )
        ConvoIconButton(
            painter = ConvoIcons.Search(),
            contentDescription = "Search projects",
            onClick = onSearch,
        )
    }
}

/** Matches [ChatSearchModal] search header geometry for identical field width. */
@Composable
internal fun ProjectsSearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    focusRequester: FocusRequester,
    searchActive: Boolean,
    showSearchBack: Boolean,
    searchTextAlpha: Float,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
                visible = showSearchBack,
                enter = slideInHorizontally(
                    animationSpec = tween(
                        ConvoSearchHeaderTokens.BackSlideMs,
                        easing = FastOutSlowInEasing,
                    ),
                    initialOffsetX = { -it },
                ) + fadeIn(
                    tween(
                        ConvoSearchHeaderTokens.BackSlideMs,
                        easing = FastOutSlowInEasing,
                    ),
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
                            onClick = onCloseSearch,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = ConvoIcons.ArrowLeft(),
                        contentDescription = "Close search",
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
            LaunchedEffect(searchActive) {
                expanded = searchActive
            }
            val searchWidth by animateDpAsState(
                targetValue = if (expanded) maxWidth else ConvoIconButtonSize,
                animationSpec = tween(
                    ConvoSearchHeaderTokens.ExpandMs,
                    easing = FastOutSlowInEasing,
                ),
                label = "projectsSearchWidth",
            )
            val searchFieldShape = RoundedCornerShape(ConvoFieldTokens.SearchCornerRadius)
            val showSearchField =
                searchActive || expanded || searchWidth > ConvoIconButtonSize + 2.dp

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
                        placeholder = "Search projects",
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
internal fun AnimatedProjectRow(
    project: Project,
    animateEnter: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var entered by remember(project.id, animateEnter) { mutableStateOf(!animateEnter) }

    LaunchedEffect(animateEnter) {
        if (animateEnter) {
            entered = true
        }
    }

    AnimatedVisibility(
        visible = entered,
        modifier = modifier,
        enter = fadeIn(tween(ConvoModalTokens.AnimMs)) +
            slideInVertically(
                animationSpec = tween(ConvoModalTokens.AnimMs, easing = FastOutSlowInEasing),
                initialOffsetY = { it / 3 },
            ),
    ) {
        ProjectRow(
            project = project,
            onDelete = onDelete,
        )
    }
}

@Composable
internal fun ProjectRow(
    project: Project,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.045f))
            .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = ConvoIcons.Folder(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            modifier = Modifier.size(20.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 8.dp),
        ) {
            Text(
                text = project.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (project.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = project.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        ConvoOverflowMenu(
            expanded = menuOpen,
            onExpandedChange = { menuOpen = it },
            contentDescription = "Project options",
        ) {
            ConvoPopupMenuItem(
                label = "Delete",
                icon = ConvoIcons.Trash2(),
                destructive = true,
                onClick = onDelete,
            )
        }
    }
}

/**
 * Bottom pull-up sheet matching the shared [ConvoBottomSheet] chrome.
 */
@Composable
internal fun CreateProjectSheet(
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheet = rememberConvoSheetController()
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    val trimmedName = name.trim()
    val isDuplicate = remember(trimmedName, existingNames) {
        trimmedName.isNotEmpty() &&
            existingNames.any { it.equals(trimmedName, ignoreCase = true) }
    }
    val canCreate = trimmedName.isNotEmpty() && !isDuplicate && !creating

    fun submit() {
        if (!canCreate || sheet.closing) return
        creating = true
        scope.launch {
            if (!sheet.animateClose()) return@launch
            onCreate(trimmedName, description.trim())
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
        dismissEnabled = !creating,
        title = "New project",
        titleBottomSpacing = 16.dp,
    ) {
        ConvoTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = "Project name",
            modifier = Modifier.fillMaxWidth(),
        )
        AnimatedVisibility(
            visible = isDuplicate && !creating,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(120)),
        ) {
            Text(
                text = "A project with this name already exists",
                style = MaterialTheme.typography.bodySmall,
                color = ChatMenuDestructive,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        ConvoTextField(
            value = description,
            onValueChange = { description = it },
            placeholder = "What are you working on?",
            singleLine = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp),
        )
        Spacer(modifier = Modifier.height(18.dp))
        ConvoButton(
            text = "Create project",
            onClick = { submit() },
            enabled = canCreate,
            loading = creating,
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

