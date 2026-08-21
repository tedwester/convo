package tedwester.convo.ui.chat.message

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tedwester.convo.features.chat.model.WebSearchStep
import tedwester.convo.ui.components.ScrollColumnWithEdgeFades
import tedwester.convo.ui.icons.ConvoIcons
import tedwester.convo.ui.theme.AssistantSerifFamily
import kotlin.math.abs

private val ThinkingPaneMaxHeight = 280.dp

private val ThinkingPaneFadeHeight = 24.dp

@Composable
internal fun ThinkingSection(
    sectionKey: Any,
    reasoning: String,
    headerText: String,
    shimmerHeader: Boolean,
    modifier: Modifier = Modifier,
    expandedByDefault: Boolean = false,
    collapseWhenResponseStarts: Boolean = false,
    showSearchTimeline: Boolean = true,
    webSearchSteps: List<WebSearchStep> = emptyList(),
    onInteraction: () -> Unit = {},
) {
    var expanded by rememberSaveable(sectionKey) {
        mutableStateOf(expandedByDefault && !collapseWhenResponseStarts)
    }
    var prevCollapse by rememberSaveable(sectionKey) { mutableStateOf(collapseWhenResponseStarts) }

    LaunchedEffect(collapseWhenResponseStarts) {
        if (collapseWhenResponseStarts && !prevCollapse) {
            expanded = false
        }
        prevCollapse = collapseWhenResponseStarts
    }

    val canExpand = reasoning.isNotBlank() || webSearchSteps.isNotEmpty()
    val hasExpandedContent = reasoning.isNotBlank() || webSearchSteps.isNotEmpty()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (canExpand) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                onInteraction()
                                expanded = !expanded
                            },
                        )
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (shimmerHeader) {
                ShimmerStatusLabel(
                    text = headerText,
                    modifier = Modifier.weight(1f, fill = false),
                )
            } else {
                StaticStatusLabel(
                    text = headerText,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            if (canExpand) {
                Icon(
                    painter = ConvoIcons.ChevronDown(),
                    contentDescription = if (expanded) "Hide thinking" else "Show thinking",
                    tint = StatusLabelGrey,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { rotationZ = if (expanded) 180f else 0f },
                )
            }
        }

        AnimatedVisibility(
            visible = expanded && hasExpandedContent,
            enter = fadeIn(tween(160, easing = FastOutSlowInEasing)) +
                expandVertically(tween(200, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(120, easing = FastOutSlowInEasing)) +
                shrinkVertically(tween(160, easing = FastOutSlowInEasing)),
        ) {
            ThinkingPane(
                reasoning = reasoning,
                webSearchSteps = webSearchSteps,
                showSearchTimeline = showSearchTimeline,
                streaming = shimmerHeader,
            )
        }
    }
}

@Composable
private fun ThinkingPane(
    reasoning: String,
    webSearchSteps: List<WebSearchStep>,
    showSearchTimeline: Boolean,
    streaming: Boolean,
) {
    val scrollState = rememberScrollState()
    val background = MaterialTheme.colorScheme.background

    var userDetached by remember { mutableStateOf(false) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            private fun onUserScroll(deltaY: Float) {
                if (abs(deltaY) < 0.5f) return
                userDetached = true
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) onUserScroll(available.y)
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput) onUserScroll(consumed.y)
                if (available.y == 0f) return Offset.Zero

                val atTop = scrollState.value <= 0
                val atBottom = scrollState.value >= scrollState.maxValue
                return when {
                    scrollState.maxValue <= 0 -> Offset(0f, available.y)
                    available.y > 0f && atTop -> Offset(0f, available.y)
                    available.y < 0f && atBottom -> Offset(0f, available.y)
                    else -> Offset.Zero
                }
            }
        }
    }

    val contentMarker = reasoning.length to webSearchSteps.size
    LaunchedEffect(contentMarker, streaming, userDetached) {
        if (!streaming || userDetached) return@LaunchedEffect
        if (scrollState.maxValue <= 0) return@LaunchedEffect
        scrollState.scrollTo(scrollState.maxValue)
    }

    ScrollColumnWithEdgeFades(
        background = background,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = ThinkingPaneMaxHeight),
        fadeHeight = ThinkingPaneFadeHeight,
        state = scrollState,
        nestedScrollConnection = nestedScrollConnection,
        contentPadding = PaddingValues(
            top = 2.dp,
            bottom = 4.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (reasoning.isNotBlank()) {
            Text(
                text = reasoning,
                style = TextStyle(
                    fontFamily = AssistantSerifFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    letterSpacing = 0.05.sp,
                    color = StatusLabelGrey,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (webSearchSteps.isNotEmpty() && showSearchTimeline) {
            WebSearchTimeline(steps = webSearchSteps)
        }
    }
}
