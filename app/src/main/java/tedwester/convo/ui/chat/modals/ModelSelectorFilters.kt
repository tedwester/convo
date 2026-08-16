package tedwester.convo.ui.chat.modals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
@Composable
internal fun ModelFilterBadgeRows(
    filters: ModelFilterState,
    onFiltersChange: (ModelFilterState) -> Unit,
    contentHorizontalPadding: Dp,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterSection(title = "Produces", contentHorizontalPadding = contentHorizontalPadding) {
            ModelOutputFilter.entries.forEach { output ->
                FilterBadge(
                    label = output.label,
                    selected = output in filters.outputFilters,
                    onClick = { onFiltersChange(filters.toggleOutput(output)) },
                )
            }
        }

        FilterSection(title = "Accepts", contentHorizontalPadding = contentHorizontalPadding) {
            ModelInputFilter.entries.forEach { input ->
                FilterBadge(
                    label = input.label,
                    selected = input in filters.inputFilters,
                    onClick = { onFiltersChange(filters.toggleInput(input)) },
                )
            }
        }

        FilterSection(title = "Sort", contentHorizontalPadding = contentHorizontalPadding) {
            ModelSortBadge.entries.forEach { sort ->
                FilterBadge(
                    label = sort.label,
                    selected = sort in filters.sorts,
                    onClick = { onFiltersChange(filters.toggleSort(sort)) },
                )
            }
        }

        FilterSection(title = "Use case", contentHorizontalPadding = contentHorizontalPadding) {
            ModelCategoryBadge.entries.forEach { category ->
                FilterBadge(
                    label = category.label,
                    selected = filters.category == category,
                    onClick = {
                        onFiltersChange(
                            filters.copy(
                                category = if (filters.category == category) null else category,
                            ),
                        )
                    },
                )
            }
        }

        FilterSection(title = "Provider", contentHorizontalPadding = contentHorizontalPadding) {
            ModelAuthorBadge.entries.forEach { author ->
                FilterBadge(
                    label = author.label,
                    selected = author in filters.authors,
                    onClick = { onFiltersChange(filters.toggleAuthor(author)) },
                )
            }
        }

        FilterSection(title = "Options", contentHorizontalPadding = contentHorizontalPadding) {
            if (filters.hasActiveFilters) {
                FilterBadge(
                    label = "Clear",
                    selected = true,
                    onClick = { onFiltersChange(ModelFilterState()) },
                    isClear = true,
                )
            }
            FilterBadge(
                label = "Free",
                selected = filters.freeOnly,
                onClick = { onFiltersChange(filters.copy(freeOnly = !filters.freeOnly)) },
            )
            FilterBadge(
                label = "Reasoning",
                selected = filters.reasoningOnly,
                onClick = {
                    onFiltersChange(filters.copy(reasoningOnly = !filters.reasoningOnly))
                },
            )
        }
    }
}

@Composable
internal fun FilterSection(
    title: String,
    contentHorizontalPadding: Dp,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.padding(horizontal = contentHorizontalPadding),
        )
        FilterChipRow(contentHorizontalPadding = contentHorizontalPadding, content = content)
    }
}

@Composable
internal fun FilterChipRow(
    contentHorizontalPadding: Dp,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(contentHorizontalPadding))
        content()
        Spacer(modifier = Modifier.width(contentHorizontalPadding))
    }
}

@Composable
internal fun FilterBadge(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    isClear: Boolean = false,
) {
    val shape = RoundedCornerShape(999.dp)
    val border = when {
        isClear -> MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
        selected -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.28f)
        else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
    }
    val bg = when {
        isClear -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        selected -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val fg = when {
        isClear -> MaterialTheme.colorScheme.error
        selected -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.95f)
        else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
        fontWeight = if (selected || isClear) FontWeight.Medium else FontWeight.Normal,
        color = fg,
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}
