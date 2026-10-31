package tedwester.convo.ui.chat.modals

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tedwester.convo.core.network.OpenRouterApi
import tedwester.convo.core.network.model.ModelKind
import tedwester.convo.core.network.model.OpenRouterModel
import tedwester.convo.ui.components.ConvoBottomSheet
import tedwester.convo.ui.components.ConvoSearchField
import tedwester.convo.ui.components.ConvoSheetHeader
import tedwester.convo.ui.components.LazyColumnWithEdgeFades
import tedwester.convo.ui.components.rememberConvoSheetController
import tedwester.convo.ui.icons.ConvoIcons
import tedwester.convo.ui.theme.ConvoModalTokens
import tedwester.convo.ui.theme.convoModalSurface
import java.util.Locale

private val ModelSelectorHorizontalPadding = 20.dp
private const val ModelFetchRetryMs = 2_000L
private const val FilterAnimMs = 220

data class SelectorTopOption(
    val label: String,
    val sublabel: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
fun ModelSelectorModal(
    apiKey: String,
    api: OpenRouterApi,
    currentModelId: String?,
    onSelect: (OpenRouterModel) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Select model",
    fixedFilters: ModelFilterState? = null,
    initialFilters: ModelFilterState = ModelFilterState(),
    onFiltersChange: (ModelFilterState) -> Unit = {},
    initialUiState: ModelSelectorUiState = ModelSelectorUiState(),
    onUiStateChange: (ModelSelectorUiState) -> Unit = {},
    topOptions: List<SelectorTopOption> = emptyList(),
    showFilterBadges: Boolean = fixedFilters == null,
    modelFilter: (OpenRouterModel) -> Boolean = { true },
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val sheet = rememberConvoSheetController()
    var highlightedId by remember { mutableStateOf(currentModelId) }

    LaunchedEffect(currentModelId) {
        if (!sheet.closing) highlightedId = currentModelId
    }

    fun selectModel(model: OpenRouterModel) {
        scope.launch {
            if (!sheet.animateClose()) return@launch
            highlightedId = model.id
            onSelect(model)
            performModelSelectHaptic(view)
            delay(ConvoModalTokens.AnimMs.toLong())
            onDismiss()
        }
    }

    ConvoBottomSheet(
        controller = sheet,
        onDismissRequest = onDismiss,
        useDialog = true,
        sheetHeightFraction = 0.86f,
        contentHorizontalPadding = 0.dp,
        contentVerticalPadding = 10.dp,
        consumeSheetClicks = false,
        modifier = modifier,
    ) {
        ModelSelectorContent(
            apiKey = apiKey,
            api = api,
            currentModelId = highlightedId,
            onSelect = ::selectModel,
            onDismiss = { sheet.dismiss(scope, onDismiss) },
            title = title,
            fixedFilters = fixedFilters,
            initialFilters = initialFilters,
            onFiltersChange = onFiltersChange,
            initialUiState = initialUiState,
            onUiStateChange = onUiStateChange,
            topOptions = topOptions,
            showFilterBadges = showFilterBadges,
            modelFilter = modelFilter,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

private suspend fun performModelSelectHaptic(view: View) {
    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    delay(42)
    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
}

@Composable
private fun ModelSelectorContent(
    apiKey: String,
    api: OpenRouterApi,
    currentModelId: String?,
    onSelect: (OpenRouterModel) -> Unit,
    onDismiss: () -> Unit,
    title: String,
    fixedFilters: ModelFilterState?,
    initialFilters: ModelFilterState,
    onFiltersChange: (ModelFilterState) -> Unit,
    initialUiState: ModelSelectorUiState,
    onUiStateChange: (ModelSelectorUiState) -> Unit,
    topOptions: List<SelectorTopOption>,
    showFilterBadges: Boolean,
    modelFilter: (OpenRouterModel) -> Boolean,
    modifier: Modifier,
) {
    var models by remember { mutableStateOf<List<OpenRouterModel>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf(initialUiState.query) }
    var filters by remember { mutableStateOf(fixedFilters ?: initialFilters) }
    var filtersExpanded by remember { mutableStateOf(initialUiState.filtersExpanded) }
    val sheetBackground = convoModalSurface()
    val activeFilters = fixedFilters ?: filters

    fun updateFilters(next: ModelFilterState) {
        if (fixedFilters != null) return
        filters = next
        onFiltersChange(next)
    }

    fun updateQuery(next: String) {
        query = next
        onUiStateChange(ModelSelectorUiState(filtersExpanded = filtersExpanded, query = next))
    }

    fun updateExpanded(next: Boolean) {
        filtersExpanded = next
        onUiStateChange(ModelSelectorUiState(filtersExpanded = next, query = query))
    }

    LaunchedEffect(activeFilters, apiKey) {
        loading = true
        models = null
        while (true) {
            val fetched = runCatching {
                api.fetchModels(apiKey = apiKey, query = activeFilters.toQuery())
            }
            if (fetched.isSuccess) {
                models = fetched.getOrThrow()
                loading = false
                break
            }
            delay(ModelFetchRetryMs)
        }
    }

    Column(modifier = modifier.fillMaxWidth().fillMaxHeight()) {
        ConvoSheetHeader(
            title = title,
            onClose = onDismiss,
            modifier = Modifier.padding(horizontal = ModelSelectorHorizontalPadding),
        )

        Spacer(modifier = Modifier.height(12.dp))

        ConvoSearchField(
            value = query,
            onValueChange = ::updateQuery,
            placeholder = "Search models…",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ModelSelectorHorizontalPadding),
        )

        if (topOptions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            topOptions.forEach { option ->
                TopOptionRow(option = option)
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                modifier = Modifier.padding(
                    horizontal = ModelSelectorHorizontalPadding,
                    vertical = 10.dp,
                ),
            )
        }

        if (showFilterBadges) {
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ModelSelectorHorizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Filters",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { updateExpanded(!filtersExpanded) },
                        ),
                ) {
                    Icon(
                        painter = if (filtersExpanded) {
                            ConvoIcons.ChevronUp()
                        } else {
                            ConvoIcons.ChevronDown()
                        },
                        contentDescription = if (filtersExpanded) {
                            "Hide filters"
                        } else {
                            "Show filters"
                        },
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = filtersExpanded,
                enter = fadeIn(tween(FilterAnimMs, easing = FastOutSlowInEasing)) +
                    expandVertically(tween(FilterAnimMs, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(FilterAnimMs - 60, easing = FastOutSlowInEasing)) +
                    shrinkVertically(tween(FilterAnimMs - 60, easing = FastOutSlowInEasing)),
            ) {
                Column {
                    Spacer(modifier = Modifier.height(6.dp))

                    ModelFilterBadgeRows(
                        filters = filters,
                        onFiltersChange = ::updateFilters,
                        contentHorizontalPadding = ModelSelectorHorizontalPadding,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        } else {
            Spacer(modifier = Modifier.height(10.dp))
        }

        when {
            loading -> {
                LazyColumnWithEdgeFades(
                    background = sheetBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        start = ModelSelectorHorizontalPadding,
                        end = ModelSelectorHorizontalPadding,
                        bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(10) {
                        ModelRowSkeleton()
                    }
                }
            }

            else -> {
                val displayModels = models.orEmpty()

                val filtered = remember(displayModels, query, activeFilters, modelFilter) {
                    val matched = displayModels
                        .asSequence()
                        .filter { it.matchesSearch(query) }
                        .filter { activeFilters.matchesCapabilities(it) }
                        .filter { activeFilters.matchesPriceTier(it) }
                        .filter { !activeFilters.reasoningOnly || it.supportsReasoning }
                        .filter(modelFilter)
                        .toList()
                    activeFilters.applySorts(matched)
                }

                if (filtered.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = if (displayModels.isEmpty()) {
                                "No models available."
                            } else {
                                "No models match these filters."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                        if (showFilterBadges && (activeFilters.hasActiveFilters || query.isNotEmpty())) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                FilterBadge(
                                    label = "Clear filters & search",
                                    selected = true,
                                    isClear = true,
                                    onClick = {
                                        updateQuery("")
                                        updateFilters(ModelFilterState())
                                    },
                                )
                            }
                        } else if (!showFilterBadges && query.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                FilterBadge(
                                    label = "Clear search",
                                    selected = true,
                                    isClear = true,
                                    onClick = { updateQuery("") },
                                )
                            }
                        }
                    }
                } else {
                    LazyColumnWithEdgeFades(
                        background = sheetBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(
                            start = ModelSelectorHorizontalPadding,
                            end = ModelSelectorHorizontalPadding,
                            bottom = 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(filtered, key = { it.id }) { model ->
                            ModelRow(
                                model = model,
                                selected = model.id == currentModelId,
                                onClick = { onSelect(model) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopOptionRow(option: SelectorTopOption) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ModelSelectorHorizontalPadding)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (option.selected) {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
                } else {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.02f)
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = option.onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = option.sublabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        if (option.selected) {
            Icon(
                painter = ConvoIcons.Check(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
