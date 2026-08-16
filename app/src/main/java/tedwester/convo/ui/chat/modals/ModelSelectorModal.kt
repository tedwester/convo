package tedwester.convo.ui.chat.modals

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import tedwester.convo.ui.theme.ConvoModalTokens
import tedwester.convo.ui.theme.convoModalSurface
import java.util.Locale

private val ModelSelectorHorizontalPadding = 20.dp
private const val ModelFetchRetryMs = 2_000L

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
    showFilterBadges: Boolean,
    modelFilter: (OpenRouterModel) -> Boolean,
    modifier: Modifier,
) {
    var models by remember { mutableStateOf<List<OpenRouterModel>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var filters by remember { mutableStateOf(fixedFilters ?: initialFilters) }
    val sheetBackground = convoModalSurface()
    val activeFilters = fixedFilters ?: filters

    fun updateFilters(next: ModelFilterState) {
        if (fixedFilters != null) return
        filters = next
        onFiltersChange(next)
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
            onValueChange = { query = it },
            placeholder = "Search models…",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ModelSelectorHorizontalPadding),
        )

        if (showFilterBadges) {
            Spacer(modifier = Modifier.height(10.dp))

            ModelFilterBadgeRows(
                filters = filters,
                onFiltersChange = ::updateFilters,
                contentHorizontalPadding = ModelSelectorHorizontalPadding,
            )

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
                            TextButton(
                                onClick = {
                                    query = ""
                                    updateFilters(ModelFilterState())
                                },
                            ) {
                                Text("Clear filters & search")
                            }
                        } else if (!showFilterBadges && query.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { query = "" }) {
                                Text("Clear search")
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
