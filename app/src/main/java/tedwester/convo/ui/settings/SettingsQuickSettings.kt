package tedwester.convo.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import android.view.HapticFeedbackConstants
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tedwester.convo.features.chat.data.QuickSettingsConfig
import tedwester.convo.features.chat.model.QuickSettingDescriptor
import tedwester.convo.ui.components.ConvoBottomSheet
import tedwester.convo.ui.components.rememberConvoSheetController
import tedwester.convo.ui.icons.ConvoIcons
import tedwester.convo.ui.theme.ConvoFieldTokens
import tedwester.convo.ui.theme.ConvoModalTokens
import tedwester.convo.ui.theme.convoFieldFill

private val RowIconSize = 20.dp
private val SmallButtonSize = 34.dp
private val SmallButtonIconSize = 15.dp
private val RowGap = 8.dp
private val ReorderRowHeight = 48.dp
private val RowStride = ReorderRowHeight + RowGap

@Composable
internal fun QuickSettingsSection(
    config: QuickSettingsConfig,
    descriptorsById: Map<String, QuickSettingDescriptor>,
    onChange: (QuickSettingsConfig) -> Unit,
    onOpenAddPicker: () -> Unit,
    onDragActiveChanged: (Boolean) -> Unit = {},
) {
    SettingsSection(
        title = "Quick settings",
        description = "Pin icon shortcuts to the dock on your chat list. Drag the handle to " +
            "reorder, up to ${QuickSettingsConfig.MAX_ITEMS} slots.",
        showDividerAbove = true,
    ) {
        if (config.items.isEmpty()) {
            Text(
                text = "Nothing pinned yet. Add a setting to put it in the dock.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            ReorderableQuickSettingsList(
                items = config.items,
                descriptorsById = descriptorsById,
                onReorder = { onChange(QuickSettingsConfig(it)) },
                onRemove = { id ->
                    onChange(QuickSettingsConfig(config.items.filter { it != id }))
                },
                onDragActiveChanged = onDragActiveChanged,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        AddQuickSettingButton(
            enabled = config.items.size < QuickSettingsConfig.MAX_ITEMS,
            onClick = onOpenAddPicker,
        )
    }
}

@Composable
private fun ReorderableQuickSettingsList(
    items: List<String>,
    descriptorsById: Map<String, QuickSettingDescriptor>,
    onReorder: (List<String>) -> Unit,
    onRemove: (String) -> Unit,
    onDragActiveChanged: (Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val rowStridePx = with(density) { RowStride.toPx() }

    var order by remember { mutableStateOf(items) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(items) {
        if (draggingId == null) order = items
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(RowGap),
        modifier = Modifier.fillMaxWidth(),
    ) {
        order.forEach { id ->
            key(id) {
                val descriptor = descriptorsById[id]
                if (descriptor != null) {
                    val isDragging = id == draggingId

                    QuickSettingRow(
                        descriptor = descriptor,
                        dragOffsetY = if (isDragging) dragOffsetY else 0f,
                        onRemove = { onRemove(id) },
                        onDragStart = {
                            draggingId = id
                            dragOffsetY = 0f
                            onDragActiveChanged(true)
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        },
                        onDrag = { deltaY ->
                            val activeId = draggingId
                            if (activeId == id) {
                                dragOffsetY += deltaY
                                val result = reorderByDragOffset(
                                    order = order,
                                    activeId = activeId,
                                    dragOffsetY = dragOffsetY,
                                    rowStridePx = rowStridePx,
                                )
                                order = result.order
                                dragOffsetY = result.dragOffsetY
                            }
                        },
                        onDragEnd = {
                            if (draggingId == id) {
                                draggingId = null
                                dragOffsetY = 0f
                                onReorder(order)
                                onDragActiveChanged(false)
                            }
                        },
                        modifier = Modifier.zIndex(if (isDragging) 1f else 0f),
                    )
                }
            }
        }
    }
}

private data class DragReorderResult(
    val order: List<String>,
    val dragOffsetY: Float,
)

private fun reorderByDragOffset(
    order: List<String>,
    activeId: String,
    dragOffsetY: Float,
    rowStridePx: Float,
): DragReorderResult {
    var currentOrder = order
    var currentOffset = dragOffsetY

    while (true) {
        val from = currentOrder.indexOf(activeId)
        if (from < 0) break

        when {
            currentOffset > rowStridePx / 2f && from < currentOrder.lastIndex -> {
                currentOrder = currentOrder.toMutableList().apply {
                    add(from + 1, removeAt(from))
                }
                currentOffset -= rowStridePx
            }
            currentOffset < -rowStridePx / 2f && from > 0 -> {
                currentOrder = currentOrder.toMutableList().apply {
                    add(from - 1, removeAt(from))
                }
                currentOffset += rowStridePx
            }
            else -> break
        }
    }

    return DragReorderResult(currentOrder, currentOffset)
}

@Composable
private fun QuickSettingRow(
    descriptor: QuickSettingDescriptor,
    dragOffsetY: Float,
    onRemove: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fillColor = convoFieldFill()
    val shape = RoundedCornerShape(ConvoFieldTokens.CornerRadius)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ReorderRowHeight)
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
            .clip(shape)
            .background(fillColor)
            .pointerInput(descriptor.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.y)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                )
            }
            .padding(horizontal = ConvoFieldTokens.HorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = ConvoIcons.Menu(),
            contentDescription = "Drag to reorder",
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Icon(
            painter = descriptor.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = descriptor.label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(SmallButtonSize)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onRemove,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = ConvoIcons.X(),
                contentDescription = "Remove",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                modifier = Modifier.size(SmallButtonIconSize),
            )
        }
    }
}

@Composable
private fun AddQuickSettingButton(enabled: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val bg = if (enabled) {
        MaterialTheme.colorScheme.onBackground
    } else {
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
    }
    val content = if (enabled) {
        MaterialTheme.colorScheme.background
    } else {
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onClick()
                },
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = ConvoIcons.Add(),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (enabled) {
                "Add quick setting"
            } else {
                "Dock full (${QuickSettingsConfig.MAX_ITEMS} max)"
            },
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = content,
        )
    }
}

@Composable
internal fun QuickSettingsAddPickerSheet(
    currentConfig: QuickSettingsConfig,
    availableSettings: List<QuickSettingDescriptor>,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheet = rememberConvoSheetController()
    val remaining = availableSettings.filter { it.id !in currentConfig.items }

    fun addAndDismiss(id: String) {
        if (sheet.closing) return
        scope.launch {
            if (!sheet.animateClose()) return@launch
            onAdd(id)
            delay(ConvoModalTokens.AnimMs.toLong())
            onDismiss()
        }
    }

    ConvoBottomSheet(
        controller = sheet,
        onDismissRequest = onDismiss,
        useDialog = true,
        contentScrollable = true,
        contentHorizontalPadding = 20.dp,
        contentVerticalPadding = 10.dp,
        consumeSheetClicks = false,
        title = "Add to dock",
        titleBottomSpacing = 8.dp,
    ) {
        Text(
            text = "Choose a shortcut for the dock on your chat list.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (remaining.isEmpty()) {
            Text(
                text = "Everything available is already in the dock.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                remaining.forEach { descriptor ->
                    QuickSettingPickerRow(
                        descriptor = descriptor,
                        onClick = { addAndDismiss(descriptor.id) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun QuickSettingPickerRow(
    descriptor: QuickSettingDescriptor,
    onClick: () -> Unit,
) {
    val tileColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ConvoModalTokens.CornerRadius))
            .background(tileColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = descriptor.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(RowIconSize),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = descriptor.label,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = descriptor.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            painter = ConvoIcons.Add(),
            contentDescription = "Add ${descriptor.label}",
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            modifier = Modifier.size(18.dp),
        )
    }
}
