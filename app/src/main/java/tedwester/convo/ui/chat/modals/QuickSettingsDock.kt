package tedwester.convo.ui.chat.modals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tedwester.convo.features.chat.model.QuickSetting
import tedwester.convo.features.chat.model.QuickSettingState

private val DockHeight = 52.dp
private val DockSlotSize = 44.dp
private val DockSlotIconSize = 21.dp
private val DockCornerRadius = 26.dp
private val DockPadding = 4.dp
private const val StaggerMs = 45L
private const val EnterMs = 220
private const val ToggleColorMs = 240

@Composable
fun QuickSettingsDock(
    quickSettings: List<QuickSetting>,
    modifier: Modifier = Modifier,
) {
    if (quickSettings.isEmpty()) return

    val dark = isSystemInDarkTheme()
    val dockFill = if (dark) {
        Color(0xFF1E1D1B).copy(alpha = 0.96f)
    } else {
        Color.White.copy(alpha = 0.96f)
    }
    val dockRing = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)

    Box(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(DockHeight)
                .clip(RoundedCornerShape(DockCornerRadius))
                .background(dockFill)
                .border(1.dp, dockRing, RoundedCornerShape(DockCornerRadius))
                .padding(DockPadding),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            quickSettings.forEachIndexed { index, setting ->
                DockSlot(setting = setting, staggerIndex = index)
            }
        }
    }
}

@Composable
private fun DockSlot(setting: QuickSetting, staggerIndex: Int) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffectStagger(staggerIndex) { entered = true }

    AnimatedVisibility(
        visible = entered,
        enter = scaleIn(spring(dampingRatio = 0.62f, stiffness = 380f)) +
            fadeIn(tween(EnterMs, easing = FastOutSlowInEasing)),
        exit = scaleOut(tween(120)) + fadeOut(tween(120)),
        modifier = Modifier.fillMaxHeight(),
    ) {
        val on = (setting.state as? QuickSettingState.Toggle)?.on == true
        val enabled = when (val s = setting.state) {
            is QuickSettingState.Picker -> s.enabled
            is QuickSettingState.Cycle -> s.enabled
            is QuickSettingState.Action -> s.enabled
            else -> true
        }

        QuickSettingsDockButton(
            icon = setting.icon(),
            contentDescription = setting.label,
            on = on,
            enabled = enabled,
            onClick = setting.onTap,
        )
    }
}

@Composable
private fun QuickSettingsDockButton(
    icon: Painter,
    contentDescription: String,
    on: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "dockSlotScale",
    )

    val defaultFill = if (dark) {
        Color.White.copy(alpha = 0.025f)
    } else {
        Color.Black.copy(alpha = 0.03f)
    }
    val activeFill = if (dark) Color.White else MaterialTheme.colorScheme.primary
    val activeIcon = if (dark) Color.Black else MaterialTheme.colorScheme.onPrimary
    val idleIcon = if (dark) Color(0xFF6E6E6E) else Color(0xFF9AA3B1)
    val disabledIcon = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f)

    val targetFill = when {
        !enabled -> Color.Transparent
        on -> activeFill
        else -> defaultFill
    }
    val targetIcon = when {
        !enabled -> disabledIcon
        on -> activeIcon
        else -> idleIcon
    }

    val fill by animateColorAsState(
        targetValue = targetFill,
        animationSpec = tween(ToggleColorMs),
        label = "dockSlotFill",
    )
    val iconTint by animateColorAsState(
        targetValue = targetIcon,
        animationSpec = tween(ToggleColorMs),
        label = "dockSlotIcon",
    )

    val shape = RoundedCornerShape(DockCornerRadius - DockPadding)
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(DockSlotSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(fill)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(DockSlotIconSize),
        )
    }
}

@Composable
private fun LaunchedEffectStagger(index: Int, block: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        delay(index * StaggerMs)
        block()
    }
}
