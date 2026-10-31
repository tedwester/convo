package tedwester.convo.ui.chat.selection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tedwester.convo.ui.chat.ChatMenuDestructive
import tedwester.convo.ui.chat.ChatSectionAnimMs
import tedwester.convo.ui.chat.message.MessageActionButton
import tedwester.convo.ui.components.DockSurface
import tedwester.convo.ui.icons.ConvoIcons

private val CheckboxSlotWidth = 34.dp
private val CheckboxSize = 20.dp

/**
 * Tint used behind a checked row, so selection reads on the row itself and not only on the
 * checkbox. Uses the same neutral highlight the rest of the sidebar uses, a touch stronger.
 */
@Composable
internal fun selectionRowTint(): Color {
    return if (isSystemInDarkTheme()) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }
}

/**
 * The slot a checkbox slides into on the left of a chat row.
 *
 * Its width animates, which is what pushes the row's titles to the right: the layout moves as
 * a whole rather than the checkbox being drawn over the title, so nothing is ever covered.
 */
@Composable
internal fun SelectionCheckboxSlot(
    visible: Boolean,
    checked: Boolean,
    modifier: Modifier = Modifier,
) {
    val width by animateDpAsState(
        targetValue = if (visible) CheckboxSlotWidth else 0.dp,
        animationSpec = tween(ChatSectionAnimMs, easing = FastOutSlowInEasing),
        label = "chatCheckSlot",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(ChatSectionAnimMs / 2, easing = FastOutSlowInEasing),
        label = "chatCheckAlpha",
    )

    Box(
        modifier = modifier.width(width),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (alpha > 0f) {
            SelectionCheckbox(
                checked = checked,
                modifier = Modifier.alpha(alpha),
            )
        }
    }
}

@Composable
internal fun SelectionCheckbox(
    checked: Boolean,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val checkedFill = if (dark) Color.White else MaterialTheme.colorScheme.onBackground
    val checkTint = if (dark) Color(0xFF171615) else Color.White

    val fill by animateColorAsState(
        targetValue = if (checked) checkedFill else Color.Transparent,
        animationSpec = tween(160, easing = FastOutSlowInEasing),
        label = "checkFill",
    )
    val ring by animateColorAsState(
        targetValue = if (checked) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
        },
        animationSpec = tween(160, easing = FastOutSlowInEasing),
        label = "checkRing",
    )
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = modifier
            .size(CheckboxSize)
            .clip(shape)
            .background(fill)
            .border(1.5.dp, ring, shape),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = checked,
            enter = scaleIn(spring(dampingRatio = 0.6f, stiffness = 420f)) +
                fadeIn(tween(120)),
            exit = scaleOut(tween(100)) + fadeOut(tween(100)),
        ) {
            Icon(
                painter = ConvoIcons.Check(),
                contentDescription = null,
                tint = checkTint,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

/**
 * Bar shown while chats are selected. It takes over the quick settings dock's spot so the two
 * never compete for the same corner, and rides the same [DockSurface] so the swap reads as one
 * object changing rather than two docks fighting. Count on the left, actions on the right.
 */
@Composable
internal fun ChatSelectionBar(
    selectedCount: Int,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    onPin: () -> Unit,
    onAddToProject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasSelection = selectedCount > 0
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
    ) {
        DockSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (selectedCount == 1) {
                        "1 selected"
                    } else {
                        "$selectedCount selected"
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                    textAlign = TextAlign.Start,
                )

                MessageActionButton(
                    painter = ConvoIcons.Trash2(),
                    contentDescription = "Delete",
                    enabled = hasSelection,
                    tint = ChatMenuDestructive,
                    onClick = onDelete,
                )
                MessageActionButton(
                    painter = ConvoIcons.Archive(),
                    contentDescription = "Archive",
                    enabled = hasSelection,
                    onClick = onArchive,
                )
                MessageActionButton(
                    painter = ConvoIcons.Pin(),
                    contentDescription = "Pin",
                    enabled = hasSelection,
                    onClick = onPin,
                )
                MessageActionButton(
                    painter = ConvoIcons.FolderPlus(),
                    contentDescription = "Add to project",
                    enabled = hasSelection,
                    onClick = onAddToProject,
                )
                MessageActionButton(
                    painter = ConvoIcons.X(),
                    contentDescription = "Cancel",
                    onClick = onCancel,
                )
            }
        }
    }
}

/**
 * Brief confirmation shown in the dock corner after a bulk delete, in place of the quick
 * settings bar. Same surface as the other docks, so it reads as the corner's current state
 * rather than a popup.
 */
@Composable
internal fun ChatDeletedFlash(
    deletedCount: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        DockSurface(modifier = Modifier.height(52.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = ConvoIcons.Check(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = if (deletedCount == 1) {
                        "1 chat deleted"
                    } else {
                        "$deletedCount chats deleted"
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}
