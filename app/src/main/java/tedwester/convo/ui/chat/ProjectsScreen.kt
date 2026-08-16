package tedwester.convo.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import tedwester.convo.ui.components.ConvoSearchField
import tedwester.convo.ui.components.ConvoTextField
import tedwester.convo.ui.components.rememberConvoSheetController
import tedwester.convo.ui.icons.ConvoIcons
import tedwester.convo.ui.theme.ConvoFieldTokens
import tedwester.convo.ui.theme.ConvoModalTokens
import tedwester.convo.ui.theme.ConvoSearchHeaderTokens

private val ProjectsHorizontalPadding = ChatHeaderHorizontalPadding

@Composable
fun ProjectsScreen(
    projects: List<Project>,
    onBack: () -> Unit,
    onCreateProject: (String, String) -> Unit,
    onDeleteProject: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateModal by remember { mutableStateOf(false) }
    var knownProjectIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var enteringProjectId by remember { mutableStateOf<String?>(null) }
    var searchActive by remember { mutableStateOf(false) }
    var headerIdle by remember { mutableStateOf(true) }
    var showSearchBack by remember { mutableStateOf(false) }
    var showSearchContent by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val filteredProjects = remember(projects, query) {
        val value = query.trim()
        if (value.isEmpty()) {
            projects
        } else {
            projects.filter {
                it.name.contains(value, ignoreCase = true) ||
                    it.description.contains(value, ignoreCase = true)
            }
        }
    }
    val existingNames = remember(projects) { projects.map { it.name } }

    LaunchedEffect(projects) {
        if (knownProjectIds.isEmpty()) {
            knownProjectIds = projects.map { it.id }.toSet()
            return@LaunchedEffect
        }
        val newProjectId = projects.firstOrNull { it.id !in knownProjectIds }?.id ?: return@LaunchedEffect
        enteringProjectId = newProjectId
        knownProjectIds = projects.map { it.id }.toSet()
        delay(ConvoModalTokens.AnimMs.toLong())
        enteringProjectId = null
    }

    val searchTextAlpha by animateFloatAsState(
        targetValue = if (showSearchContent) 1f else 0f,
        animationSpec = if (showSearchContent) {
            tween(ConvoSearchHeaderTokens.ContentFadeMs, easing = LinearOutSlowInEasing)
        } else {
            tween(80, easing = FastOutSlowInEasing)
        },
        label = "searchTextAlpha",
    )

    fun closeSearch() {
        showSearchBack = false
        showSearchContent = false
        keyboardController?.hide()
        searchActive = false
        scope.launch {
            delay(
                maxOf(
                    ConvoSearchHeaderTokens.ExpandMs,
                    ConvoSearchHeaderTokens.BackSlideMs,
                ).toLong(),
            )
            headerIdle = true
            query = ""
        }
    }

    LaunchedEffect(searchActive) {
        if (searchActive) {
            showSearchBack = false
            showSearchContent = false
            delay(ConvoSearchHeaderTokens.ContentStartMs.toLong())
            if (!searchActive) return@LaunchedEffect
            showSearchContent = true
            delay((ConvoSearchHeaderTokens.BackDelayMs - ConvoSearchHeaderTokens.ContentStartMs).toLong().coerceAtLeast(0))
            if (!searchActive) return@LaunchedEffect
            showSearchBack = true
            runCatching { searchFocus.requestFocus() }
            keyboardController?.show()
        } else {
            showSearchBack = false
            showSearchContent = false
        }
    }

    BackHandler(enabled = !showCreateModal) {
        when {
            searchActive -> closeSearch()
            else -> onBack()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(
                            start = ProjectsHorizontalPadding,
                            end = ProjectsHorizontalPadding,
                            top = ChatHeaderVerticalPadding,
                            bottom = ChatHeaderVerticalPadding,
                        )
                        .height(ConvoIconButtonSize),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = headerIdle,
                        enter = fadeIn(tween(140)),
                        exit = fadeOut(tween(140)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ProjectsIdleHeader(
                            onBack = onBack,
                            onSearch = {
                                headerIdle = false
                                searchActive = true
                            },
                        )
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !headerIdle,
                        enter = fadeIn(tween(140)),
                        exit = fadeOut(tween(140)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ProjectsSearchHeader(
                            query = query,
                            onQueryChange = { query = it },
                            onCloseSearch = ::closeSearch,
                            focusRequester = searchFocus,
                            searchActive = searchActive,
                            showSearchBack = showSearchBack,
                            searchTextAlpha = searchTextAlpha,
                        )
                    }
                }
            },
            floatingActionButton = {
                ConvoIconButton(
                    painter = ConvoIcons.Add(),
                    contentDescription = "Create project",
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        showCreateModal = true
                    },
                    size = 48.dp,
                    iconSize = 21.dp,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(end = 4.dp, bottom = 4.dp),
                )
            },
        ) { padding ->
            AnimatedContent(
                targetState = filteredProjects.isEmpty(),
                transitionSpec = {
                    fadeIn(tween(ConvoModalTokens.AnimMs)) togetherWith fadeOut(tween(160))
                },
                label = "projectsList",
            ) { isEmpty ->
                if (isEmpty) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (query.isBlank()) {
                                "Create a project to organize related chats."
                            } else {
                                "No matching projects"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentPadding = PaddingValues(
                            start = ProjectsHorizontalPadding,
                            end = ProjectsHorizontalPadding,
                            top = 8.dp,
                            bottom = 92.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filteredProjects, key = { it.id }) { project ->
                            AnimatedProjectRow(
                                modifier = Modifier.animateItem(
                                    fadeOutSpec = tween(
                                        ConvoModalTokens.AnimMs,
                                        easing = FastOutSlowInEasing,
                                    ),
                                ),
                                project = project,
                                animateEnter = project.id == enteringProjectId,
                                onDelete = { onDeleteProject(project.id) },
                            )
                        }
                    }
                }
            }
        }

        if (showCreateModal) {
            CreateProjectSheet(
                existingNames = existingNames,
                onDismiss = { showCreateModal = false },
                onCreate = { name, description ->
                    onCreateProject(name, description)
                },
            )
        }
    }
}
